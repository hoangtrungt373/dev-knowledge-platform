# CLAUDE.md — identity-service

Module-local guidance for `identity-service`. Read alongside the root `CLAUDE.md`.

## What lives here

**Login, registration, password/OTP handling, and Google/Facebook OAuth2 login are no longer this
module's concern — Keycloak owns that entire lifecycle now** (see `docs/CHANGELOG.md`'s Keycloak
migration entry, spanning several phases). This module narrowed to: JIT-syncing a local `User` row
from a verified Keycloak identity, and the authenticated user's own profile (update/avatar).
Package root: `com.ttg.devknowledgeplatform.identity.*`.

**Now a standalone Spring Boot application, not part of the monolith** — see the
`project-microservices-extraction-plan` memory for the full extraction history. Concretely: its own
`IdentityServiceApplication` entry point, its own `identity` Postgres schema/database connection
(separate from the monolith's `product` schema, though the same physical Postgres instance/database —
per-service-per-schema, see root `CLAUDE.md`'s Database Conventions), its own port (`8082`), and its
own Liquibase changelog/docker-compose file. `gateway` no longer has a Maven dependency on this
module at all — nor does `social-service`, which used to be the one module allowed to reach into it.
**`gateway`-side HTTP proxying to this service is not built yet** — until it is, this service is only
reachable directly on its own port, same limitation `ecommerce-service` has.

- `IdentityServiceApplication` — `@SpringBootApplication` entry point. `@EntityScan`/
  `@EnableJpaRepositories` explicitly point at `com.ttg.devknowledgeplatform.common.entity`/
  `common.repository` — this module owns no entities/repositories of its own, but needs
  `common.entity.User`/`common.repository.UserRepository`, and Spring Boot's default JPA scanning
  is scoped to the main class's own package tree only, which doesn't reach `common`. Don't remove
  these annotations assuming they're redundant — without them `UserRepository` never becomes a bean
  and the app fails to start (the exact bug this fixed, see `docs/CHANGELOG.md`).
- `api/` (+ `api/impl/`) — `AuthApi`/`AuthController` (just `GET /api/v1/auth/user`, the only
  endpoint Keycloak's own `/userinfo` doesn't cover — this app's avatar/local-username profile
  shape; renamed from `OAuth2Api`/`OAuth2Controller` once every other endpoint on it was deleted)
  and `UserApi`/`UserController` (`updateProfile`/`uploadAvatar` — the pure "my own profile"
  operations that only need `UserService`/`UserMapper`/`infra`'s `StorageService`). `getPublicProfile`
  and `search` live in `social-service`'s own `UserApi`/`UserController` instead (same `/api/v1/users`
  mapping, different class) — that module resolves its own base lookup via `common`'s
  `UserRepository` directly now rather than reaching into this module, since this module can no
  longer be called in-process.
- `mapper/UserMapper` — MapStruct abstract class (not a plain interface) needing an injected
  `infra`'s `StorageService` for avatar presigned-URL resolution — MapStruct interfaces can't hold
  instance fields, same pattern as `social-service`'s `FriendMapper`/`MessagingMapper` (which now
  has its own, separately-duplicated `toUserInfo` method for the same shape — see that module's
  `CLAUDE.md`).
- `dto/user/UpdateProfileRequest`, `dto/UserInfoResponse` — the only DTOs left in this module.
  Everything auth-flow-specific (`dto/auth/*`, `dto/RegisterRequest`,
  `dto/{OAuth2UserInfo,GoogleOAuth2UserInfo,FacebookOAuth2UserInfo,OAuth2UserInfoFactory}`) was
  deleted alongside the endpoints/services that used them.
