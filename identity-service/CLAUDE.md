# CLAUDE.md — identity-service

Module-local guidance for `identity-service`. Read alongside the root `CLAUDE.md`.

## What lives here

Auth and user-identity: local login/register/OTP verification, OAuth2/OIDC login (Google,
Facebook), JWT issuance/refresh/blacklisting, and the authenticated user's own profile
(update/avatar). Package root: `com.ttg.devknowledgeplatform.identity.*`. Extracted from the `api`
Maven module as one of several parallel workstreams peeling REST controllers/DTOs/mappers out of
`api` into feature modules — see root `CLAUDE.md`'s Module Structure and `docs/CHANGELOG.md` for
the "why now."

- `api/` (+ `api/impl/`) — `OAuth2Api`/`OAuth2Controller` (moved here in full: oauth2Authorization,
  login, register, verifyOtp, resendOtp, exchangeState, getCurrentUser, logout, refreshToken) and a
  **split-off** `UserApi`/`UserController` containing only `updateProfile`/`uploadAvatar` — the pure
  "my own profile" operations that only need `UserService`/`UserMapper`/`infra`'s `StorageService`.
  `getPublicProfile` and `search` deliberately stayed behind in `api`'s own `UserApi`/`UserController`
  (same `/api/v1/users` mapping, different class) because they need `social-service`'s
  `FriendService`/`FriendMapper`/`RelationshipStatus` for relationship enrichment, and this module
  must never depend on `social-service` (parallel sibling, same rule as `social-service`'s own "never
  depend on `api`/`ai-service`/`content-service`").
- `mapper/UserMapper` — MapStruct abstract class (not a plain interface) needing an injected
  `infra`'s `StorageService` for avatar presigned-URL resolution — MapStruct interfaces can't hold
  instance fields, same pattern as `social-service`'s `FriendMapper`/`MessagingMapper`.
- `dto/auth/` — `LoginRequest`, `LoginResponse`, `RegisterResponse`, `ExchangeStateRequest`,
  `LogoutRequest`, `RefreshTokenRequest`, `ResendOtpRequest`, `TokenResponse`, `VerifyOtpRequest`.
  `dto/user/UpdateProfileRequest`. `dto/RegisterRequest`, `dto/UserInfoResponse`,
  `dto/{OAuth2UserInfo,GoogleOAuth2UserInfo,FacebookOAuth2UserInfo,OAuth2UserInfoFactory}` — the
  provider-attribute normalization layer used only during OAuth2/OIDC login. Plain DTOs, no
  behavior, same shape as when they lived in `api`.
- `security/JwtTokenProvider` — signs/parses/validates JWTs (HMAC-SHA-512), backed by
  `security/jwt/TokenClaims` (sealed interface: `AccessTokenClaims`/`RefreshTokenClaims`).
  `security/PasswordEncoderConfig` — the *only* `PasswordEncoder` bean in the whole reactor; do not
  add a second one anywhere else.
- `service/seed/UserSeeder` — moved in from `gateway`, alongside `PasswordEncoder` (see above),
  which it uses to hash the shared demo password every seeded account gets. Writes directly via
  `common`'s `UserRepository`, not `UserService.registerLocalUser(...)` — same reasoning as every
  other seeder in the reactor (`content-service`'s `CategorySeeder`/`TagSeeder`, `social-service`'s
  `FriendGraphSeeder`/`UserBlockSeeder`): production registration always derives fields and
  enforces conflicts in ways incompatible with idempotent, externally-keyed seed rows. Extends
  `infra`'s `CsvSeeder<T>`; the actual `data/csv/users.csv` file stays under `gateway`'s
  `src/main/resources/` — moving the seeder class doesn't move the data file. `gateway`'s
  `DataSeedingRunner` imports it across the module boundary to run it in the right order (after
  content seeding, before `social-service`'s seeders, which reference users by `User.seedId`).
- `security/service/UserService` (+ `impl/`) — registration (OAuth2 + local), password hashing,
  profile/avatar updates, OTP-gated activation; returns `common` entities, never this module's own
  DTOs, so `api`'s trimmed `UserController` (the `getPublicProfile`/`search` half) can keep calling
  it directly. `CustomOAuth2UserService`/`CustomOidcUserService` — find-or-create on OAuth2/OIDC
  login (Facebook and Google respectively). `RefreshTokenBlacklistService`/`StateTokenServiceImpl` —
  both Redis-backed (blacklist TTL = token's remaining lifetime; state tokens TTL from
  `infra`'s `CacheTtlProperties` under `cache.ttl.state-tokens`, via `infra`'s `CacheNames`).
- `security/handler/OAuth2LoginSuccessHandler` — on successful OAuth2/OIDC login, mints JWTs, stores
  them in Redis under a short-lived state token, and redirects the browser to
  `{frontendUrl}/auth/callback?state=<token>` (never puts tokens directly in the redirect URL/browser
  history). The frontend then calls `OAuth2Api.exchangeState` to retrieve them.
- `service/EmailService` (+ `impl/`) — OTP email delivery via `JavaMailSender`.
  `service/OtpService` (+ `impl/`) — generate/verify/expire OTPs in Redis, keyed by email.

## Rules specific to this module

- **Depends only on `common` + `infra`. Never add a dependency on `api`, `social-service`,
  `content-service`, or `ai-service`** — same isolation rule as `social-service`, so these feature
  modules stay parallel siblings `api` wires together rather than a dependency tangle.
- **`CacheNames`/`CacheTtlProperties` live in `infra`, not here or in `api`** — they're shared by
  this module's `StateTokenServiceImpl` and `api`'s `RedisCacheConfig`, two modules that can't depend
  on each other, so they moved to `infra` (the "utility needed by two modules" case its own
  `CLAUDE.md` describes) rather than being duplicated.
- **`UserApi`/`UserController` here is intentionally a subset** of what used to be one class in
  `api` — resist the urge to "complete" it with `getPublicProfile`/`search`; those stay in `api`
  because of the `social-service` dependency they need. If a future refactor removes that need
  (e.g. relationship enrichment moves to a shared read model), revisit the split then, not before.
- **Business logic (validation, hashing, uniqueness checks) belongs in `security/service`'s
  implementations, not in `api/impl` controllers** — a controller method should resolve the
  authenticated principal, build a call from the request DTO, call the service, map the result.
- The 4 types this module imports from `common`/`infra` rather than owning locally —
  `common.dto.PagedResponse` (unused here directly today, but the shared type other modules use for
  paged responses), `common.dto.CustomOAuth2User`, `common.annotation.CurrentUserId`,
  `infra.service.StorageService` — were promoted out of `api` specifically so every feature module
  (this one included) could reach them without depending on `api`. Don't recreate local copies.
- Liquibase migrations for `User` still live under `api`'s changelog tree (`database/sql/`) — same
  as every other feature module; don't create a per-module changelog folder here.
