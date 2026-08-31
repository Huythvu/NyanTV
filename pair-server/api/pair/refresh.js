// POST /api/pair/refresh  → renew an access token for a "web" client (browser/extension) that can't
// hold the client secret. Body: { provider, refreshToken }. Returns { accessToken, refreshToken,
// expiresIn }. Only providers with a server-side refresh (MAL) are supported.
import { getProvider } from '../_lib.js';

export default async function handler(req, res) {
  if (req.method !== 'POST') {
    res.status(405).json({ error: 'method_not_allowed' });
    return;
  }

  const provider = (req.query.provider || req.body?.provider || '').toString().toLowerCase();
  const refreshToken = (req.body?.refreshToken || req.body?.refresh_token || '').toString();

  const prov = getProvider(provider);
  if (!prov || typeof prov.refreshToken !== 'function') {
    res.status(400).json({ error: 'unsupported_provider' });
    return;
  }
  if (!refreshToken) {
    res.status(400).json({ error: 'missing_refresh_token' });
    return;
  }

  try {
    const tok = await prov.refreshToken(refreshToken);
    res.status(200).json(tok);
  } catch (e) {
    res.status(502).json({ error: 'refresh_failed', message: String(e.message || e).slice(0, 200) });
  }
}
