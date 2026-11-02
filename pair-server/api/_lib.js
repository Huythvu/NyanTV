// Shared helpers for the NyanTV pairing relay. Files prefixed with "_" are not routed by Vercel.
//
// ── Shared store: Firestore ───────────────────────────────────────────────────────────────────
// The relay needs a store that survives inactivity. Upstash's free tier archives an idle database,
// and the relay only touches storage when someone logs in — so a quiet stretch would silently break
// the next login. Firestore on the Spark (free) plan never archives, and it's already the backend
// for the companion AnimePahe watchlist, so this consolidates to one service.
//
// The relay authenticates as a Firebase service account (server-only — public clients can neither
// read nor write the pairing collection; service-account access uses IAM and bypasses security
// rules, so the default-deny rules stay as they are). Each pairing entry is stored as a single JSON
// blob plus an `expireAt` timestamp. A Firestore TTL policy on `expireAt` garbage-collects old
// entries; reads ALSO enforce expiry, so a stale entry is never returned even in the window before
// TTL sweeps it (TTL deletion can lag up to ~24h and must not be relied on for correctness).
//
// Zero dependencies: the service-account JWT is signed with Node's built-in crypto and exchanged for
// a short-lived access token, which is cached in module scope for the life of a warm instance.
import crypto from 'crypto';

const FS_COLLECTION = process.env.FIRESTORE_PAIR_COLLECTION || 'pairing';

function serviceAccount() {
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT;
  if (!raw) throw new Error('Firestore not configured (missing FIREBASE_SERVICE_ACCOUNT env var)');
  let sa;
  try {
    sa = JSON.parse(raw);
  } catch {
    throw new Error('FIREBASE_SERVICE_ACCOUNT is not valid JSON (paste the whole service-account key)');
  }
  if (!sa.client_email || !sa.private_key) {
    throw new Error('FIREBASE_SERVICE_ACCOUNT missing client_email/private_key');
  }
  return sa;
}

function projectId() {
  return serviceAccount().project_id || process.env.FIREBASE_PROJECT_ID || '';
}

const b64url = (obj) => Buffer.from(JSON.stringify(obj)).toString('base64url');

let _tokenCache = { token: '', exp: 0 };

async function getAccessToken() {
  const now = Math.floor(Date.now() / 1000);
  if (_tokenCache.token && _tokenCache.exp - 60 > now) return _tokenCache.token;

  const sa = serviceAccount();
  const signingInput =
    `${b64url({ alg: 'RS256', typ: 'JWT' })}.` +
    b64url({
      iss: sa.client_email,
      scope: 'https://www.googleapis.com/auth/datastore',
      aud: 'https://oauth2.googleapis.com/token',
      iat: now,
      exp: now + 3600,
    });
  const signature = crypto.createSign('RSA-SHA256').update(signingInput).sign(sa.private_key, 'base64url');
  const assertion = `${signingInput}.${signature}`;

  const resp = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion,
    }).toString(),
  });
  if (!resp.ok) throw new Error(`Firestore auth failed (${resp.status}): ${(await resp.text()).slice(0, 160)}`);
  const data = await resp.json();
  _tokenCache = { token: data.access_token, exp: now + (data.expires_in || 3600) };
  return _tokenCache.token;
}

// Firestore REST document URL. The key (e.g. "pair:ABCD2345") becomes the document id in
// FS_COLLECTION; encodeURIComponent keeps the ':' legal in the URL path.
function docUrl(key) {
  const pid = projectId();
  if (!pid) throw new Error('Firestore project id missing (set project_id in the service account or FIREBASE_PROJECT_ID)');
  return `https://firestore.googleapis.com/v1/projects/${pid}/databases/(default)/documents/${FS_COLLECTION}/${encodeURIComponent(key)}`;
}

async function authHeader() {
  return { Authorization: `Bearer ${await getAccessToken()}` };
}

