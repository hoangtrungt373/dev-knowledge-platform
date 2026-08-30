# Keycloak dev realm

`realm-export.json` is imported automatically on container start (`start-dev --import-realm`) —
see the `keycloak` service in `docker-compose.infra.yml`. Import only happens if
the `dev-knowledge-platform` realm doesn't already exist in the `keycloak` Postgres schema; if you
hand-edit the realm via the Admin Console (`http://localhost:8180`, `admin`/`admin`) and want the
change captured here, re-export it (Realm Settings → Action → Partial/Full Export) and it won't
take effect on an existing volume until `docker compose down -v`.

## Clients

- **`gui`** — the real SPA client. Public, Authorization Code + PKCE only, no direct grants. This
  is what `gui`'s OIDC config points at.
- **`diagnostic-cli`** — not used by any application code. Has Direct Access Grants (Resource Owner
  Password) enabled so a `curl`-based smoke test can fetch a real token without driving a browser
  through the hosted login page. Only exists for local verification during development — never
  reuse this client shape in a non-dev realm.

## Google/Facebook login

Both are imported **disabled**, with placeholder `REPLACE_WITH_..._CLIENT_ID`/`_SECRET` values —
real credentials can't be committed to the repo. To enable one:

1. Fill in the real `clientId`/`clientSecret` for that provider, either by editing this file before
   first import or via Admin Console → Identity Providers → (google|facebook) → Settings.
2. Set `enabled: true`.
3. **Update the provider's own app registration** (Google Cloud Console / Facebook Developer
   Console) so its redirect URI points at Keycloak's broker callback, not the old Spring
   Security one:
   `http://localhost:8180/realms/dev-knowledge-platform/broker/{google|facebook}/endpoint`

Step 3 is external to this repo and can't be automated — it has to be done once per environment
wherever the provider's own app registration lives.

## Login page: registration and Google hidden on purpose

`registrationAllowed: false` and the `google` identity provider's `hideOnLoginPage: true` both
exist so Keycloak's own bare hosted login page — the one `AdminLogin.tsx` redirects to (it never
passes `kc_idp_hint`, unlike the regular flow below) — doesn't show a "Register" link or a
"Continue with Google" button that would just lead a non-admin down a dead end (the callback
rejects anyone without the `ADMIN` realm role regardless). Neither setting affects the app's
regular, non-admin flows:

- `SignUp.tsx` never reaches Keycloak's own hosted registration page at all — it calls
  `identity-service`'s own endpoint, which creates the Keycloak account server-side via the Admin
  REST API. `registrationAllowed` only gates the "Register" link on Keycloak's *own* login form,
  which nothing in this app's real sign-up path relies on.
- `Login.tsx`'s own "Continue with Google" button passes `kc_idp_hint=google` when it redirects,
  which sends the browser straight to Google — it never renders Keycloak's account-chooser page at
  all, so `hideOnLoginPage` (which only controls whether the button appears *on that page*) has no
  effect on it.

If a future admin-facing flow ever needs registration or social login back on Keycloak's own login
page, flip these back per-setting rather than assuming both need to move together — they're
independent toggles that happened to need the same fix for the same underlying reason here.

## Smoke-test user

`kc-smoke-test@devknowledge.local` / `smoke-test-password` (`USER`+`ADMIN` roles) exists purely to
verify the realm imports correctly and issues well-formed tokens (via `diagnostic-cli`'s password
grant) — not a real demo account. Phase 5 of the Keycloak migration adds real seeded demo accounts
with deterministic subject ids matching `data/csv/users.csv`.
