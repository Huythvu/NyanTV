// GET /api/pair/callback  (the AniList implicit-grant redirect target)
// Implicit grant returns the access token in the URL *fragment* (#access_token=...), which the
// browser never sends to the server. So we serve a tiny page that reads the fragment client-side
// and POSTs the token back to /api/pair/store, keyed by the pairing code carried in `state`.
export default function handler(req, res) {
  res.setHeader('Content-Type', 'text/html');
  res.status(200).send(`<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Finishing sign-in · NyanTV</title>
<style>
  :root { color-scheme: dark; }
  body { margin:0; min-height:100vh; display:grid; place-items:center; background:#14101a; color:#ede0e4;
         font-family:-apple-system,Segoe UI,Roboto,sans-serif; text-align:center; padding:24px; }
  .card { max-width:420px; } h1 { font-size:1.5rem; margin:0 0 12px; } p { color:#cbb8be; line-height:1.5; }
</style></head>
<body><div class="card"><h1 id="t">Finishing sign-in…</h1><p id="m">One moment.</p></div>
<script>
(async function () {
  function show(t, m) { document.getElementById('t').textContent = t; document.getElementById('m').textContent = m; }
  var hash  = new URLSearchParams(location.hash.slice(1));
  var query = new URLSearchParams(location.search);
  var token = hash.get('access_token');
  var state = hash.get('state') || query.get('state');
  var err   = hash.get('error')  || query.get('error');
  if (err)            { show('Login cancelled', 'You can close this tab and try again on your TV.'); return; }
  if (!token || !state) { show('Login failed', 'No token was returned. Close this tab and retry on your TV.'); return; }
  try {
    var r = await fetch('/api/pair/store', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ state: state, accessToken: token, expiresIn: hash.get('expires_in') }),
    });
    if (!r.ok) throw new Error('store ' + r.status);
    show("You're signed in ✅", 'Return to your TV — it will continue automatically. You can close this tab.');
  } catch (e) {
    show('Almost there', 'Could not hand the token to your TV (' + e.message + '). Retry on your TV.');
  }
})();
</script></body></html>`);
}
