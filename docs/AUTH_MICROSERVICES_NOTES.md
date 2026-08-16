# Authentication & Authorization in Microservices — Key Components

Learning notes: general concepts first, then how each one maps onto this project's actual
implementation. Written as a working document for a point-by-point deep dive — see chat history
for the detailed walkthrough of each point once it happens.

## 1. Identity Provider (IdP)

A centralized service that authenticates users and issues tokens — the single source of truth for
"who is this user." Instead of every service having its own login logic and password store, one
dedicated system (Keycloak, Auth0, Okta) owns that responsibility.

- **Authentication protocol**: OAuth2 Authorization Code flow (+PKCE for public clients like SPAs)
  is the standard for user-facing login. Client Credentials flow is used for service-to-service
  auth with no human involved.
- **Federation/brokering**: the IdP can broker external providers (Google, Facebook login) so
  individual services never touch third-party OAuth directly.

## 2. Tokens (usually JWT)

The credential passed between client and services. A JWT has three parts: header (algorithm),
payload (claims — `sub`, `roles`, `exp`, custom claims), signature.

- **Access token**: short-lived (minutes), sent on every request, contains the claims services need
  to authorize.
- **Refresh token**: long-lived, used only to get a new access token without re-prompting login.
- Because it's *signed* (not encrypted), any service holding the IdP's public key can verify
  authenticity offline — no round-trip call to the IdP needed per request. That's the property that
  makes JWTs scale in a distributed system.

## 3. Resource Server (token verification)

Every backend service that receives requests acts as an OAuth2 **resource server**: it validates
the JWT's signature against the IdP's public keys (JWKS endpoint) and checks `exp`/`iss`/`aud`.
This is a *local, stateless* check — no database or IdP call on the hot path.

## 4. API Gateway / Single Entry Point

A reverse proxy in front of all services that's often where coarse-grained concerns get
centralized: routing, CORS, sometimes token validation before forwarding. The key architectural
question is *how much* auth logic lives here vs. pushed down to each service (see point 6).

## 5. Claims-based Authorization (stateless)

Once a token is verified, authorization decisions (is this user an admin? do they own this
resource?) can often be made **from the token's claims alone** — no database lookup required:

- Role/permission checks: read straight from a `roles`/`realm_access` claim.
- Ownership checks: compare the resource's owner column against the token's `sub` claim.

This avoids each service needing to call back to a central user database just to authorize a
request — which would reintroduce the tight coupling microservices are meant to avoid.

## 6. Per-service vs. centralized enforcement (the core design tension)

Two competing shapes:

- **Centralize at the gateway**: gateway validates the token once, downstream services trust an
  internal header/context. Fewer moving parts, but every downstream service now implicitly trusts
  the gateway completely (a network boundary becomes a security boundary).
- **Verify independently in every service** (JWT is self-contained, so this is cheap): each service
  is a resource server in its own right, defense-in-depth — a service is never one hop away from
  being wide open if someone bypasses the gateway. More duplicated config, but no single point of
  trust failure.

## 7. Local identity projection (when a service needs more than claims)

Sometimes a service needs to *search, list, or join across other users' data* (not just "is this
the caller"), which claims alone can't satisfy. Two common answers:

- **JIT (just-in-time) provisioning**: on first authenticated request, create/update a local row
  from the token's claims — lets a service maintain its own minimal profile without owning
  registration.
- **Event-driven read-model projection**: another service publishes user-changed events;
  interested services maintain their own local, eventually-consistent copy of just the fields they
  need.

Both avoid a service calling back to a central "user service" synchronously on every request.

## 8. Service-to-service (M2M) auth

Distinct from user-facing auth: when service A calls service B directly (not through the gateway),
it needs its own credential — typically Client Credentials grant tokens, or a shared internal API
key/mTLS for trusted internal traffic — since there's no user token to forward, or the call is on a
private internal path (e.g., internal admin/ingestion endpoints).

---

## How this project applies each point (summary — to be expanded point by point)

| # | Concept | This project |
|---|---|---|
| 1 | IdP | Keycloak, one realm, hosted login page, Google/Facebook brokered inside Keycloak |
| 2 | Tokens | JWT access tokens; `sub` claim stands in for `userUuid` throughout |
| 3 | Resource server | All seven deployables (`gateway`, `ecommerce-service`, `identity-service`, `task-service`, `social-service`, `content-service`, `ai-service`) independently verify against the same JWKS/issuer-uri |
| 4 | Gateway | `gateway` (port 8080) is the single external entry point — routing only (Spring Cloud Gateway Server MVC), not a trust boundary |
| 5 | Claims-based authz | `@CurrentUserId`, `realm_access.roles` → `UserRole`; ownership checks via plain `ownerUuid`/`authorUuid`/`userUuid` columns compared to `sub`, not FKs |
| 6 | Per-service vs. centralized | This project chose **verify everywhere** — every service has its own `KeycloakJwtAuthenticationConverter`; `gateway` does no auth enforcement of its own beyond routing |
| 7 | Local identity projection | Mixed: `identity-service` JIT-provisions the one canonical `User` row (`identity.USER`); `social-service` JIT-provisions its own lean `SocialProfile` (`social.PROFILE`) for search/list/join needs; `ecommerce-service`/`task-service`/`content-service`/`ai-service`/`gateway` persist no user row at all (claims-only, "Option C") |
| 8 | M2M auth | One real inter-service call today: `ai-service` → `content-service`'s `/internal/content-items/**`, gated by a shared `X-Internal-Api-Key` header (not a JWT) |

