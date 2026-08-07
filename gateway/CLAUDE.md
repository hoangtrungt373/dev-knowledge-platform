# CLAUDE.md — gateway

Module-local guidance for `gateway` (formerly named `api` — renamed once its REST controller layer
finished moving out into the feature modules, see `docs/CHANGELOG.md`). Read alongside the root
`CLAUDE.md`. This is the module every other embedded module gets wired up through — Spring Boot
entry point, security/JWT-filter wiring, Liquibase migrations — so it depends on `common`, `infra`,
`content-service`, and `ai-service`.
**No Maven dependency on `identity-service`, `ecommerce-service`, `task-service`, or
`social-service`** — all four are standalone Spring Boot applications now (see root `CLAUDE.md`);
this app JIT-provisions its own local `User` copy directly instead of calling any of them in-process
(see `security/` below). Removing `task-service`'s and `social-service`'s dependencies needed no
rewritten call site, unlike `identity-service` — `gateway` never called into either module's Java
classes in-process to begin with (`ProjectApi`/`TaskApi`, `FriendApi`/`GroupApi`/`DmApi`/`UserApi`
were already those modules' own REST layers, just riding on this app's Spring context via the Maven
dependency), so removing them was mostly `pom.xml` edits — `social-service`'s removal additionally
required deleting this app's own `WebSocketConfig`/`StompAuthChannelInterceptor`/
`CurrentUserIdMessageArgumentResolver` outright, since chat was the only STOMP use case in this
reactor and `social-service` now owns that whole transport on its own port (`8084`) — **this app has
no WebSocket/STOMP transport of its own anymore.**

