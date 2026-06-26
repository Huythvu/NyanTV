// POST /api/pair/new  → mints a pairing code for the TV to display.
// Body/query: { provider?: "anilist" } (defaults to anilist)
import { kv, pairKey, generateCode, getProvider, PAIR_TTL_SECONDS } from '../_lib.js';

export default async function handler(req, res) {
  if (req.method !== 'POST') {
    res.status(405).json({ error: 'method_not_allowed' });
    return;
  }

  const provider = (req.query.provider || req.body?.provider || 'anilist').toString().toLowerCase();
  if (!getProvider(provider)) {
    res.status(400).json({ error: 'unknown_provider' });
    return;
  }

  // Generate a code that isn't already taken (collisions are astronomically unlikely, but cheap to check).
  let code = '';
  for (let i = 0; i < 5; i++) {
    code = generateCode();
    if (!(await kv.get(pairKey(code)))) break;
  }

  await kv.set(pairKey(code), { status: 'pending', provider }, { ex: PAIR_TTL_SECONDS });

  const base = process.env.PUBLIC_BASE_URL || `https://${req.headers.host}`;
  res.status(200).json({
    code,
    provider,
    verifyUrl: `${base}/api/pair/start?code=${code}`,
    expiresIn: PAIR_TTL_SECONDS,
  });
}