export const kv = {
  async get(key) {
    const resp = await fetch(docUrl(key), { headers: await authHeader() });
    if (resp.status === 404) return null;
    if (!resp.ok) throw new Error(`Firestore get failed: ${resp.status} ${(await resp.text()).slice(0, 120)}`);
    const fields = (await resp.json()).fields || {};
    const expireAt = fields.expireAt?.timestampValue;
    if (expireAt && new Date(expireAt).getTime() <= Date.now()) {
      // Expired but not yet swept by the TTL policy — treat as gone, and best-effort clean it up.
      kv.del(key).catch(() => {});
      return null;
    }
    const data = fields.data?.stringValue;
    if (data == null) return null;
    try { return JSON.parse(data); } catch { return data; }
  },
  async set(key, value, opts = {}) {
    const fields = { data: { stringValue: JSON.stringify(value) } };
    if (opts.ex) {
      fields.expireAt = { timestampValue: new Date(Date.now() + opts.ex * 1000).toISOString() };
    }
    // PATCH on the document path creates-or-overwrites the doc with exactly these fields.
    const resp = await fetch(docUrl(key), {
      method: 'PATCH',
      headers: { ...(await authHeader()), 'Content-Type': 'application/json' },
      body: JSON.stringify({ fields }),
    });
    if (!resp.ok) throw new Error(`Firestore set failed: ${resp.status} ${(await resp.text()).slice(0, 120)}`);
    return 'OK';
  },
  async del(key) {
    const resp = await fetch(docUrl(key), { method: 'DELETE', headers: await authHeader() });
    if (!resp.ok && resp.status !== 404) throw new Error(`Firestore del failed: ${resp.status}`);
    return 1;
  },
};

// Pairing entries live for this long; the TV must finish within the window.
export const PAIR_TTL_SECONDS = 600; // 10 minutes

// Unambiguous alphabet (no 0/O/1/I/L) so codes are easy to read off a TV.
const CODE_ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';

export function generateCode(length = 8) {
  const bytes = new Uint8Array(length);
  crypto.getRandomValues(bytes);
  let out = '';
  for (const b of bytes) out += CODE_ALPHABET[b % CODE_ALPHABET.length];
  return out;
}

/** A PKCE code_verifier (base64url, 43–128 chars). Used by providers that require PKCE (MAL). */
export function generateVerifier(bytes = 64) {
  const arr = new Uint8Array(bytes);
  crypto.getRandomValues(arr);
  return Buffer.from(arr).toString('base64url');
}

export const pairKey = (code) => `pair:${String(code || '').toUpperCase()}`;

/**
 * Provider registry. AniList first; MAL can be added later with its PKCE variant.
 * Each provider exposes how to build the authorize URL and how to exchange the code.
 */
