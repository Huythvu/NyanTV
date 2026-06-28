// POST /api/pair/store  { state, accessToken, expiresIn }
// Receives the implicit-grant token captured by the callback page (client-side, from the URL
// fragment) and stashes it against the pairing code so the waiting TV can pick it up via /poll.
import { kv, pairKey, PAIR_TTL_SECONDS } from '../_lib.js';

export default async function handler(req, res) {
  if (req.method !== 'POST') {
    res.status(405).json({ error: 'method_not_allowed' });
    return;
  }

  const body = req.body || {};
  const code = (body.state || '').toString().toUpperCase();
  const accessToken = body.accessToken;
  if (!code || !accessToken) {
    res.status(400).json({ error: 'missing_params' });
    return;
  }

  const entry = await kv.get(pairKey(code));
  if (!entry) {
    res.status(404).json({ error: 'expired' });
    return;
  }

  await kv.set(
    pairKey(code),
    { status: 'done', provider: entry.provider, accessToken, expiresIn: body.expiresIn ?? null },
    { ex: PAIR_TTL_SECONDS },
  );
  res.status(200).json({ ok: true });
}
