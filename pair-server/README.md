# NyanTV Pairing Relay

A tiny stateless-ish relay that gives NyanTV a **TV-friendly OAuth device-pairing login**:
the TV shows a short code + QR, you log in on your phone/PC (a real browser that passes
Cloudflare), and the TV polls for the finished token. AniList first; MAL can be added later
with the same pattern.

This folder is a **self-contained Vercel project**. It does not interact with the Android app
build — deploy it separately with **Root Directory = `pair-server`**.

## Why a relay is needed

Neither AniList nor MAL supports the OAuth Device Authorization Grant (RFC 8628), so a TV
can't pair without a small server. The relay also moves the OAuth **client secret off the
device** (it currently ships inside the APK), which is strictly more secure.

## Flow

```
TV                         Relay (this)                       AniList            Phone/PC
 │  POST /api/pair/new ───────►│                                                   │
 │  ◄── { code, verifyUrl } ───│                                                   │
 │  show code + QR(verifyUrl)                                                       │
 │                              │  ◄──────────────── opens verifyUrl ──────────────│
 │                              │  302 → AniList authorize (state=code) ──────────►│
 │                              │                         user logs in ───────────►│
 │                              │  ◄── GET /api/pair/callback?code=..&state=code ──│ (redirect)
 │                              │  exchange code→token (with secret) ─────►│        │
 │                              │  store token under `code`                         │
 │  GET /api/pair/poll?code ───►│                                                   │
 │  ◄── { status:"pending" } ──│  (repeat until done)                              │
 │  GET /api/pair/poll?code ───►│                                                   │
 │  ◄── { status:"done", accessToken, … } ─ (deleted after this single read)       │
 │  store token locally                                                            │
```

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/pair/new` | Mint a pairing code. Optional `?provider=anilist`. Returns `{ code, verifyUrl, expiresIn }`. |
| `GET`  | `/api/pair/start?code=…` | Opened by the phone (the QR/link). Redirects into the provider's OAuth page. |
| `GET`  | `/api/pair/callback?code=…&state=…` | Provider redirect target. Exchanges the code and stores the token. |
| `GET`  | `/api/pair/poll?code=…` | The TV polls this. `{status:"pending"\|"done"\|"expired"}`; returns the token once on `done`. |

## Deploy (Vercel)

1. **Import** this repo into Vercel as a new project; set **Root Directory** to `pair-server`.
2. **Set up Firestore storage** (free Spark plan — it does **not** archive for inactivity the way
   Upstash's free Redis does, which is why the relay uses it):
   - In the [Firebase console](https://console.firebase.google.com/), create/use a project with
     **Firestore** in Native mode.
   - Add a **TTL policy**: Firestore → *TTL* → create a policy on collection `pairing`, field
     `expireAt`. This auto-deletes expired pairing entries (sweeps can lag up to ~24h; the relay also
     enforces expiry on read, so correctness never depends on the sweep).
   - Create a **service account key**: Project settings → *Service accounts* → *Generate new private
     key*. This downloads a JSON file with full Firestore access (it uses IAM, so your security rules
     can stay default-deny — public clients still can't touch the `pairing` collection).
3. **Set env vars** (Settings → Environment Variables), see `.env.example`:
   - `FIREBASE_SERVICE_ACCOUNT` = the **entire** service-account JSON, pasted as one value. (The
     relay reads the project id from it; set `FIREBASE_PROJECT_ID` only to override.)
   - `ANILIST_CLIENT_ID`, `ANILIST_CLIENT_SECRET`
   - `ANILIST_REDIRECT_URI` = `https://<your-domain>/api/pair/callback`
   - `MAL_CLIENT_ID`, `MAL_CLIENT_SECRET` (for extension/browser login)
   - `PUBLIC_BASE_URL` = `https://<your-domain>` (optional)
4. **Register the AniList client** at <https://anilist.co/settings/developer> with the
   **Redirect URL** set to the exact same `…/api/pair/callback` value.
5. Deploy, then point your new domain at the project. **Redeploy after changing any env var** —
   Vercel doesn't apply env changes to an already-live deployment.

## Quick test

```bash
# 1. Mint a code
curl -X POST https://<your-domain>/api/pair/new
# → { "code":"ABCD2345", "verifyUrl":"https://<your-domain>/api/pair/start?code=ABCD2345", ... }

# 2. Open verifyUrl in a browser, log into AniList.

# 3. Poll (the TV does this)
curl "https://<your-domain>/api/pair/poll?code=ABCD2345"
# → { "status":"done", "accessToken":"…" }   (only once)
```

## Security notes

- Codes are short-lived (10 min), single-use on delivery, and drawn from an unambiguous alphabet.
- The token is held only briefly in Firestore and deleted on first successful poll.
- The client secret lives only in server env vars — never in the app.
- Hardening to add later if abused: rate-limit `/api/pair/new` per IP, and add MAL (PKCE) as a
  second provider in `api/_lib.js`.
