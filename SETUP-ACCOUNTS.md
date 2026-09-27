# Snip — accounts & Google SSO setup

The code is deployed. Two things need your input in the Render dashboard
(https://dashboard.render.com → service `url-shortener` → Environment):

## 1. Environment variables

| Variable | Value | Why |
|---|---|---|
| `ADMIN_EMAILS` | `aman.siddiqui114@gmail.com` | Grants you the ADMIN role on first sign-in (already the default, set it explicitly to be safe) |
| `GOOGLE_CLIENT_ID` | _(from step 2)_ | Enables the "Sign in with Google" button |
| `GOOGLE_CLIENT_SECRET` | _(from step 2)_ | OAuth client secret |
| `SESSION_COOKIE_SECURE` | `true` | Marks the login cookie Secure (site is HTTPS-only) |

`BASE_URL` should already be `https://snip.nakhlavi.me` — leave it.

Without the Google variables, the app still works: email/password
registration and login function normally, the Google button is hidden.

## 2. Google OAuth client (free)

1. Go to https://console.cloud.google.com/apis/credentials (create a
   project first if you don't have one).
2. **Configure consent screen** (OAuth consent screen → External):
   app name "Snip", your email as developer contact. For personal use you
   can leave it in Testing mode and add your Gmail as a test user.
3. **Create Credentials → OAuth client ID → Web application.**
4. Under **Authorized redirect URIs**, add exactly:
   `https://snip.nakhlavi.me/login/oauth2/code/google`
5. Copy the **Client ID** and **Client secret** into the Render env vars
   above, then **Save** (Render redeploys automatically).

## 3. Verify

1. Open https://snip.nakhlavi.me/login.html → click **Sign in with Google**.
2. Sign in with `aman.siddiqui114@gmail.com` — you land on `/admin.html`.
3. The admin dashboard shows: total users, links, clicks, site visits,
   30-day charts, top/newest links, newest users.

## Notes

- Anyone can register with email + password; only addresses in
  `ADMIN_EMAILS` become admins.
- Users see and manage only their own links on `/dashboard.html`.
  Anonymous (signed-out) link creation still works as before.
- Analytics history starts at deploy time: daily click and visit charts
  fill in from today onward; lifetime click totals include old data.
