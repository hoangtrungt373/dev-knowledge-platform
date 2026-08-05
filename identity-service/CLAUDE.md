# CLAUDE.md — identity-service

Module-local guidance for `identity-service`. Read alongside the root `CLAUDE.md`.

## What lives here

**Login, registration, password/OTP handling, and Google/Facebook OAuth2 login are no longer this
module's concern — Keycloak owns that entire lifecycle now** (see `docs/CHANGELOG.md`'s Keycloak
migration entry, spanning several phases). This module narrowed to: JIT-syncing a local `User` row
from a verified Keycloak identity, and the authenticated user's own profile (update/avatar).
Package root: `com.ttg.devknowledgeplatform.identity.*`.

- `api/` (+ `api/impl/`) — `AuthApi`/`AuthController` (just `GET /api/v1/auth/user`, the only
  endpoint Keycloak's own `/userinfo` doesn't cover — this app's avatar/local-username profile
  shape; renamed from `OAuth2Api`/`OAuth2Controller` once every other endpoint on it was deleted)
  and `UserApi`/`UserController` (`updateProfile`/`uploadAvatar` — the pure "my own profile"
  operations that only need `UserService`/`UserMapper`/`infra`'s `StorageService`). `getPublicProfile`
  and `search` live in `social-service`'s own `UserApi`/`UserController` instead (same `/api/v1/users`
  mapping, different class) because they need `FriendService`/`FriendMapper`/`RelationshipStatus`
  for relationship enrichment — `social-service` reaches into this module's `UserService`/
  `UserMapper` for the base lookup rather than the other way around, since this module must never
  depend on `social-service` (this module stays a pure leaf: `common`+`infra` only, no
  feature-module dependencies at all — `social-service`/`content-service`/`ai-service` are the ones
  allowed to depend on it, never the reverse).
- `mapper/UserMapper` — MapStruct abstract class (not a plain interface) needing an injected
  `infra`'s `StorageService` for avatar presigned-URL resolution — MapStruct interfaces can't hold
  instance fields, same pattern as `social-service`'s `FriendMapper`/`MessagingMapper`.
- `dto/user/UpdateProfileRequest`, `dto/UserInfoResponse` — the only DTOs left in this module.
  Everything auth-flow-specific (`dto/auth/*`, `dto/RegisterRequest`,
  `dto/{OAuth2UserInfo,GoogleOAuth2UserInfo,FacebookOAuth2UserInfo,OAuth2UserInfoFactory}`) was
  deleted alongside the endpoints/services that used them.
- `security/service/UserService` (+ `impl/`) — narrowed to: `findOrCreateFromKeycloak`
  (`KeycloakUserInfo` carrier record, same package) — the JIT-provisioning entry point `gateway`'s
  `KeycloakJwtAuthenticationConverter` calls on every authenticated request, resolving by
  `keycloakSubjectId` first, `findByEmail` as a fallback (links a pre-existing local row on its
  owner's first Keycloak login), else inserting a new row; only writes when a field actually
  changed — plus `resolveCurrentUser`/`findByEmail`/`findByUserUuid(Optional)`/`findById`/
  `updateStatus`/`updateProfile`/`updateAvatar`. Returns `common` entities, never this module's own
  DTOs, so `social-service`'s `UserController` can keep calling it directly across the module
  boundary.
- `service/seed/UserSeeder` — moved in from `gateway`; writes directly via `common`'s
  `UserRepository`, not through `UserService`, same reasoning as every other seeder in the reactor
  (`content-service`'s `CategorySeeder`/`TagSeeder`, `social-service`'s `FriendGraphSeeder`/
  `UserBlockSeeder`): production logic always derives fields and enforces conflicts in ways
  incompatible with idempotent, externally-keyed seed rows. Extends `infra`'s `CsvSeeder<T>`; the
  actual `data/csv/users.csv` file stays under `gateway`'s `src/main/resources/` — moving the
  seeder class doesn't move the data file. `gateway`'s `DataSeedingRunner` imports it across the
  module boundary to run it in the right order (after content seeding, before `social-service`'s
  seeders, which reference users by `User.seedId`). A future phase of the Keycloak migration will
  give seeded demo accounts a matching, deterministic `keycloakSubjectId` baked into both this
  seeder's CSV and the checked-in dev realm export — not done yet.

**Deleted outright** (all superseded by Keycloak — do not resurrect any of this to "fix" a
compile error; the fix is always to route through Keycloak instead): `security/JwtTokenProvider`,
`security/jwt/{TokenClaims,AccessTokenClaims,RefreshTokenClaims}`, `security/PasswordEncoderConfig`
(nothing hashes/verifies passwords locally anymore — `User.password` is a vestigial `@NotNull`
column now set to a fixed placeholder string on JIT-created rows, never read/compared),
`security/service/{CustomOAuth2UserService,CustomOidcUserService}`,
`security/handler/OAuth2LoginSuccessHandler`, `security/service/StateTokenService`(`Impl`),
`security/service/RefreshTokenBlacklistService`(`Impl`), `service/{OtpService,EmailService}`(`Impl`)
(the whole OTP-email flow — Keycloak's own "Verify Email" required action replaces it, a click-
through link rather than a 6-digit code), and every `OAuth2Api` endpoint except `getCurrentUser`.

## Rules specific to this module

- **Depends only on `common` + `infra`. Never add a dependency on `gateway`, `social-service`,
  `content-service`, or `ai-service`** — this module stays a pure leaf so any of those can safely
  depend on it (as `social-service` already does) without ever risking a cycle.
- **`UserApi`/`UserController` here is intentionally a subset** of what used to be one class in
  `gateway` — resist the urge to "complete" it with `getPublicProfile`/`search`; those live in
  `social-service`'s own `UserApi` instead, because of the `FriendService` dependency they need and
  this module's rule of staying a pure `common`+`infra` leaf. If a future refactor removes that need
  (e.g. relationship enrichment moves to a shared read model), revisit the split then, not before.
- **Business logic (validation, uniqueness checks) belongs in `security/service`'s implementations,
  not in `api/impl` controllers** — a controller method should resolve the authenticated principal,
  build a call from the request DTO, call the service, map the result.
- The 4 types this module imports from `common`/`infra` rather than owning locally —
  `common.dto.PagedResponse` (unused here directly today, but the shared type other modules use for
  paged responses), `common.dto.CustomOAuth2User`, `common.annotation.CurrentUserId`,
  `infra.service.StorageService` — were promoted out of `gateway` specifically so every feature
  module (this one included) could reach them without depending on `gateway`. Don't recreate local
  copies.
- Liquibase migrations for `User` still live under `gateway`'s changelog tree (`database/sql/`) —
  same as every other feature module; don't create a per-module changelog folder here.