- `security/` — this app's own filter chain, independent of `gateway`'s, since it now runs on its
  own port and must guard its own endpoints regardless of whether `gateway` is proxying to it
  (mirrors `ecommerce-service`'s `security/` package). `SecurityConfig` requires authentication on
  everything except `/actuator/**` — unlike `content-service`/`ecommerce-service`, this module has
  no public or admin-only surface. `KeycloakRealmRoleConverter` maps `realm_access.roles` to
  `ROLE_*` authorities, duplicated from `gateway`'s/`ecommerce-service`'s converter of the same name
  (no Maven dependency to share it through). `KeycloakJwtAuthenticationConverter` is the one
  converter in the whole reactor that still *delegates* rather than inlining the JIT-provisioning
  logic: it calls this module's own in-process `service/UserService.findOrCreateFromKeycloak`
  directly, since both live in this same standalone app — no duplication needed here.
- `security/service/UserService` (+ `impl/`) — narrowed to: `findOrCreateFromKeycloak`
  (`KeycloakUserInfo` carrier record, same package) — the JIT-provisioning entry point this module's
  own `KeycloakJwtAuthenticationConverter` calls on every authenticated request, resolving by
  `keycloakSubjectId` first, `findByEmail` as a fallback (links a pre-existing local row on its
  owner's first Keycloak login), else inserting a new row; only writes when a field actually
  changed — plus `resolveCurrentUser`/`findByEmail`/`findByUserUuid(Optional)`/`findById`/
  `updateStatus`/`updateProfile`/`updateAvatar`. Returns `common` entities, never this module's own
  DTOs.

**Deleted outright** (all superseded by Keycloak — do not resurrect any of this to "fix" a
compile error; the fix is always to route through Keycloak instead): `security/JwtTokenProvider`,
`security/jwt/{TokenClaims,AccessTokenClaims,RefreshTokenClaims}`, `security/PasswordEncoderConfig`
(nothing hashes/verifies passwords locally anymore — `User.password` is a vestigial `@NotNull`
column now set to a fixed placeholder string on JIT-created rows, never read/compared),
`security/service/{CustomOAuth2UserService,CustomOidcUserService}`,
`security/handler/OAuth2LoginSuccessHandler`, `security/service/StateTokenService`(`Impl`),
`security/service/RefreshTokenBlacklistService`(`Impl`), `service/{OtpService,EmailService}`(`Impl`)
(the whole OTP-email flow — Keycloak's own "Verify Email" required action replaces it, a click-
through link rather than a 6-digit code), every `OAuth2Api` endpoint except `getCurrentUser`, and —
as part of the standalone extraction — `service/seed/UserSeeder` (**relocated** to `gateway`, not
deleted: it only ever wrote via `common`'s `UserRepository` directly, no other dependency on this
module, and `gateway` still needs to seed its own `product.USER` for the modules still embedded
there). This module needs no seed data of its own: a seeded demo account has no matching Keycloak
identity, so this module's own `identity.USER` table only ever fills via JIT-provisioning on a real
login. The pom's leftover JJWT/Redis-for-blacklist/mail-for-OTP dependencies (never cleaned up when
the classes using them were deleted) were removed alongside the standalone extraction too.

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
- The types this module imports from `common`/`infra` rather than owning locally —
  `common.dto.PagedResponse` (unused here directly today, but the shared type other modules use for
  paged responses), `common.dto.CustomOAuth2User`, `common.enums.{UserRole,UserStatus,UserProvider}`,
  `infra.service.StorageService` — stay there since they're either genuinely cross-cutting or
  needed by other independent deployables too. Don't recreate local copies.
- **Never hardcode a schema on anything shared with other deployables** — `common.entity.User`'s
  `@Table` deliberately has no `schema` attribute; this app's own `hibernate.default_schema:
  identity` (in `application.yml`) is what resolves it to `identity.USER` at runtime. A hardcoded
  schema anywhere on that class would silently break this module's isolation (see that class's
  Javadoc and `docs/CHANGELOG.md` for the incident this rule comes from).
- **Liquibase migrations for this module's own `USER` table live in this module's own changelog
  tree now** (`database/sql/identity-service.xml` + `2026/0.0.1/*.sql`), applied via the standalone
  `identity-service-liquibase.yml` docker-compose file at the repo root — the opposite of every
  embedded feature module (which still migrate via `gateway`'s changelog tree per root `CLAUDE.md`'s
  Database Conventions). Don't move future migrations back under `gateway`'s tree; this module owns
  its own schema lifecycle now, same as `ecommerce-service`.
