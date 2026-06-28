// GET /api/pair/poll?code=XXXX  → the TV polls this until the login completes.
// Returns { status: "pending" | "done" | "expired" }. On "done" it also returns the token and
// deletes the entry (single-use), so a token is handed out exactly once.
import { kv, pairKey } from '../_lib.js';

export default async function handler(req, res) {
  if (req.method !== 'GET') {
    res.status(405).json({ error: 'method_not_allowed' });
    return;
  }

  const code = (req.query.code || '').toString().toUpperCase();
  const entry = await kv.get(pairKey(code));

  if (!entry) {
    res.status(404).json({ status: 'expired' });
    return;
  }

  if (entry.status !== 'done') {
    res.status(200).json({ status: 'pending' });
    return;
  }

  // Hand the result over exactly once. New flow returns the auth code + redirect_uri for the TV to
  // exchange; the accessToken fields stay for backward compatibility with any older payload.
  await kv.del(pairKey(code));
  res.status(200).json({
    status: 'done',
    provider: entry.provider,
    code: entry.code ?? null,
    redirectUri: entry.redirectUri ?? null,
    accessToken: entry.accessToken ?? null,
    refreshToken: entry.refreshToken ?? null,
    expiresIn: entry.expiresIn ?? null,
  });
}