Every feature's REST controllers, DTOs, and MapStruct mappers now live in the feature module that
owns the underlying entities/services (`content-service`, `ai-service`, `social-service`/
`identity-service` before their later extractions) instead of centralized here — see root
`CLAUDE.md`'s Module Structure table and `docs/PROJECT_STRUCTURE.md` for the full per-module
breakdown, and `docs/CHANGELOG.md`'s `[Unreleased]` entry for why (microservices-readiness: each
module owning its full vertical slice — entity through REST controller — instead of one module
centralizing every controller regardless of which module owns the entity). **This module holds zero
REST controllers of its own** — even the one composed `UserApi.search`/`getPublicProfile` endpoint
that used to live here moved to `social-service` (which resolves its own base lookup via its own
`SocialProfileRepository` now, rather than reaching into `identity-service` or `common`'s
`UserRepository` — see that module's `CLAUDE.md`) once a one-directional sibling dependency became
the better fit than an orchestration endpoint sitting in the entry-point module, and later became a
fully independent local lookup once both `identity-service` and `social-service` were extracted into
standalone services.

## What lives here

- `security/` — transport/security **edge** infra. `SecurityConfig` (Keycloak is the identity
  provider — this app is a pure OAuth2 **resource server** now, `.oauth2ResourceServer(jwt -> ...)`
  verifying bearer tokens against Keycloak's JWKS via
  `spring.security.oauth2.resourceserver.jwt.issuer-uri`; no `.oauth2Login()`/custom filter
  anymore), `KeycloakRealmRoleConverter` (maps the token's `realm_access.roles` claim to `ROLE_*`
  `GrantedAuthority`s — Spring's default converter doesn't read Keycloak's nested claim shape),
  `KeycloakJwtAuthenticationConverter` — the JIT-provisioning glue, **now inlined here directly**
  via `common`'s `UserRepository` (find by `keycloakSubjectId`, fallback `email`, write only if
  changed), maintaining this app's own `product.USER` row. Used to delegate to `identity-service`'s
  `UserService.findOrCreateFromKeycloak` across the module boundary; that stopped being possible
  once `identity-service` became a standalone service with no Maven dependency from this one — see
  that module's `CLAUDE.md`. Deliberately duplicated from `identity-service`'s/`ecommerce-service`'s
  equivalent logic rather than shared. Builds the `CustomOAuth2User` principal shape every remaining
  call site in this app expects. `CorsConfig`, `JsonAuthenticationEntryPoint`, `CurrentUserResolver`.
  **No `WebSocketConfig`/`StompAuthChannelInterceptor` here anymore** — both moved to
  `social-service`'s own `security/` package (same package name there) once that module became a
  standalone app owning the whole WebSocket/STOMP transport for chat; this app never had a second
  use for it.
- `config/web/` — `WebMvcConfig` (SSE async-request wiring, reads `SSE_TIMEOUT_MS` from
  `ai-service`'s `SseStreamTemplate` rather than duplicating it; deliberately registers no
  interceptors of its own anymore — `ai-service`'s own `ChatMvcConfig` registers the chat
  rate-limit interceptor via its own composed `WebMvcConfigurer` bean instead, see that module's
  `CLAUDE.md`), `CurrentUserIdArgumentResolver` (resolves `common.annotation.CurrentUserId` for
  REST controllers — the annotation itself lives in `common` so every feature module's controllers
  can use it without depending on this module). **No `CurrentUserIdMessageArgumentResolver` here
  anymore** — that STOMP-side counterpart moved to `social-service` alongside `WebSocketConfig`.
- `config/cache/RedisCacheConfig` — `@EnableCaching`, base `RedisCacheConfiguration` + per-cache TTL
  `RedisCacheManager` (reads `infra`'s `CacheTtlProperties`), dedicated Bucket4j Redis connection
  (also used by `ai-service`'s `ChatRateLimiter`, injected there by type — no import needed).
- `config/thread/` — `ThreadPoolConfig`/`ThreadPoolProperties`: `sseStreamExecutor` only (SSE/MVC
  async dispatch) — the `asyncEventExecutor` bulkhead (`@EventHandler` dispatch) moved to `infra`'s
  own `AsyncEventThreadPoolConfig`/`AsyncEventThreadPoolProperties`, since `infra`'s own event
  framework is the thing that owns that pool's purpose, not this module. Don't conflate the two
  when reasoning about thread pool exhaustion — they're deliberately separate bulkheads in
  separate modules now.
- **No `event/` package here anymore** — every listener moved into the module that owns the event
  it reacts to (`ContentPublishedEventListener` → `ai-service`, since it calls that module's own
  `ContentIndexingService`; `FriendRequestSentEventListener`/`FriendRequestAcceptedEventListener` →
  `social-service`, alongside the events themselves, before that module's own later extraction).
  Same "does this genuinely need to be at the entry-point module" question already applied to
  `config/`; none of the three ever had a `gateway`-specific dependency.
- `service/seed/{DataSeedingRunner,UserSeeder}` — `DataSeedingRunner` orchestrates every seeder
  this app still owns, in dependency order (category → tag → question-answer → user); `CategorySeeder`/
  `TagSeeder`/`QuestionAnswerSeeder` live in `content-service` (the feature module owning what they
  seed), but `UserSeeder` lives right here — relocated from `identity-service` once that module
  became a standalone service and could no longer be imported across the module boundary.
  `UserSeeder` still only writes via `common`'s `UserRepository` directly, same as before the move.
  **No longer seeds the friend graph/blocks/DM conversations** — `FriendGraphSeeder`/
  `UserBlockSeeder`/`DmThreadSeeder` moved fully into `social-service`'s own seeding orchestration
  (its own `service.seed.DataSeedingRunner`, plus its own copy of the demo-user CSV — see that
  module's `CLAUDE.md`) once that module was extracted with no Maven dependency from this one. The
  seed data files this app still owns (`data/csv/categories.csv`/`tags.csv`/`users.csv`,
  `data/question-answers/*.md`) stay under this module's own `src/main/resources/`; `data/csv/
  friend-requests.csv`/`user-blocks.csv` were deleted from here (moved to `social-service`'s own
  resources, since this app no longer reads them).
- `database/sql/` — Liquibase changelogs for this module's own tables plus every *embedded* feature
  module's — `content-service`'s and this app's own `product.USER` are migrated from here (see root
  `CLAUDE.md`'s Database Conventions). `identity-service`'s, `ecommerce-service`'s,
  `task-service`'s, and `social-service`'s tables are **not** here — each standalone service
  migrates its own schema from its own changelog tree now (see their `CLAUDE.md`s). Several
  changesets are still physically present in this tree as already-run history — frozen, not
  deleted, per this repo's never-edit-an-executed-changeset convention — but now describe tables no
  entity in this app's Spring context maps to anymore: `DKP-0020`/`DKP-0021`/`DKP-0022` (the old
  `product.PROJECT`/`product.TASK`, `OWNER_ID` a real FK to `product.USER` — `task-service` migrates
  its own fresh `task.PROJECT`/`task.TASK` snapshot instead, `DKP-0028` in that module's own tree,
  with a plain `OWNER_UUID` column replacing that FK entirely) and `DKP-0015`/`DKP-0019` (the old
  `product.FRIEND_REQUEST`/`FRIENDSHIP`/`USER_BLOCK`/`MESSAGE_GROUP`/`GROUP_MEMBER`/`CHANNEL`/
  `DM_THREAD`/`DM_MESSAGE`/`CHANNEL_MESSAGE` — `social-service` migrates its own fresh
  `social.PROFILE` + friend-graph/chat snapshot instead, `DKP-0029`/`DKP-0030` in that module's own
  tree, with every FK repointed at `social.PROFILE` instead of `product.USER`, since that module
  persists its own lean entity now rather than reusing `common.entity.User` — see
  `social-service/CLAUDE.md`). New embedded feature modules don't get their own changelog folder; a
  module extracted into a standalone service does.
- `Dockerfile` (repo root as build context) — multi-stage build producing this module's runtime
  image. `COPY`s only the sources of modules it actually still depends on (`common`/`infra`/
  `content-service`/`ai-service`) — every other module's `pom.xml` alone, needed only for Maven to
  parse the reactor's full `<modules>` list. Relies entirely on `application-docker.yml`'s existing
  env-var defaults (`DB_HOST`/`REDIS_HOST`/`MINIO_ENDPOINT` already default to the infra compose's
  service names); the new `dev-knowledge-platform-apps-docker-compose.yml` at the repo root only
  overrides the values that have no default (`KEYCLOAK_ISSUER_URI`, `FRONTEND_URL`,
  `OPENAI_API_KEY`). See root `CLAUDE.md`'s Build & Run Commands and `docs/PROJECT_STRUCTURE.md`'s
  Deployment section.

## Current-user resolution

Two real patterns — do not reference `UserUtils.getCurrentUser()`, it doesn't exist:

- **`@CurrentUserId Integer userId`** controller parameter (`common.annotation.CurrentUserId`,
  resolved by `CurrentUserIdArgumentResolver` in `config/web/`) — use when you only need the numeric
  `User` PK and the endpoint requires authentication (JWT).
- **Resolve the full `User` entity from a `CustomOAuth2User` principal directly via `common`'s
  `UserRepository.findByEmail(principal.getEmail())`** when the endpoint takes
  `@AuthenticationPrincipal CustomOAuth2User principal` (`common.dto.CustomOAuth2User`) and needs
  the full entity, not just the id. This used to be `identity-service`'s
  `UserService.resolveCurrentUser(...)`, callable in-process; that stopped being an option once
  `identity-service` became a standalone service, so every module in this reactor that needs this
  now resolves it locally (`social-service`'s own `UserController` does the same thing against its
  own `SocialProfileRepository` instead, since it no longer shares `common.entity.User` at all —
  see that module's `CLAUDE.md`).
- `common`'s `UserUtils.getUserName()` is unrelated — it's for populating audit-column string
  fields (`usrCreation`/`usrLastModification`), not for resolving "the acting user" in a controller.
- **No STOMP counterpart here anymore** — `@CurrentUserId` on `@MessageMapping` methods (e.g.
  `social-service`'s `GroupMessagingController`/`DmMessagingController`) is now resolved by
  `social-service`'s own `CurrentUserIdMessageArgumentResolver`, against that module's own
  `SocialProfile` rather than this app's `product.USER`. This app has no WebSocket/STOMP transport
  of its own to resolve a STOMP principal for.

## Rules specific to this module

- **This is the only module allowed to depend on more than one feature module — but that no longer
  means REST endpoints live here.** A new REST endpoint's controller/DTO/mapper belongs in whichever
  single feature module owns the entities it fronts, even if that means one feature module taking a
  one-directional dependency on a sibling (see `ai-service` → `content-service`). `social-service` →
  `identity-service` used to be a second example of this shape; it was removed once
  `identity-service` was extracted into a standalone service (see that module's `CLAUDE.md`) — a
  standalone service can never be the target of an in-process sibling dependency like this, only a
  future network call through this module's (not-yet-built) proxy layer. Only reach for putting
  something in this module if it genuinely needs two feature modules that have **no** dependency
  relationship possible between them in either direction — that's now a narrow, rare case (there is
  currently no REST endpoint that qualifies).
- **Business logic (validation, uniqueness checks, cascades) belongs in the owning feature module's
  service, not in a controller** — but there are no controllers in this module anymore, so this
  mainly matters as guidance for whoever's tempted to add one here instead of in a feature module.
- SSE streaming runs on `sseStreamExecutor` (10 core / 50 max / queue 100, configured here); event
  listener dispatch runs on the separate `asyncEventExecutor` (`infra`) — don't conflate the two
  when reasoning about thread pool exhaustion.
- **Never expose a raw integer PK for any user other than the authenticated caller** — path
  variables/service params referencing "the other user" take a UUID (`String ...Uuid`), never an
  `Integer ...Id`. The authenticated caller's own id is fine as a raw `Integer` (`@CurrentUserId`,
  never in the URL). Caught as a real bug during the chat feature build (back when it still lived
  here): `GroupService.addMember` correctly took a UUID for the new member, but
  `removeMember`/`changeRole` initially took a raw `Integer targetUserId` — would have leaked
  enumerable internal ids into REST URLs. Fixed to match `FriendService`'s convention before the
  controller layer was built on top. This rule applies wherever a controller lives now, this module
  or any feature module.
- **No STOMP/WebSocket transport lives here anymore, and no test suite does either.** Both moved to
  `social-service` in the same extraction (own `WebSocketConfig`/`StompAuthChannelInterceptor`/
  `CurrentUserIdMessageArgumentResolver`, own `AbstractStompIntegrationTest`/
  `DmMessagingStompIntegrationTest`, own Testcontainers Postgres/MinIO/Keycloak harness — see that
  module's `CLAUDE.md`). This module currently has **no test suite of its own** — a future
  REST-only integration test for one of this app's own concerns (JWT verification, JIT-provisioning)
  would be the first one; it wouldn't need the STOMP-specific Keycloak-admin-client/`persistUser()`
  machinery `social-service`'s suite carries.

