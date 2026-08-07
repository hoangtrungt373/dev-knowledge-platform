# CLAUDE.md — gateway

Module-local guidance for `gateway` (formerly named `api` — renamed once its REST controller layer
finished moving out into the feature modules, see `docs/CHANGELOG.md`). Read alongside the root
`CLAUDE.md`. This is the module every other embedded module gets wired up through — Spring Boot
entry point, security/JWT-filter/STOMP transport wiring, Liquibase migrations — so it depends on
`common`, `infra`, `content-service`, `ai-service`, and `social-service`.
**No Maven dependency on `identity-service`, `ecommerce-service`, or `task-service`** — all three
are standalone Spring Boot applications now (see root `CLAUDE.md`); this app JIT-provisions its own
local `User` copy directly instead of calling any of them in-process (see `security/` below).
`task-service` never actually needed a rewritten call site when its dependency was dropped, unlike
`identity-service` — `gateway` never called into its Java classes in-process to begin with
(`ProjectApi`/`TaskApi` were already that module's own REST layer, just riding on this app's Spring
context via the Maven dependency), so removing it was a pure `pom.xml` edit.

Every feature's REST controllers, DTOs, and MapStruct mappers now live in the feature module that
owns the underlying entities/services (`content-service`, `social-service`, `ai-service`,
`identity-service` before its later extraction) instead of centralized here — see root
`CLAUDE.md`'s Module Structure table and `docs/PROJECT_STRUCTURE.md` for the full per-module
breakdown, and `docs/CHANGELOG.md`'s `[Unreleased]` entry for why (microservices-readiness: each
module owning its full vertical slice — entity through REST controller — instead of one module
centralizing every controller regardless of which module owns the entity). **This module holds zero
REST controllers of its own** — even the one composed `UserApi.search`/`getPublicProfile` endpoint
that used to live here moved to `social-service` (which resolves its own base lookup via `common`'s
`UserRepository` now, rather than reaching into `identity-service` — see that module's `CLAUDE.md`)
once a one-directional sibling dependency became the better fit than an orchestration endpoint
sitting in the entry-point module, and later became a plain local lookup once `identity-service`
was extracted into a standalone service.

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
  equivalent logic rather than shared. Builds the same `CustomOAuth2User` principal shape every call
  site already expects — shared by both the REST filter chain and STOMP `CONNECT`, exactly one
  JIT-provisioning code path. `CorsConfig`, `JsonAuthenticationEntryPoint`, `CurrentUserResolver`,
  `WebSocketConfig`,
  `StompAuthChannelInterceptor` (STOMP CONNECT/SUBSCRIBE auth — see `docs/PROJECT_STRUCTURE.md` for
  the mechanics; decodes the bearer token via an injected `JwtDecoder` — Spring Boot's
  resource-server auto-config, no manual key handling — then reuses
  `KeycloakJwtAuthenticationConverter.convert(jwt)`; imports `GroupMessagingController`/
  `DmMessagingController` from `social-service`'s `social.api.impl` package and
  `GroupService.isChannelMember` from `social-service`'s service layer).
- `config/web/` — `WebMvcConfig` (SSE async-request wiring, reads `SSE_TIMEOUT_MS` from
  `ai-service`'s `SseStreamTemplate` rather than duplicating it; deliberately registers no
  interceptors of its own anymore — `ai-service`'s own `ChatMvcConfig` registers the chat
  rate-limit interceptor via its own composed `WebMvcConfigurer` bean instead, see that module's
  `CLAUDE.md`), `CurrentUserIdArgumentResolver`/`CurrentUserIdMessageArgumentResolver` (resolve
  `common.annotation.CurrentUserId` for REST/STOMP respectively — the annotation itself lives in
  `common` so every feature module's controllers can use it without depending on this module).
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
  `social-service`, alongside the events themselves). Same "does this genuinely need to be at the
  entry-point module" question already applied to `config/`; none of the three ever had a
  `gateway`-specific dependency.
- `service/seed/{DataSeedingRunner,UserSeeder}` — `DataSeedingRunner` orchestrates every seeder in
  dependency order (category → tag → question-answer → user → friend graph → blocks); most seeders
  it calls live in the feature module owning what they seed (`content-service`, `social-service`),
  but `UserSeeder` lives right here now — relocated from `identity-service` once that module became
  a standalone service and could no longer be imported across the module boundary. `UserSeeder`
  still only writes via `common`'s `UserRepository` directly, same as before the move. The actual
  seed data files (`data/csv/*.csv`, `data/question-answers/*.md`) stay under this module's own
  `src/main/resources/` regardless of which module the seeder Java class lives in.
- `database/sql/` — Liquibase changelogs for this module's own tables plus every *embedded* feature
  module's — `content-service`'s, `social-service`'s, and this app's own `product.USER` are all
  migrated from here (see root `CLAUDE.md`'s Database Conventions). `identity-service`'s,
  `ecommerce-service`'s, and `task-service`'s tables are **not** here — each standalone service
  migrates its own schema from its own changelog tree now (see their `CLAUDE.md`s). `DKP-0020`/
  `DKP-0021`/`DKP-0022` (the old `product.PROJECT`/`product.TASK` tables, `OWNER_ID` as a real FK to
  `product.USER`) are still physically present in this tree as already-run history — frozen, not
  deleted, per this repo's never-edit-an-executed-changeset convention — but describe an orphaned
  pair no entity in this app's Spring context maps to anymore, now that `task-service` migrates its
  own fresh `task.PROJECT`/`task.TASK` snapshot instead (`DKP-0028` in that module's own tree, with
  a plain `OWNER_UUID` column replacing that FK entirely — see `task-service/CLAUDE.md`). New
  embedded feature modules don't get their own changelog folder; a module extracted into a
  standalone service does.
- `Dockerfile` (repo root as build context) — multi-stage build producing this module's runtime
  image. Relies entirely on `application-docker.yml`'s existing env-var defaults
  (`DB_HOST`/`REDIS_HOST`/`MINIO_ENDPOINT` already default to the infra compose's service names);
  the new `dev-knowledge-platform-apps-docker-compose.yml` at the repo root only overrides the
  values that have no default (`KEYCLOAK_ISSUER_URI`, `FRONTEND_URL`, `OPENAI_API_KEY`). See root
  `CLAUDE.md`'s Build & Run Commands and `docs/PROJECT_STRUCTURE.md`'s Deployment section.

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
  now resolves it locally the same way `social-service`'s `UserController` does (see that module's
  `CLAUDE.md`).
- `common`'s `UserUtils.getUserName()` is unrelated — it's for populating audit-column string
  fields (`usrCreation`/`usrLastModification`), not for resolving "the acting user" in a controller.
- **STOMP counterpart:** `@CurrentUserId Integer userId` also works on `@MessageMapping` methods
  (e.g. `social-service`'s `GroupMessagingController`/`DmMessagingController`), resolved by
  `CurrentUserIdMessageArgumentResolver` from the STOMP session's `Principal` (set once at
  `CONNECT` by `StompAuthChannelInterceptor`, not per-message) — same annotation, same
  `CustomOAuth2User` shape, different resolver interface (Spring Messaging's, not Spring MVC's).

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
  never in the URL). Caught as a real bug during the chat feature build: `GroupService.addMember`
  correctly took a UUID for the new member, but `removeMember`/`changeRole` initially took a raw
  `Integer targetUserId` — would have leaked enumerable internal ids into REST URLs. Fixed to match
  `FriendService`'s convention before the controller layer was built on top. This rule applies
  wherever a controller lives now, this module or any feature module.
- **STOMP handling cannot rely on Open-Session-In-View.** REST controllers can safely map a
  service's returned entity's lazy associations after the service call returns, because Spring
  Boot's default `spring.jpa.open-in-view=true` (never overridden here) keeps the Hibernate session
  open for the whole HTTP request via a servlet filter. STOMP message handling never goes through
  that filter, so the same pattern in a `@MessageMapping` method risks a
  `LazyInitializationException`. Caught while building `social-service`'s `DmMessagingController`:
  don't navigate `message.getDmThread().getUser1()/getUser2()` there (existing-thread case makes
  those genuine lazy proxies) — resolve what you need via a fresh repository call or from data
  already fully loaded within the same request instead. If this constraint is ever lifted (e.g.
  `open-in-view` gets explicitly disabled for the whole app), every module's REST list endpoints
  (`listMessages`, `listMyThreads`, etc.) would need the same treatment — they currently only work
  because OSIV papers over it.
- **This is the only module with real tests today** (`src/test/java/.../ws/`) —
  `AbstractStompIntegrationTest` (Testcontainers Postgres/Redis/MinIO/**Keycloak** — the last via
  `com.github.dasniko:testcontainers-keycloak` importing a dedicated, minimal test realm,
  `src/test/resources/keycloak/test-realm-export.json`, separate from the dev realm export — boots
  the full context) + `DmMessagingStompIntegrationTest`. It has to be here, not `social-service`:
  `WebSocketConfig`/`StompAuthChannelInterceptor` only ever get assembled together with
  `DmMessagingController` in a running app in this module, since `social-service` has no
  `@SpringBootApplication` of its own. `persistUser()` provisions a matching Keycloak user per call
  via the admin client (`KeycloakContainer.getKeycloakAdminClient()`), linked by
  `keycloakSubjectId`, so tests exercise `KeycloakJwtAuthenticationConverter`'s realistic find-path;
  `accessTokenFor`/`refreshTokenFor` fetch real tokens via a Resource Owner Password grant against a
  test-only client (the real `gui` client disables that grant — never reuse this client shape
  outside tests). A future STOMP/REST integration test for another feature's controller belongs
  here too, for the same reason — a slice test (`@WebMvcTest`, plain Mockito) can still live in the
  owning feature module if it doesn't need the real broker/security wiring.
- **Any new public STOMP topic needs a `SUBSCRIBE`-time authorization check** in
  `StompAuthChannelInterceptor`, the same way `/topic/channels/{id}` has one via `social-service`'s
  `GroupService.isChannelMember`. The simple in-memory broker has no per-destination ACL — anyone
  who knows the topic string can subscribe unless this interceptor rejects it. Prefer
  `convertAndSendToUser`'s private per-user queue over a public topic when a destination is
  inherently 1:1 (e.g. DMs) — it needs no such check at all.
