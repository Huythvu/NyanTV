// GET /api/pair/callback?code=<authCode>&state=<pairingCode>
// The provider redirects the phone/PC browser here after a successful login. We exchange the
// authorization code for a token (server-side, with the client secret) and stash it against the
// pairing code so the waiting TV can pick it up via /api/pair/poll.
import { kv, pairKey, getProvider, page, PAIR_TTL_SECONDS } from '../_lib.js';

export default async function handler(req, res) {
  res.setHeader('Content-Type', 'text/html');

  const authCode = (req.query.code || '').toString();
  const pairCode = (req.query.state || '').toString().toUpperCase();
  const error = (req.query.error || '').toString();

  if (error) {
    res.status(400).send(page('Login cancelled', 'You can close this tab and try again on your TV.'));
    return;
  }

  const entry = await kv.get(pairKey(pairCode));
  if (!entry) {
    res.status(400).send(page('Pairing expired', 'Too much time passed. Start pairing again on your TV.'));
    return;
  }

  const provider = getProvider(entry.provider);
  if (!provider) {
    res.status(400).send(page('Unknown provider', 'The pairing session referenced an unsupported provider.'));
    return;
  }

  // Hand the raw auth code (plus the redirect_uri it was issued for) back to the TV, which finishes
  // the code->token exchange itself. The TV runs on a residential IP with a Cloudflare-aware client,
  // so it clears AniList's Cloudflare challenge that a datacenter exchange here cannot reliably pass.
  await kv.set(
    pairKey(pairCode),
    { status: 'done', provider: entry.provider, code: authCode, redirectUri: provider.redirectUri() },
    { ex: PAIR_TTL_SECONDS },
  );
  res.status(200).send(page("You're signed in ✅", 'Return to your TV — it will continue automatically. You can close this tab.'));
}