export const PROVIDERS = {
  anilist: {
    authorizeUrl: 'https://anilist.co/api/v2/oauth/authorize',
    tokenUrl: 'https://anilist.co/api/v2/oauth/token',
    clientId: () => process.env.ANILIST_CLIENT_ID,
    redirectUri: () => process.env.ANILIST_REDIRECT_URI,
    buildAuthorizeUrl(state, _opts = {}) {
      const u = new URL(this.authorizeUrl);
      u.searchParams.set('client_id', this.clientId());
      u.searchParams.set('redirect_uri', this.redirectUri());
      // AniList only supports the authorization-code grant (implicit/response_type=token is
      // rejected as unsupported_grant_type), so we get a code and exchange it server-side.
      u.searchParams.set('response_type', 'code');
      u.searchParams.set('state', state);
      return u.toString();
    },
    async exchangeCode(code) {
      // AniList's /token sits behind Cloudflare, which can serve a "Just a moment..." managed
      // challenge (403) to requests that don't look like a browser. Present a full set of browser
      // headers, and retry a few times since the challenge decision can vary between attempts.
      const headers = {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        'Accept-Language': 'en-US,en;q=0.9',
        Origin: 'https://anilist.co',
        Referer: 'https://anilist.co/',
        'User-Agent':
          'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 ' +
          '(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36',
      };
      const payload = JSON.stringify({
        grant_type: 'authorization_code',
        client_id: this.clientId(),
        client_secret: process.env.ANILIST_CLIENT_SECRET,
        redirect_uri: this.redirectUri(),
        code,
      });
      let lastErr = '';
      for (let attempt = 0; attempt < 3; attempt++) {
        const resp = await fetch(this.tokenUrl, { method: 'POST', headers, body: payload });
        if (resp.ok) {
          const data = await resp.json();
          return {
            accessToken: data.access_token,
            refreshToken: data.refresh_token ?? null,
            expiresIn: data.expires_in ?? null,
          };
        }
        lastErr = `(${resp.status}): ${(await resp.text()).slice(0, 160)}`;
        await new Promise((r) => setTimeout(r, 500));
      }
      throw new Error(`token exchange failed ${lastErr}`);
    },
  },

  mal: {
    authorizeUrl: 'https://myanimelist.net/v1/oauth2/authorize',
    tokenUrl: 'https://myanimelist.net/v1/oauth2/token',
    // MAL requires PKCE on the authorization-code grant. The relay mints the verifier per session
    // and hands it back to the TV alongside the code; the TV does the token exchange itself, so the
    // MAL client secret never has to live on the relay (it stays in the app's local.properties).
    usesPkce: true,
    clientId: () => process.env.MAL_CLIENT_ID,
    // The callback is provider-agnostic, so MAL reuses the same registered relay redirect URL as
    // AniList. The user just adds that URL as an extra redirect on their MAL app.
    redirectUri: () => process.env.MAL_REDIRECT_URI || process.env.ANILIST_REDIRECT_URI,
    buildAuthorizeUrl(state, opts = {}) {
      const u = new URL(this.authorizeUrl);
      u.searchParams.set('client_id', this.clientId());
      u.searchParams.set('redirect_uri', this.redirectUri());
      u.searchParams.set('response_type', 'code');
      u.searchParams.set('state', state);
      // MAL only supports the "plain" PKCE method (code_challenge == code_verifier).
      if (opts.codeChallenge) {
        u.searchParams.set('code_challenge', opts.codeChallenge);
        u.searchParams.set('code_challenge_method', 'plain');
      }
      return u.toString();
    },
    // Server-side exchange for the "web" (browser/extension) flow, so a client that can't safely
    // hold the secret (a Chrome extension) never has to. The TV flow still exchanges on-device and
    // doesn't call this. MAL has no Cloudflare gate, so a plain server request works.
    async exchangeCode(code, codeVerifier) {
      const body = new URLSearchParams({
        client_id: this.clientId(),
        client_secret: process.env.MAL_CLIENT_SECRET || '',
        grant_type: 'authorization_code',
        code,
        code_verifier: codeVerifier || '',
        redirect_uri: this.redirectUri(),
      });
      const resp = await fetch(this.tokenUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: body.toString(),
      });
      if (!resp.ok) throw new Error(`MAL token exchange failed (${resp.status}): ${(await resp.text()).slice(0, 160)}`);
      const data = await resp.json();
      return {
        accessToken: data.access_token,
        refreshToken: data.refresh_token ?? null,
        expiresIn: data.expires_in ?? null,
      };
    },
    async refreshToken(refreshToken) {
      const body = new URLSearchParams({
        client_id: this.clientId(),
        client_secret: process.env.MAL_CLIENT_SECRET || '',
        grant_type: 'refresh_token',
        refresh_token: refreshToken,
      });
      const resp = await fetch(this.tokenUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: body.toString(),
      });
      if (!resp.ok) throw new Error(`MAL refresh failed (${resp.status})`);
      const data = await resp.json();
      return {
        accessToken: data.access_token,
        refreshToken: data.refresh_token ?? refreshToken,
        expiresIn: data.expires_in ?? null,
      };
    },
  },
};

export function getProvider(name) {
  return PROVIDERS[String(name || 'anilist').toLowerCase()] || null;
}

/** Minimal styled HTML page for the phone/PC browser steps. */
export function page(title, message) {
  return `<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${title} · NyanTV</title>
<style>
  :root { color-scheme: dark; }
  body { margin:0; min-height:100vh; display:grid; place-items:center; background:#14101a; color:#ede0e4;
         font-family:-apple-system,Segoe UI,Roboto,sans-serif; text-align:center; padding:24px; }
  .card { max-width:420px; }
  h1 { font-size:1.5rem; margin:0 0 12px; }
  p { color:#cbb8be; line-height:1.5; }
</style></head>
<body><div class="card"><h1>${title}</h1><p>${message}</p></div></body></html>`;
}