Next: go through points 1–8 one at a time against this codebase's actual files
(`gateway/security/`, each service's `KeycloakJwtAuthenticationConverter`, `GatewayRoutesConfig`,
`ContentServiceClient`, etc.) to confirm the table above still matches the code, and dig into any
gaps or hardening opportunities found along the way.

---

## Point 1 in detail: Identity Provider (Keycloak) in this project

**Setup** — Keycloak runs as its own container (`docker-compose.infra.yml:63-86`,
image `quay.io/keycloak/keycloak:26.0`, `start-dev --import-realm`), sharing the `dev-premier`
Postgres instance but with its own `keycloak` schema (created by `docker/postgres/init.sql`,
per this project's per-service-per-schema convention). The realm is imported from
`docker/keycloak/realm-export.json` on every startup — the whole IdP configuration is
version-controlled in this repo, not clicked together by hand.

**Realm `dev-knowledge-platform`** — two realm roles, `USER`/`ADMIN`, plus a composite
`default-roles-dev-knowledge-platform` every new user gets. Two clients:
- `gui` — public SPA client, Authorization Code flow with **mandatory PKCE**
  (`pkce.code.challenge.method: S256`), implicit/direct-grant both disabled. Correct shape for a
  browser client that can't hold a secret.
- `diagnostic-cli` — public, direct-grant only, dev/smoke-test only, unused by real app code.

Google/Facebook brokering entries exist but are `enabled: false` with literal placeholder
client ID/secret strings — scaffolded, not live. `syncMode: IMPORT` means (once enabled) external
profile attributes copy in once at first login, not on every login.

**One realm, seven independent verifiers** — every one of the seven services (`gateway` included)
uses the identical `issuer-uri`:
`${KEYCLOAK_ISSUER_URI:http://localhost:8180/realms/dev-knowledge-platform}`.
`gateway`'s `application-docker.yml` omits the default deliberately, forcing an explicit env value
in that profile rather than risking a silent wrong-host fallback inside the container network.

**Known gap (documented, not fixed here)** — the GUI's own `authService.ts`/`authApi.ts` still call
homegrown `/oauth2/authorization/{provider}` and `login`/`verify-otp`-style endpoints instead of
talking to Keycloak; both `identity-service/CLAUDE.md` and `gui/CLAUDE.md` already flag these as
stale/broken now that Keycloak owns the full login lifecycle. Conceptually this is a **Facade**
(GUI's `authApi` module) that hasn't caught up to a swapped-out identity provider underneath it.

Traced concretely (2026-08-13): **no login path works today, full stop.**
- `Login.tsx`/`SignUp.tsx` submit handlers POST straight to `authApi.login`/`register` (no redirect
  to Keycloak at all) → `identity-service`'s `AuthController` only implements
  `GET /api/v1/auth/user` now; `login`/`register`/`verify-otp`/`resend-otp`/`refresh`/
  `exchange-state`/`oauth2/authorization/{provider}` were all deleted server-side → 404.
- The social-login buttons do redirect (`authService.startOAuth` → `window.location.href`), but to
  the same dead `/api/v1/auth/oauth2/authorization/{provider}` path, never to Keycloak's hosted
  login page.
- `AuthCallback.tsx` reads an old `?state=<uuid>` param and calls the also-deleted
  `POST /api/v1/auth/exchange-state` — no code anywhere reads a real OAuth2 `?code=` param or
  exchanges it against Keycloak's `/token` endpoint.
- Zero PKCE code exists in the GUI (`code_verifier`/`S256`/etc. — no matches); no
  `oidc-client-ts`/`keycloak-js`-style library is wired into any auth page.
- `gateway` doesn't fill the gap either — its `SecurityConfig` is a pure resource server, no
  `oauth2Login()` client registration, so there's no gateway-side login-initiation endpoint either.

The correct flow (given `gui`'s realm config: `standardFlowEnabled` + mandatory PKCE,
`directAccessGrantsEnabled: false`) is: browser generates a `code_verifier`/`code_challenge` →
full-page redirect to Keycloak's hosted `/protocol/openid-connect/auth` page → user authenticates
*on Keycloak's own page*, not anything the React app renders → Keycloak redirects back to
`/auth/callback?code=...` → GUI exchanges `code`+`code_verifier` at Keycloak's `/token` endpoint →
stores the returned JWTs. None of this is implemented yet — this is the real next-build item once
the auth walkthrough is done.

**JIT provisioning, concretely** — `identity-service`'s `KeycloakJwtAuthenticationConverter.convert()`
reads `sub`/`email`/`preferred_username`/`given_name`/`family_name` plus a derived `admin` boolean
from `realm_access.roles`, and rejects any token whose `typ` claim isn't `"Bearer"` (guards against
accidentally accepting a refresh/ID token as a bearer credential). `UserServiceImpl
.findOrCreateFromKeycloak()` looks up by `keycloakSubjectId`, falls back to lookup-by-email, else
creates a new `User` row — and only writes fields that actually changed.
