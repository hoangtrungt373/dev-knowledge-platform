# CLAUDE.md — identity-service

Module-local guidance for `identity-service`. Read alongside the root `CLAUDE.md`.

## What lives here

**Login, password/OTP handling, and Google/Facebook OAuth2 login are no longer this module's
concern — Keycloak owns that entire lifecycle now** (see `docs/CHANGELOG.md`'s Keycloak migration
entry, spanning several phases). This module narrowed to: JIT-syncing a local `User` row from a
verified Keycloak identity, the authenticated user's own profile (update/avatar), and — as of
`docs/CHANGELOG.md`'s later `[Unreleased]` entry — **registration came back**, with a different
implementation than before: rather than hashing/storing a password locally, `register` calls
Keycloak's own Admin REST API server-side (`KeycloakAdminService`), since Keycloak's token endpoint
can only authenticate an *existing* user, never create one. See the `KeycloakAdminService`/
`api/AuthApi` bullets below. Package root: `com.ttg.devknowledgeplatform.identity.*`.

**Now a standalone Spring Boot application, not part of the monolith** — see the
`project-microservices-extraction-plan` memory for the full extraction history. Concretely: its own
`IdentityServiceApplication` entry point, its own `identity` Postgres schema/database connection
(separate from the monolith's `product` schema, though the same physical Postgres instance/database —
per-service-per-schema, see root `CLAUDE.md`'s Database Conventions), its own port (`8082`), and its
own Liquibase changelog/docker-compose file. `gateway` no longer has a Maven dependency on this
module at all — nor does `social-service`, which used to be the one module allowed to reach into it.
**`gateway`-side HTTP proxying to this service is not built yet** — until it is, this service is only
reachable directly on its own port, same limitation `ecommerce-service` has.

- `IdentityServiceApplication` — `@SpringBootApplication` +
  `@Import({JacksonConfig.class, TraceContextFilter.class, StorageProperties.class,
  StorageConfig.class, StorageServiceImpl.class, KeycloakRealmRoleConverter.class})` entry point.
  Names the exact `infra` beans this module uses — the `MinioClient` bean plus the avatar-upload
  service built on it (`UserController`/`UserMapper`), and `KeycloakRealmRoleConverter` for this
  module's own local `security.KeycloakJwtAuthenticationConverter`'s role-mapping delegate — instead
  of widening `@ComponentScan`/`@ConfigurationPropertiesScan` to the whole sibling `infra` package
  the way an earlier revision did. That broad-scan approach took three rounds of real startup
  failures on `task-service` (a sibling in the identical shape) to get right before this reactor
  moved to explicit imports instead — see `infra/CLAUDE.md`'s note and `docs/CHANGELOG.md`'s
  `[Unreleased]` entry for the full history. `AsyncEventThreadPoolConfig` is deliberately **not**
  imported here — this module has no `@EventHandler` to dispatch. **No `@EntityScan`/
  `@EnableJpaRepositories` anymore** — `User`/`UserRepository` used to live in `common.entity`/
  `common.repository` (default JPA scanning, scoped to this class's own package tree, doesn't reach
  there, so both annotations used to widen the scan explicitly — a real bug this module hit and
  fixed once before, see `docs/CHANGELOG.md`), but moved into this module's own `entity`/
  `repository` packages below once `gateway` dropped its own local copy and this module became the
  sole consumer (see `docs/CHANGELOG.md`'s `[Unreleased]` entry). Default scanning now covers them
  without help — don't add those annotations back assuming they're still needed.
- `entity/User` — the sole system-of-record for user identity in this reactor now (see root
  `CLAUDE.md`'s Security section). Moved here from `common` outright, not just re-exported —
  `common` no longer has any `User`-shaped class at all. `@Table` still deliberately carries no
  hardcoded schema (this app's own `hibernate.default_schema: identity` resolves it to
  `identity.USER` at runtime) — see the "Never hardcode a schema" rule below.
- `repository/UserRepository` — this module's own repository now, moved here alongside `User`.
  Still extends `JpaSpecificationExecutor<User>`, though that capability has no real consumer
  today — it used to back `social-service`'s dynamic user search before that module moved to its
  own `SocialProfile`/`SocialProfileRepository` (see that module's `CLAUDE.md`); kept rather than
  silently dropped, since removing it isn't a decision this move should make on its own.
- `enums/{UserProvider,UserRole,UserStatus}` — moved here alongside `User`, for the same reason:
  all three are field types on `User`, and `identity-service` is now the only module that imports
  any of them.
- `api/` (+ `api/impl/`) — `AuthApi`/`AuthController` (`GET /api/v1/auth/user` — the only profile
  endpoint Keycloak's own `/userinfo` doesn't cover — plus `POST /api/v1/auth/register`, added back
  once Keycloak Admin API integration landed; renamed from `OAuth2Api`/`OAuth2Controller` once
  every other pre-Keycloak endpoint on it was deleted; `POST /api/v1/auth/resend-verification-email`
  added alongside real email verification, see the `KeycloakAdminService` bullet below) and
  `UserApi`/`UserController`
  (`updateProfile`/`uploadAvatar` — the pure "my own profile" operations that only need
  `UserService`/`UserMapper`/`infra`'s `StorageService`). `getPublicProfile` and `search` live in
  `social-service`'s own `UserApi`/`UserController` instead (same `/api/v1/users` mapping, different
  class) — that module resolves its own base lookup via `common`'s `UserRepository` directly now
  rather than reaching into this module, since this module can no longer be called in-process.
- `mapper/UserMapper` — MapStruct abstract class (not a plain interface) needing an injected
  `infra`'s `StorageService` for avatar presigned-URL resolution — MapStruct interfaces can't hold
  instance fields, same pattern as `social-service`'s `FriendMapper`/`MessagingMapper` (which now
  has its own, separately-duplicated `toUserInfo` method for the same shape — see that module's
  `CLAUDE.md`).
- `dto/user/UpdateProfileRequest`, `dto/UserInfoResponse` — the profile DTOs.
  `dto/auth/RegisterRequest`/`RegisterResponse` came back alongside the revived `register`
  endpoint — a different shape than the pre-Keycloak `dto/RegisterRequest` this replaces (no
  password-confirmation/OTP fields; `RegisterResponse` matches `gui`'s existing type exactly —
  the account is created *unverified* now (see the `KeycloakAdminService` bullet below) but still
  immediately usable, and `gui` logs the user in itself afterward rather than expecting tokens
  back from this response). Everything else auth-flow-specific
  (`dto/{OAuth2UserInfo,GoogleOAuth2UserInfo,FacebookOAuth2UserInfo,OAuth2UserInfoFactory}`) is
  still deleted, alongside the endpoints/services that used them.
- `config/KeycloakAdminProperties`/`KeycloakAdminConfig` — connection details + the `Keycloak`
  admin-client bean (`client_credentials` grant, via the `identity-service-admin` confidential
  client's service account — see `docker/keycloak/realm-export.json`) `KeycloakAdminService` calls
  the Admin REST API through. `KeycloakAdminProperties` needs an explicit `@Import` on
  `IdentityServiceApplication` (a plain `@ConfigurationProperties` class, no `@Component`
  stereotype — default scanning alone won't register it); `KeycloakAdminConfig` doesn't, since it's
  a real `@Configuration` class already inside this module's own scanned package tree.
  `KeycloakAdminProperties.frontendUrl` (`app.keycloak-admin.frontend-url`, env `FRONTEND_URL` —
  already passed to this service's container in `docker-compose.apps.yml`, previously unread by
  anything) is the GUI's own public base URL, used only by `sendVerifyEmail` below.
- `security/service/KeycloakAdminService` (+ `impl/`) — `createUser(RegisterRequest)`: builds a
  `UserRepresentation` (`enabled: true`, **`emailVerified: false`** — no longer the earlier "created
  pre-verified" scope choice, see `docs/CHANGELOG.md`) and calls `keycloak.realm(...).users().create(...)`,
  mapping the Admin API's own 409 response to `IdentityErrorCode.EMAIL_ALREADY_EXISTS`.
  **`username` is derived from the email's local part, not the full email** —
  `deriveUsernameBase` lowercases everything before the `@` and collapses any character outside
  `[a-z0-9_]` (dots, plusses, hyphens) to a single underscore, falling back to `"user"` if nothing
  survives sanitization; `withSuffix` appends a numeric disambiguator (truncating the base as
  needed to stay under `USERNAME_MAX_LENGTH = 30`, the same cap `gui`'s own username-edit
  validation enforces) on a real collision. `createUser` retries with the next suffix only when
  Keycloak's 409 body says the *username* collided (`"...same username"` vs `"...same email"` — the
  two conflict reasons Keycloak's own error message distinguishes); an email collision (or an
  exhausted 50-attempt suffix budget, astronomically unlikely) still surfaces as
  `EMAIL_ALREADY_EXISTS` to the caller, same as before this change.
  **This derivation logic alone did nothing while `realm-export.json`'s
  `registrationEmailAsUsername` stayed `true`** — Keycloak's own server source
  (`UsersResource.createUser()`/`UserResource.updateUserFromRep()`) unconditionally overrides
  *any* submitted `username` with `email` whenever that realm flag is on, for every caller
  (Admin REST API included, not just Keycloak's native self-registration form — an earlier revision
  of this note wrongly assumed the flag was form-scoped; it isn't). `editUsernameAllowed` gates a
  username *update* the same absolute way — `false` (this realm's original default) makes Keycloak
  refuse the change outright regardless of caller, which is what silently defeated
  `updateUsername` above too. Both are now `registrationEmailAsUsername: false`/
  `editUsernameAllowed: true` in `realm-export.json` — but that file only applies on a *fresh*
  Keycloak import (see `docker/keycloak/README.md`'s import-once caveat), so an already-provisioned
  dev instance needs the same two settings flipped by hand (Admin Console → Realm Settings → Login)
  or via a live `PUT /admin/realms/{realm}` call before either feature actually takes effect.
  `registrationAllowed: true` (Keycloak's own native self-registration form, reachable via its
  hosted login page) is a separate, still-open path neither flip touches.
  `loginWithEmailAllowed: true` (unrelated flag, unchanged) is what keeps `gui`'s password-grant
  login working regardless — its `username` grant parameter is always the caller's email
  (`authService.loginWithPassword`), which Keycloak resolves against either field when this flag is
  on, so login needed no change here. On success, extracts
  the new account's Keycloak id from the create response (`CreatedResponseUtil.getCreatedId`) and
  triggers a real verification email via Keycloak's own Admin API `send-verify-email` action
  (private `sendVerifyEmail` helper) — **best-effort**: a mail-server hiccup logs a warning but
  never fails registration itself, since the account already exists either way. `sendVerifyEmail`
  passes `client_id="gui"`/`redirect_uri=${frontendUrl}/login?emailVerified=true`
  (`UserResource.sendVerifyEmail`'s 3-arg overload — a `void` method, not `Response`; its generated
  client proxy throws on a non-2xx reply itself, so there's no status code to check by hand) so
  clicking the emailed link lands the user back in this app after Keycloak's own confirmation step,
  instead of Keycloak's default target (its `account` client's generic "your account has been
  updated" page — a dead end from this app's perspective). `/login`, not `/dashboard`, deliberately
  — `gui`'s `GuestRoute` already redirects an authenticated caller on to `/dashboard` (now
  forwarding the query string, see that class's own note), so one target correctly handles both "no
  longer logged in since registering" (lands on the real login form) and "still logged in" (bounced
  straight through) without this module needing to know or care which case applies.
  `emailVerified=true` lets whichever page actually renders show a one-time confirmation toast —
  see `gui/CLAUDE.md`. Keycloak's initial "Confirm validity... Click here to
  proceed" click-through itself is a separate, hardcoded anti-prefetch guard on action-token links
  (protects the one-time token from being silently consumed by an email client's link-prescanning)
  — not something exposed as a realm/client config toggle, so it isn't avoidable without a custom
  Keycloak theme, which wasn't judged worth the added complexity for this feature.
  `resendVerificationEmail(keycloakSubjectId)` is the same `sendVerifyEmail` call, exposed for
  `AuthController.resendVerificationEmail` (`POST /api/v1/auth/resend-verification-email`,
  authenticated — backs `gui`'s Dashboard "Resend email" banner action) to call on demand; throws
  `IdentityErrorCode.VERIFICATION_EMAIL_SEND_FAILED` if Keycloak rejects it.
  `AuthController.resendVerificationEmail` itself rejects with `IdentityErrorCode.EMAIL_ALREADY_VERIFIED`
  before ever calling Keycloak if the caller's own local `User.emailVerified` is already `true`.
  `updateUsername(keycloakSubjectId, newUsername)` — added so a username edit actually sticks:
  fetches the Keycloak `UserRepresentation` (`.toRepresentation()`), sets its `username`, and calls
  `.update(...)`, mapping a 409 to `CommonErrorCode.USER_USERNAME_ALREADY_EXISTS` (the same code the
  local-DB uniqueness check already threw) and any other non-2xx to a new
  `IdentityErrorCode.KEYCLOAK_USER_UPDATE_FAILED`. Called from `UserServiceImpl.updateProfile`
  *before* the local row is saved — see that method's own note below for why order matters here.
  Both `updateUsername` and `createUser`'s own retry loop now share one private `applyUsername`
  helper (rename attempt → `true`/`false`/throw), rather than each duplicating the
  fetch-representation/set-username/catch-409 shape.
  `assignDerivedUsername(keycloakSubjectId, email)` — added for brokered (Google/Facebook) logins,
  which land in Keycloak with `username == email` by default (a federated identity has no separate
  username concept of its own, so Keycloak's own "First Broker Login" flow falls back to the email —
  the same starting point local accounts had before `createUser` began deriving one). Reuses
  `deriveUsernameBase`/`withSuffix` and retries via `applyUsername` on a collision, same alphabet/cap
  as `createUser`. Called from `UserService#findOrCreateFromKeycloak` — see that method's own note
  below for the call site, the staleness wrinkle it reintroduces, and how `gui` handles it.
- `exception/IdentityErrorCode` — this module's first `ErrorCode` enum (implements `common`'s
  `ErrorCode` interface, mirroring `content-service`'s `ContentErrorCode`) — `EMAIL_ALREADY_EXISTS`/
  `KEYCLOAK_USER_CREATE_FAILED`/`EMAIL_ALREADY_VERIFIED`/`VERIFICATION_EMAIL_SEND_FAILED`/
  `KEYCLOAK_USER_UPDATE_FAILED`, all thrown by `KeycloakAdminService`/`AuthController`.
- `security/` — this app's own filter chain, independent of `gateway`'s, since it now runs on its
  own port and must guard its own endpoints regardless of whether `gateway` is proxying to it
  (mirrors `ecommerce-service`'s `security/` package). `SecurityConfig` requires authentication on
  everything except `/actuator/**` and `POST /api/v1/auth/register` — the latter is the one
  deliberate exception (a brand-new user has no token yet); unlike `content-service`/
  `ecommerce-service`, this module has no admin-only surface. **No local `KeycloakRealmRoleConverter`
  anymore** — this module
  uses `infra.security.KeycloakRealmRoleConverter` (the shared bean, see `infra/CLAUDE.md`) for
  `realm_access.roles` → `ROLE_*` mapping, picked up via this module's existing `@ComponentScan`
  reaching `infra`. `security/KeycloakJwtAuthenticationConverter` itself stays local, though — it's
  one of only two converters in the reactor (`social-service`'s is the other) that still does real
  divergent work beyond claims-only mapping: it JIT-provisions this module's own `User` row, so it
  wasn't a candidate for `infra`'s shared claims-only converter the way `gateway`'s/
  `ecommerce-service`'s/`task-service`'s/`content-service`'s/`ai-service`'s were. It *does* delegate
  to `infra`'s shared `KeycloakRealmRoleConverter` for the role-mapping half, rather than
  duplicating that too. It is still the one converter in the whole reactor that *delegates* to an
  in-process service rather than inlining the JIT-provisioning
  logic: it calls this module's own in-process `service/UserService.findOrCreateFromKeycloak`
  directly, since both live in this same standalone app — no duplication needed here.
- `security/service/UserService` (+ `impl/`) — narrowed to: `findOrCreateFromKeycloak`
  (`KeycloakUserInfo` carrier record, same package) — the JIT-provisioning entry point this module's
  own `KeycloakJwtAuthenticationConverter` calls on every authenticated request, resolving by
  `keycloakSubjectId` first, `findByEmail` as a fallback (links a pre-existing local row on its
  owner's first Keycloak login), else inserting a new row; only writes when a field actually
  changed — plus `resolveCurrentUser`/`findByEmail`/`findByUserUuid(Optional)`/`findById`/
  `updateStatus`/`updateProfile`/`updateAvatar`. Returns `common` entities, never this module's own
  DTOs. **`KeycloakUserInfo.emailVerified` (the token's `email_verified` claim,
  `KeycloakJwtAuthenticationConverter` reads it via `jwt.getClaimAsBoolean`) is re-synced into
  `User.emailVerified` on every request, not just at JIT-creation** — this is what makes the
  Dashboard verification banner ever turn off: once the caller clicks Keycloak's emailed
  verification link (out-of-band, no app code involved) and their access token eventually
  refreshes (or they re-log-in), the fresh token's `email_verified: true` claim flows through here
  into the local row `AuthController.getCurrentUser` reads back.
  **`updateProfile` renames the user in Keycloak too, not just the local row, when `username`
  actually changes.** The same re-sync described above cuts both ways: because
  `KeycloakJwtAuthenticationConverter` re-derives `username` from the token's `preferred_username`
  claim on every request, a local-only rename would be silently reverted on the caller's very next
  call — this bit real usage before it was caught (see `docs/CHANGELOG.md`'s `[Unreleased]` entry).
  `updateProfile` now only touches `username` when the trimmed/lowercased value differs from
  `user.getUsername()` (skips the Keycloak round trip on a firstName/lastName-only save), checks
  local uniqueness first (`existsByUsernameAndIdNot`, unchanged), then calls
  `KeycloakAdminService.updateUsername(user.getKeycloakSubjectId(), trimmed)` **before** setting the
  field locally — if Keycloak rejects the rename (its own uniqueness conflict or otherwise), nothing
  has been written locally yet, so there's nothing to roll back. `gui`'s side of this fix
  (`authService.refreshAccessToken()` after a successful username-changing save) is documented in
  `gui/CLAUDE.md` — the local row and Keycloak agree immediately after this call, but the *caller's
  already-issued access token* doesn't until a fresh one is obtained, same staleness window the
  email-verification claim already has.
  **Known gap, not fixed here**: `social-service`'s own `SocialProfile.username` JIT-syncs from
  this exact same Keycloak claim, independently of this module — a rename here doesn't propagate to
  friend search/public profiles until that service's own converter next runs against a fresh token
  (or is revisited to do the same Admin API push). See `docs/CHANGELOG.md`'s `[Unreleased]` entry.
  **`findOrCreateFromKeycloak` also renames a brokered login's default username, on first sight
  only.** Right after computing `isNew`, if `info.username()` (the JWT's `preferred_username`) still
  equals `info.email()`, it calls `KeycloakAdminService.assignDerivedUsername(info.subject(),
  info.email())` in a best-effort `try/catch` (log-and-continue on failure — never blocks login over
  a cosmetic default) and uses whatever it returns — falling back to `info.username()` unchanged on
  failure — as the value written to both `changed`'s comparison and `user.setUsername(...)`, instead
  of `info.username()` directly. Deliberately gated on `isNew`: this only ever needs to happen once
  per account, and repeating it on every request would just be a wasted Admin API round trip inside
  the authentication hot path. **This reintroduces the exact same claim-staleness problem
  `updateProfile`'s own fix above has** — the token this very request authenticated with was minted
  *before* the rename, so its `preferred_username` claim still says the old (email) value; left
  alone, the *next* authenticated request (from anywhere in the app, not just this one) would see
  that stale claim and JIT-sync the local row right back to it. Since `AuthCallback.tsx` (the
  Google/Facebook PKCE callback) always navigates to `/dashboard` first after a fresh login, `gui`
  closes this window in `Dashboard.tsx`'s own mount effect rather than in `AuthCallback.tsx` itself
  — see `gui/CLAUDE.md` for why a general "does the token's claim match what this response just
  said" check there, rather than a special-cased "was this a brand-new account" signal from the
  backend, is what it does.

**Deleted outright** (all superseded by Keycloak — do not resurrect any of this to "fix" a
compile error; the fix is always to route through Keycloak instead): `security/JwtTokenProvider`,
`security/jwt/{TokenClaims,AccessTokenClaims,RefreshTokenClaims}`, `security/PasswordEncoderConfig`
(nothing hashes/verifies passwords locally anymore — `User.password` is a vestigial `@NotNull`
column now set to a fixed placeholder string on JIT-created rows, never read/compared),
`security/service/{CustomOAuth2UserService,CustomOidcUserService}`,
`security/handler/OAuth2LoginSuccessHandler`, `security/service/StateTokenService`(`Impl`),
`security/service/RefreshTokenBlacklistService`(`Impl`), `service/{OtpService,EmailService}`(`Impl`)
(the whole OTP-code-email flow — a real replacement landed later: Keycloak's own "Verify Email"
action-token link, triggered via `KeycloakAdminService.createUser`/`resendVerificationEmail`'s
`sendVerifyEmail` calls, a click-through link rather than a 6-digit code, and — unlike this
deleted machinery — needing no mail-sending dependency of this module's own, since Keycloak sends
the email itself), every `OAuth2Api` endpoint except `getCurrentUser` **at the time of that
migration** (`register` came back later with a different implementation — see "What lives here"
above — do not read this as register being gone forever), and —
as part of the standalone extraction — `service/seed/UserSeeder`, relocated to `gateway` at the
time (it only ever wrote via `common`'s `UserRepository` directly, no other dependency on this
module, and `gateway` still needed to seed its own `product.USER` for the modules still embedded
there). `UserSeeder` was later deleted outright in `gateway` too, once `product.USER` itself was
dropped (see root `CLAUDE.md`'s Database Conventions section) — it has no home anywhere in this
reactor anymore, and this module still needs no seed data of its own: a seeded demo account has no
matching Keycloak identity, so this module's own `identity.USER` table only ever fills via
JIT-provisioning on a real login. The pom's leftover JJWT/Redis-for-blacklist/mail-for-OTP
dependencies (never cleaned up when the classes using them were deleted) were removed alongside the
standalone extraction too. A new, unrelated dependency was added back later for a different reason
— `org.keycloak:keycloak-admin-client` (version pinned via root `pom.xml`'s
`keycloak-admin-client.version`, matching the Keycloak server image), backing
`KeycloakAdminService`'s registration call. Don't confuse this with the deleted local-auth
dependencies above; this one talks to Keycloak's Admin REST API, not a local credential store.

## Rules specific to this module

- **Depends only on `common` + `infra`. Never add a Maven dependency on `gateway`, `social-service`,
  `content-service`, `ai-service`, or `task-service`** — and none of them may depend on this module
  either anymore, now that it's a standalone deployable with no shared Spring context. Cross-service
  communication would need a real network call (through `gateway`, once proxying exists), never a
  `pom.xml` entry.
- **`UserApi`/`UserController` here is intentionally a subset** of what used to be one class in
  `gateway` — resist the urge to "complete" it with `getPublicProfile`/`search`; those live in
  `social-service`'s own `UserApi` instead, because of the `FriendService` dependency they need.
- **Business logic (validation, uniqueness checks) belongs in `security/service`'s implementations,
  not in `api/impl` controllers** — a controller method should resolve the authenticated principal,
  build a call from the request DTO, call the service, map the result.
- **The `identity-service-admin` Keycloak client secret (`app.keycloak-admin.client-secret`) must
  never reach `gui` or any other frontend** — it's a confidential client with real
  `manage-users` capability, kept entirely separate from the public `gui`/`gui-password-login`
  clients for exactly this reason. If a future change needs this service to call any other
  privileged Keycloak Admin API operation, reuse this same client/service account rather than
  minting another one, but never widen its exposure beyond this module's own backend config.
- The types this module imports from `common`/`infra` rather than owning locally —
  `common.dto.PagedResponse` (unused here directly today, but the shared type other modules use for
  paged responses), `common.dto.CustomOAuth2User`, `infra.service.StorageService` — stay there since
  they're either genuinely cross-cutting or needed by other independent deployables too. Don't
  recreate local copies. **`User`/`UserRepository`/`UserProvider`/`UserRole`/`UserStatus` are the
  opposite case** — they used to live in `common` too, but moved into this module's own
  `entity`/`repository`/`enums` packages once this module became their sole consumer (see "What
  lives here" above); don't move them back to `common` on the assumption they're still shared.
- **Never hardcode a schema on anything shared with other deployables** — `entity.User`'s `@Table`
  deliberately has no `schema` attribute, even though nothing else maps this class anymore; this
  app's own `hibernate.default_schema: identity` (in `application.yml`) is what resolves it to
  `identity.USER` at runtime. This rule predates `User`'s move here — back when it lived in
  `common` and both `gateway` and `ecommerce-service` mapped it, a hardcoded schema on that same
  class silently broke `ecommerce-service`'s isolation for months (see `docs/CHANGELOG.md` for the
  incident) — the convention stuck even though `User` is no longer a shared class, since any future
  entity added here should follow the same default-schema-per-app pattern as every other module in
  this reactor.
- **Liquibase migrations for this module's own `USER` table live in this module's own changelog
  tree now** (`database/sql/identity-service.xml` + `2026/0.0.2/*.sql`), applied via the
  consolidated `services-liquibase` job in `docker-compose.apps.yml` — this module has **no**
  standalone `identity-service-liquibase.yml` file of its own (unlike `task-service`/
  `social-service`, which each kept a leftover standalone one-shot file from before this
  consolidation existed; see root `CLAUDE.md`'s Migrations — Liquibase section). Either way, the
  opposite of every embedded feature module (which still migrate via `gateway`'s changelog tree per
  root `CLAUDE.md`'s Database Conventions). Don't move future migrations back under `gateway`'s
  tree; this module owns its own schema lifecycle now, same as `ecommerce-service`.
