// POST /api/pair/new  → mints a pairing code for the TV to display.
// Body/query: { provider?: "anilist" } (defaults to anilist)
import { kv, pairKey, generateCode, generateVerifier, getProvider, PAIR_TTL_SECONDS } from '../_lib.js';

export default async function handler(req, res) {
  if (req.method !== 'POST') {
    res.status(405).json({ error: 'method_not_allowed' });
    return;
  }

  const provider = (req.query.provider || req.body?.provider || 'anilist').toString().toLowerCase();
  const prov = getProvider(provider);
  if (!prov) {
    res.status(400).json({ error: 'unknown_provider' });
    return;
  }

  // Generate a code that isn't already taken (collisions are astronomically unlikely, but cheap to check).
  let code = '';
  for (let i = 0; i < 5; i++) {
    code = generateCode();
    if (!(await kv.get(pairKey(code)))) break;
  }

  // PKCE providers (MAL) need a verifier minted now: its challenge goes into the authorize URL and
  // the verifier travels back to the TV for the exchange.
  const entry = { status: 'pending', provider };
  if (prov.usesPkce) entry.codeVerifier = generateVerifier();
  await kv.set(pairKey(code), entry, { ex: PAIR_TTL_SECONDS });

  const base = process.env.PUBLIC_BASE_URL || `https://${req.headers.host}`;
  res.status(200).json({
    code,
    provider,
    verifyUrl: `${base}/api/pair/start?code=${code}`,
    expiresIn: PAIR_TTL_SECONDS,
  });
}
