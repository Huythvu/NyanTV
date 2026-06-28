// Shared helpers for the NyanTV pairing relay. Files prefixed with "_" are not routed by Vercel.
//
// Minimal Redis/KV client over the Upstash REST API — zero dependencies. Works with either the
// Vercel-injected names (KV_REST_API_*) or the Upstash-native ones (UPSTASH_REDIS_REST_*), so it
// doesn't matter which storage integration is connected.
const KV_URL = process.env.KV_REST_API_URL || process.env.UPSTASH_REDIS_REST_URL || '';
const KV_TOKEN = process.env.KV_REST_API_TOKEN || process.env.UPSTASH_REDIS_REST_TOKEN || '';

async function kvCommand(args) {
  if (!KV_URL || !KV_TOKEN) throw new Error('KV store not configured (missing REST url/token env vars)');
  const resp = await fetch(KV_URL, {
    method: 'POST',
    headers: { Authorization: `Bearer ${KV_TOKEN}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(args),
  });
  if (!resp.ok) throw new Error(`KV ${args[0]} failed: ${resp.status} ${(await resp.text()).slice(0, 120)}`);
  return (await resp.json()).result;
}

export const kv = {
  async get(key) {
    const r = await kvCommand(['GET', key]);
    if (r == null) return null;
    try { return JSON.parse(r); } catch { return r; }
  },
  async set(key, value, opts = {}) {
    const args = ['SET', key, JSON.stringify(value)];
    if (opts.ex) args.push('EX', String(opts.ex));
    return kvCommand(args);
  },
  async del(key) {
    return kvCommand(['DEL', key]);
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
    // No exchangeCode: MAL tokens are exchanged on the TV (see MalService.exchangePairedCode).
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
