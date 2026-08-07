# CLAUDE.md — gateway

Module-local guidance for `gateway` (formerly named `api` — renamed once its REST controller layer
finished moving out into the feature modules, see `docs/CHANGELOG.md`). Read alongside the root
`CLAUDE.md`. This is the module every other embedded module used to get wired up through — Spring
Boot entry point, security/JWT-filter wiring, Liquibase migrations — but **it now depends on nothing
but `common` and `infra`.** `ai-service` was the sixth and final embedded feature module (following
`identity-service`/`ecommerce-service`/`task-service`/`social-service`/`content-service` out the
door); once its Maven dependency was dropped, `gateway` reached **zero embedded feature modules
remaining** — see root `CLAUDE.md`'s Long-term direction section, which explicitly calls this a
stopping point for the microservices-study exercise, not a pause partway through one.
**No Maven dependency on `identity-service`, `ecommerce-service`, `task-service`, `social-service`,
`content-service`, or `ai-service`** — all six are standalone Spring Boot applications now (see root
`CLAUDE.md`); this app JIT-provisions its own local `User` copy directly instead of calling any of
them in-process (see `security/` below). Removing `task-service`'s and `social-service`'s
dependencies needed no rewritten call site, unlike `identity-service` — `gateway` never called into
either module's Java classes in-process to begin with (`ProjectApi`/`TaskApi`,
`FriendApi`/`GroupApi`/`DmApi`/`UserApi` were already those modules' own REST layers, just riding on
this app's Spring context via the Maven dependency), so removing them was mostly `pom.xml` edits —
`social-service`'s removal additionally required deleting this app's own `WebSocketConfig`/
`StompAuthChannelInterceptor`/`CurrentUserIdMessageArgumentResolver` outright, since chat was the
only STOMP use case in this reactor and `social-service` now owns that whole transport on its own
port (`8084`) — **this app has no WebSocket/STOMP transport of its own anymore.**
`content-service`'s removal needed a real HTTP rewrite first, unlike the other three: `ai-service`
(still embedded at the time) had a real one-directional Maven dependency on `content-service` (a JPA
FK, a live entity parameter, and `PublicContentApi`/`PublicContentController`), so removing it
required `ai-service`'s `ContentServiceClient` HTTP rewrite before the `pom.xml` edit was safe, plus
moving `PublicContentApi`/`PublicContentController` back into `content-service` and narrowing this
app's own `DataSeedingRunner` to `UserSeeder` only (see that class's Javadoc and
`content-service/CLAUDE.md`). `ai-service`'s own removal, by contrast, needed **no** further HTTP
rewrite — by the time it was extracted, `ai-service` already had no Maven dependency on
`content-service` of its own (that was severed during `content-service`'s extraction, not this one),
so dropping `gateway`'s dependency on `ai-service` was a straightforward `pom.xml`/config/Dockerfile
cleanup, not a rewrite of any call site: `gateway` never called into `ai-service`'s Java classes
in-process either (`ChatApi`/`IngestionApi`/`EmbeddingIndexApi`/`PipelineMetricsApi` were already
`ai-service`'s own REST layer). What *did* need moving out of `gateway` alongside the Maven
dependency was infrastructure `ai-service`'s controllers/services actually used at runtime —
`sseStreamExecutor`, the Bucket4j Redis connection, the SSE/`@CurrentUserId` MVC wiring — see "What
lives here" below for exactly what moved vs. what turned out to be dead code and was deleted
outright instead.

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
- **`config/` now holds only `JacksonConfig`** (shared `ObjectMapper` customization) — every other
  class that used to live under `config/` was either relocated to `ai-service` (the module that
  actually consumed it) or deleted outright as dead code once `ai-service` went standalone:
  - **No `config/web/` here anymore.** `WebMvcConfig` (SSE async-request wiring) and
    `CurrentUserIdArgumentResolver` (resolved `common.annotation.CurrentUserId` for REST
    controllers) both moved to `ai-service`'s own `config/web/` — the only module in this reactor
    with a REST controller left to resolve `@CurrentUserId` for, or an SSE endpoint to configure
    async-request timeouts for. `ai-service`'s own `ChatMvcConfig` now registers **both**
    `ChatRateLimitInterceptor` and its ported `CurrentUserIdArgumentResolver` via its own composed
    `WebMvcConfigurer` bean — see `ai-service/CLAUDE.md`. **No `CurrentUserIdMessageArgumentResolver`
    here either** — that STOMP-side counterpart moved to `social-service` alongside `WebSocketConfig`
    in an earlier extraction.
  - **No `config/cache/RedisCacheConfig` here anymore, and it was *not* moved to `ai-service`
    either — it was deleted outright, because half of it was dead code.** While extracting
    `ai-service`, a reactor-wide grep for `@Cacheable`/`@CacheEvict`/`@CachePut` came back with
    **zero real usages anywhere in the whole codebase**. `RedisCacheConfig`'s `@EnableCaching` +
    `cacheManager`/`baseRedisCacheConfiguration` beans (backed by `infra`'s now-deleted
    `CacheTtlProperties`/`CacheNames`) had been fully wired up and shipped but never actually
    consumed by a single Spring-managed cache annotation — a scaffold that looked load-bearing but
    wasn't, same class of finding as `ai-service`'s own `SearchDocument` dead-entity finding from the
    `content-service` extraction. Only the one real consumer, the dedicated Bucket4j Redis
    connection (`ChatRateLimiter`'s per-user rate-limit buckets), moved — into `ai-service`'s own new
    `config/RedisConfig`, as its sole bean. If a real `@Cacheable` use case ever shows up in this
    reactor, build `@EnableCaching`/a `RedisCacheManager` from scratch wherever it's actually needed;
    don't assume the deleted classes are worth resurrecting as-is.
  - **No `config/thread/` here anymore.** `ThreadPoolConfig`/`ThreadPoolProperties` (`sseStreamExecutor`
    — SSE/MVC async dispatch, 10 core / 50 max / queue 100) moved to `ai-service` **verbatim** — the
    only module left with an SSE endpoint or MVC async dispatch to feed. This app has no thread pool
    configuration of its own left at all; the separate `asyncEventExecutor` bulkhead (`@EventHandler`
    dispatch) already lived in `infra`'s own `AsyncEventThreadPoolConfig`/
    `AsyncEventThreadPoolProperties` before this extraction and is unaffected.
- **No `event/` package here anymore** — every listener moved into the module that owns the event
  it reacts to (`FriendRequestSentEventListener`/`FriendRequestAcceptedEventListener` →
  `social-service`, alongside the events themselves, before that module's own later extraction).
  `ContentPublishedEventListener` also moved to `ai-service` at the same time as those two, on the
  same reasoning — but was later deleted outright as dead code during `content-service`'s own
  extraction (step 5): the event it listened for never had a publisher wired up, and an in-process
  Spring event can't cross a service boundary once `content-service` and `ai-service` are separate
  deployables anyway. Same "does this genuinely need to be at the entry-point module" question
  already applied to `config/`; none of these ever had a `gateway`-specific dependency.
- `service/seed/{DataSeedingRunner,UserSeeder}` — `DataSeedingRunner` now only orchestrates
  `UserSeeder`. `CategorySeeder`/`TagSeeder`/`QuestionAnswerSeeder` used to be injected here too,
  but moved fully into `content-service`'s own seeding orchestration (its own
  `service.seed.DataSeedingRunner`) once that module was extracted with no Maven dependency from
  this one — this app can no longer import those classes at all. `UserSeeder` lives right here —
  relocated from `identity-service` once that module became a standalone service and could no
  longer be imported across the module boundary. `UserSeeder` still only writes via `common`'s
  `UserRepository` directly, same as before the move. **No longer seeds the friend graph/blocks/DM
  conversations either** — `FriendGraphSeeder`/`UserBlockSeeder`/`DmThreadSeeder` moved fully into
  `social-service`'s own seeding orchestration (its own `service.seed.DataSeedingRunner`, plus its
  own copy of the demo-user CSV — see that module's `CLAUDE.md`) once that module was extracted
  with no Maven dependency from this one. The seed data files this app still owns
  (`data/csv/users.csv`) stay under this module's own `src/main/resources/`; `data/csv/
  categories.csv`/`tags.csv`, `data/question-answers/*.md`, `data/csv/friend-requests.csv`/
  `user-blocks.csv` were all deleted from here (moved to `content-service`'s/`social-service`'s own
  resources respectively, since this app no longer reads any of them).
- `database/sql/` — Liquibase changelogs for this app's own `product.USER` table only now (see root
  `CLAUDE.md`'s Database Conventions), plus every embedded feature module's tables from before each
  one was extracted, all now frozen history. `identity-service`'s, `ecommerce-service`'s,
  `task-service`'s, `social-service`'s, `content-service`'s, and `ai-service`'s tables are **not**
  here — each standalone service migrates its own schema from its own changelog tree now (see their
  `CLAUDE.md`s). Several changesets are still physically present in this tree as already-run
  history — frozen, not deleted, per this repo's never-edit-an-executed-changeset convention — but
  now describe tables no entity in this app's Spring context maps to anymore: `DKP-0020`/`DKP-0021`/
  `DKP-0022` (the old `product.PROJECT`/`product.TASK`, `OWNER_ID` a real FK to `product.USER` —
  `task-service` migrates its own fresh `task.PROJECT`/`task.TASK` snapshot instead, `DKP-0028` in
  that module's own tree, with a plain `OWNER_UUID` column replacing that FK entirely);
  `DKP-0015`/`DKP-0019` (the old `product.FRIEND_REQUEST`/`FRIENDSHIP`/`USER_BLOCK`/
  `MESSAGE_GROUP`/`GROUP_MEMBER`/`CHANNEL`/`DM_THREAD`/`DM_MESSAGE`/`CHANNEL_MESSAGE` —
  `social-service` migrates its own fresh `social.PROFILE` + friend-graph/chat snapshot instead,
  `DKP-0029`/`DKP-0030` in that module's own tree, with every FK repointed at `social.PROFILE`
  instead of `product.USER`, since that module persists its own lean entity now rather than reusing
  `common.entity.User` — see `social-service/CLAUDE.md`); `DKP-0001`-`0004`/`0009`/`0013`/`0014`/
  `0018` (the old `product.CATEGORY`/`TAG`/`CONTENT_ITEM`/`CONTENT_ITEM_TAG`/`QUESTION_ANSWER`/
  `ARTICLE`, `AUTHOR_ID` a plain unindexed-by-FK column — `content-service` migrates its own fresh
  snapshot of the same final shape instead, `DKP-0031` in that module's own tree, with `AUTHOR_UUID`
  (a plain Keycloak-subject-id column) replacing `AUTHOR_ID` entirely — see
  `content-service/CLAUDE.md`); and now, from this module's own extraction, `DKP-0005`/`DKP-0006`/
  `DKP-0007`/`DKP-0008`/`DKP-0010`/`DKP-0011`/`DKP-0012` (the old
  `product.CONTENT_EMBEDDING`/`CHAT_SESSION`/`CHAT_MESSAGE`/`SYS_PARAM`/`PIPELINE_METRICS` —
  `ai-service` migrates its own fresh snapshot of the final shape instead, `DKP-0032` in that
  module's own tree, with `CHAT_SESSION.USER_UUID`/`PIPELINE_METRICS.USER_UUID` (plain
  Keycloak-subject-id columns) replacing `USER_ID` entirely — see `ai-service/CLAUDE.md`). New
  embedded feature modules don't get their own changelog folder; a module extracted into a
  standalone service does — but there are no more embedded feature modules left in this reactor to
  extract, so this tree's job going forward is purely `product.USER` plus this now-closed set of
  frozen historical changesets.
- `Dockerfile` (repo root as build context) — multi-stage build producing this module's runtime
  image. `COPY`s only the sources of modules it actually still depends on (`common`/`infra`) —
  every other module's `pom.xml` alone, needed only for Maven to parse the reactor's full
  `<modules>` list. This app's own `Dockerfile` no longer copies `ai-service`'s sources either, now
  that its Maven dependency is gone — the same treatment every other departed module's `COPY` line
  already got. Relies entirely on `application-docker.yml`'s existing env-var defaults
  (`DB_HOST`/`MINIO_ENDPOINT` already default to the infra compose's service names); the
  `dev-knowledge-platform-apps-docker-compose.yml` at the repo root only overrides the values that
  have no default (`KEYCLOAK_ISSUER_URI`, `FRONTEND_URL`). This app's own container block in that
  compose file no longer needs `OPENAI_API_KEY`/`CONTENT_SERVICE_BASE_URL`/`INTERNAL_API_KEY` (all
  three were only ever read by `ai-service`'s beans while it was still embedded here) or a
  `redis`/`content-service` `depends_on` — just `SPRING_PROFILES_ACTIVE`/`KEYCLOAK_ISSUER_URI`/
  `FRONTEND_URL`, depending on `dkp-liquibase`+`minio`. See root `CLAUDE.md`'s Build & Run Commands
  and `docs/PROJECT_STRUCTURE.md`'s Deployment section.

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

- **This module is nominally still "the only one allowed to depend on more than one feature
  module" — but that's now doubly moot, since there are zero embedded feature modules left to
  depend on at all.** A new REST endpoint's controller/DTO/mapper belongs in whichever single
  standalone service owns the entities it fronts, even if that means one service taking a
  one-directional Maven dependency on a sibling that's also still embedded (there are none of those
  left either) — or, for two already-standalone services, a real HTTP call, never a resurrected
  Maven dependency. `ai-service` → `content-service` used to be the standing example of this shape
  (real FK coupling via `ContentEmbedding`/`ContentItem`), but it was removed during
  `content-service`'s own extraction — `ai-service`'s indexing pipeline called `content-service`'s
  internal API over HTTP instead well before `ai-service` itself went standalone, so the two were
  already parallel siblings with no Maven dependency relationship at all (see root `CLAUDE.md`'s
  Module Structure section) by the time this module's own extraction happened. `social-service` →
  `identity-service` used to be a second example of this shape; it was removed once
  `identity-service` was extracted into a standalone service (see that module's `CLAUDE.md`) — a
  standalone service can never be the target of an in-process sibling dependency like this, only a
  future network call through this module's (not-yet-built) proxy layer. A future orchestration
  endpoint that genuinely needs two standalone services with **no** dependency relationship possible
  between them in either direction is the one case that would still land here — none exists yet, and
  it would need a real HTTP call to each service regardless, not a Maven dependency on either.
- **Business logic (validation, uniqueness checks, cascades) belongs in the owning feature module's
  service, not in a controller** — but there are no controllers in this module anymore, so this
  mainly matters as guidance for whoever's tempted to add one here instead of in a feature module.
- SSE streaming runs on `sseStreamExecutor` (10 core / 50 max / queue 100) — **no longer configured
  here**, moved to `ai-service`'s own `config/thread/` as part of this module's extraction, since
  this app has no SSE endpoint left to feed it; event listener dispatch runs on the separate
  `asyncEventExecutor` (`infra`) — don't conflate the two when reasoning about thread pool
  exhaustion.
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

