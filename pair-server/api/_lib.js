// Shared helpers for the NyanTV pairing relay. Files prefixed with "_" are not routed by Vercel.
import { kv } from '@vercel/kv';

export { kv };

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
    buildAuthorizeUrl(state) {
      const u = new URL(this.authorizeUrl);
      u.searchParams.set('client_id', this.clientId());
      u.searchParams.set('redirect_uri', this.redirectUri());
      u.searchParams.set('response_type', 'code');
      u.searchParams.set('state', state);
      return u.toString();
    },
    async exchangeCode(code) {
      const resp = await fetch(this.tokenUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify({
          grant_type: 'authorization_code',
          client_id: this.clientId(),
          client_secret: process.env.ANILIST_CLIENT_SECRET,
          redirect_uri: this.redirectUri(),
          code,
        }),
      });
      if (!resp.ok) {
        throw new Error(`token exchange failed (${resp.status}): ${(await resp.text()).slice(0, 200)}`);
      }
      const data = await resp.json();
      return {
        accessToken: data.access_token,
        refreshToken: data.refresh_token ?? null,
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
