// GET /api/pair/start?code=XXXX  → the URL the phone/PC opens (from the QR/link).
// Looks up the pairing session and redirects the real browser into the provider's OAuth page,
// carrying the pairing code in `state` so the callback can match it back to this TV.
import { kv, pairKey, getProvider, page } from '../_lib.js';

export default async function handler(req, res) {
  const code = (req.query.code || '').toString().toUpperCase();
  const entry = await kv.get(pairKey(code));

  if (!entry) {
    res.status(400).setHeader('Content-Type', 'text/html');
    res.send(page('Pairing not found', 'This code is invalid or has expired. Start pairing again on your TV.'));
    return;
  }

  const provider = getProvider(entry.provider);
  if (!provider || !provider.clientId() || !provider.redirectUri()) {
    res.status(500).setHeader('Content-Type', 'text/html');
    res.send(page('Not configured', 'The pairing server is missing its provider configuration.'));
    return;
  }

  // For PKCE providers the "plain" code_challenge is the verifier we minted in /new.
  res.writeHead(302, {
    Location: provider.buildAuthorizeUrl(code, { codeChallenge: entry.codeVerifier }),
  });
  res.end();
}
