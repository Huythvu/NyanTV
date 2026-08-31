// GET /api/pair/callback?code=<authCode>&state=<pairingCode>
// AniList redirects the phone/PC browser here after login. We exchange the authorization code for a
// token (server-side, with the client secret) and stash it against the pairing code so the waiting
// TV can pick it up via /api/pair/poll.
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

  // "token" mode (browser/extension client): exchange the code for a token here, server-side, so
  // the client never needs the secret. The waiting client polls and gets the token directly.
  if (entry.mode === 'token' && typeof provider.exchangeCode === 'function') {
    try {
      const tok = await provider.exchangeCode(authCode, entry.codeVerifier ?? null);
      await kv.set(
        pairKey(pairCode),
        { status: 'done', provider: entry.provider, ...tok },
        { ex: PAIR_TTL_SECONDS },
      );
      res.status(200).send(page("You're signed in ✅", 'Return to the app — it will continue automatically. You can close this tab.'));
    } catch (e) {
      await kv.set(
        pairKey(pairCode),
        { status: 'error', provider: entry.provider, error: String(e.message || e).slice(0, 200) },
        { ex: PAIR_TTL_SECONDS },
      );
      res.status(502).send(page('Sign-in failed', 'Something went wrong finishing sign-in. Close this tab and try again.'));
    }
    return;
  }

  // Default (TV/device flow): hand the raw auth code (and the redirect_uri it was issued for) back
  // to the device. The device does the code->token exchange itself: it warms up a Cloudflare
  // clearance cookie via its WebView-backed network client first, which a datacenter request here
  // can't do — so the exchange runs there.
  await kv.set(
    pairKey(pairCode),
    {
      status: 'done',
      provider: entry.provider,
      code: authCode,
      redirectUri: provider.redirectUri(),
      // PKCE providers (MAL) need the verifier at exchange time; pass it through to the device.
      codeVerifier: entry.codeVerifier ?? null,
    },
    { ex: PAIR_TTL_SECONDS },
  );
  res.status(200).send(page("You're signed in ✅", 'Return to your TV — it will continue automatically. You can close this tab.'));
}
