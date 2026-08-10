# CLAUDE.md — gateway

Module-local guidance for `gateway` (formerly named `api` — renamed once its REST controller layer
finished moving out into the feature modules, see `docs/CHANGELOG.md`). Read alongside the root
`CLAUDE.md`. This is the module every other embedded module used to get wired up through — Spring
Boot entry point, security/JWT-filter wiring — but **it now depends on nothing but `common` and
`infra`, and has no Liquibase migrations of its own left at all** (see `database/sql/` below). `ai-service` was the sixth and final embedded feature module (following
`identity-service`/`ecommerce-service`/`task-service`/`social-service`/`content-service` out the
door); once its Maven dependency was dropped, `gateway` reached **zero embedded feature modules
remaining** — see root `CLAUDE.md`'s Long-term direction section, which explicitly calls this a
stopping point for the microservices-study exercise, not a pause partway through one.
**No Maven dependency on `identity-service`, `ecommerce-service`, `task-service`, `social-service`,
`content-service`, or `ai-service`** — all six are standalone Spring Boot applications now (see root
`CLAUDE.md`). This app used to JIT-provision its own local `User` copy (into `product.USER`) instead
of calling any of them in-process; it now persists **no caller-identity row at all** — see
`security/` below for why that stopped being useful once it lost every REST controller of its own.
Removing `task-service`'s and `social-service`'s dependencies needed no rewritten call site, unlike
`identity-service` — `gateway` never called into either module's Java classes in-process to begin
with (`ProjectApi`/`TaskApi`, `FriendApi`/`GroupApi`/`DmApi`/`UserApi` were already those modules'
own REST layers, just riding on this app's Spring context via the Maven dependency), so removing
them was mostly `pom.xml` edits — `social-service`'s removal additionally required deleting this
app's own `WebSocketConfig`/`StompAuthChannelInterceptor`/`CurrentUserIdMessageArgumentResolver`
outright, since chat was the only STOMP use case in this reactor and `social-service` now owns that
whole transport on its own port (`8084`) — **this app has no WebSocket/STOMP transport of its own
anymore.** `content-service`'s removal needed a real HTTP rewrite first, unlike the other three:
`ai-service` (still embedded at the time) had a real one-directional Maven dependency on
`content-service` (a JPA FK, a live entity parameter, and
`PublicContentApi`/`PublicContentController`), so removing it required `ai-service`'s
`ContentServiceClient` HTTP rewrite before the `pom.xml` edit was safe, plus moving
`PublicContentApi`/`PublicContentController` back into `content-service`. `ai-service`'s own
removal, by contrast, needed **no** further HTTP rewrite — by the time it was extracted, `ai-service`
already had no Maven dependency on `content-service` of its own (that was severed during
`content-service`'s extraction, not this one), so dropping `gateway`'s dependency on `ai-service` was
a straightforward `pom.xml`/config/Dockerfile cleanup, not a rewrite of any call site: `gateway`
never called into `ai-service`'s Java classes in-process either (`ChatApi`/`IngestionApi`/
`EmbeddingIndexApi`/`PipelineMetricsApi` were already `ai-service`'s own REST layer). What *did* need
moving out of `gateway` alongside the Maven dependency was infrastructure `ai-service`'s
controllers/services actually used at runtime — `sseStreamExecutor`, the Bucket4j Redis connection,
the SSE/`@CurrentUserId` MVC wiring — see "What lives here" below for exactly what moved vs. what
turned out to be dead code and was deleted outright instead.

**Retired its own local user persistence entirely, after the six extractions above.** `User`/
`UserRepository`/`UserProvider`/`UserRole`/`UserStatus` — which used to live in `common` as a
shared-kernel class both this app and `identity-service` mapped into their own separate `USER`
table — moved out of `common` into `identity-service` outright once this app's own copy was
retired, since `identity-service` became the sole remaining consumer (see root `CLAUDE.md`'s
Security section and `docs/CHANGELOG.md`'s `[Unreleased]` entry). The reasoning: this app's JWT
converter never actually needed that row back for anything — the authorization decision
(`ROLE_ADMIN` or not) was always read straight off the token's own `realm_access.roles` claim, and
this app has had zero REST controllers to read the row back for since well before this cleanup.
`identity-service`'s own `identity.USER` is now the sole system-of-record for user identity in this
whole reactor. `product.USER` itself, plus every other now-orphaned table `product` schema still
held from the six embedded-module extractions (23 tables total), was dropped outright in the same
pass, via a new `DKP-0033` changeset — and this app's entire Liquibase changelog tree (that
changeset included) was then deleted outright once nothing in it was needed anymore. See
`database/sql/` below.

Every feature's REST controllers, DTOs, and MapStruct mappers now live in the feature module that
owns the underlying entities/services (`content-service`, `ai-service`, `social-service`/
`identity-service` before their later extractions) instead of centralized here — see root
`CLAUDE.md`'s Module Structure table and `docs/PROJECT_STRUCTURE.md` for the full per-module
breakdown, and `docs/CHANGELOG.md`'s `[Unreleased]` entry for why (microservices-readiness: each
module owning its full vertical slice — entity through REST controller — instead of one module
centralizing every controller regardless of which module owns the entity). **This module holds zero
REST controllers of its own** — even the one composed `UserApi.search`/`getPublicProfile` endpoint
that used to live here moved to `social-service` (which resolves its own base lookup via its own
`SocialProfileRepository` now, rather than reaching into `identity-service` — see that module's
`CLAUDE.md`) once a one-directional sibling dependency became the better fit than an orchestration
endpoint sitting in the entry-point module, and later became a fully independent local lookup once
both `identity-service` and `social-service` were extracted into standalone services.

## What lives here

- `security/` — transport/security **edge** infra, and now the entirety of what this module does at
  runtime — no Liquibase and no entity mapped to any table left (see below; the JDBC `datasource`
  connection in `application*.yml` still exists only because `common`'s JPA starter dependency
  needs one to autoconfigure against, even with zero entities to map). `SecurityConfig` (Keycloak is the identity provider — this app is a pure
  OAuth2 **resource server** now, `.oauth2ResourceServer(jwt -> ...)` verifying bearer tokens against
  Keycloak's JWKS via `spring.security.oauth2.resourceserver.jwt.issuer-uri`; no `.oauth2Login()`/
  custom filter anymore), `KeycloakRealmRoleConverter` (maps the token's `realm_access.roles` claim
  to `ROLE_*` `GrantedAuthority`s — Spring's default converter doesn't read Keycloak's nested claim
  shape), `KeycloakJwtAuthenticationConverter` — builds the `CustomOAuth2User` principal **straight
  from the token's claims now, with zero database access** (`jwt.getSubject()` standing in for
  `userUuid`), mirroring `ecommerce-service`'s/`task-service`'s/`content-service`'s/`ai-service`'s
  converters. Used to JIT-provision/refresh this app's own local `User` row into `product.USER`
  (inlined directly via `common`'s `UserRepository`, since delegating to `identity-service`'s
  `UserService` in-process stopped being possible once that module went standalone) — retired
  outright once it became clear nothing in this app ever read that row back (see the intro above).
  `CorsConfig`, `JsonAuthenticationEntryPoint`. **No `CurrentUserResolver` here anymore** — it read
  the JIT-provisioned row back to resolve an `Integer` PK for `@CurrentUserId`, but its only two
  callers (`CurrentUserIdArgumentResolver`, `CurrentUserIdMessageArgumentResolver`) had already
  moved out to other modules in earlier extractions, leaving it with zero real callers left in this
  app — confirmed via grep before deleting it, same class of dead-code finding as
  `RedisCacheConfig`'s below. **No `WebSocketConfig`/`StompAuthChannelInterceptor` here either** —
  both moved to `social-service`'s own `security/` package (same package name there) once that
  module became a standalone app owning the whole WebSocket/STOMP transport for chat; this app never
  had a second use for it.
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
- **No `service/` package here anymore.** `service/seed/{DataSeedingRunner,UserSeeder}` — this app's
  last seeder — was deleted outright once `product.USER` itself was dropped: `UserSeeder` had
  nothing left to seed, and `DataSeedingRunner` had nothing left to orchestrate.
  `CategorySeeder`/`TagSeeder`/`QuestionAnswerSeeder` had already moved fully into `content-service`'s
  own seeding orchestration well before this, and `FriendGraphSeeder`/`UserBlockSeeder`/
  `DmThreadSeeder` into `social-service`'s, for the same reason each module's own extraction gave —
  this app could no longer import those classes at all once the Maven dependency was gone. The seed
  data this app used to own (`data/csv/users.csv`, plus the standalone `data/init-admin-user.sql`
  script that inserted directly into `product.USER`) was deleted alongside the seeder classes —
  both are unreachable now that the table they wrote to doesn't exist.
- **No `database/sql/` here anymore — this app has zero Liquibase story left at all.** Every
  changeset that ever created a `product` table (`DKP-0001`-`0004`/`0009`/`0013`/`0014`/`0018` for
  `CATEGORY`/`TAG`/`CONTENT_ITEM`/`CONTENT_ITEM_TAG`/`QUESTION_ANSWER`/`ARTICLE`;
  `DKP-0005`-`0008`/`0010`-`0012` for `CONTENT_EMBEDDING`/`CHAT_SESSION`/`CHAT_MESSAGE`/`SYS_PARAM`/
  `PIPELINE_METRICS`; `DKP-0015`/`DKP-0019` for the old friend-graph/chat tables; `DKP-0020`-`0022`
  for `PROJECT`/`TASK`; `DKP-0002`/`DKP-0016`/`DKP-0017`/`DKP-0025` for `USER` itself), plus the
  final `DKP-0033` that dropped all 23 of those tables outright in one statement (naming every
  table, `CASCADE` for the FKs between them — safe there specifically because every possible
  dependent was already inside the same drop list), was deleted wholesale rather than left as
  frozen history — nothing in this reactor needed any of it anymore: every table this tree ever
  described either has a fresh-snapshot equivalent in its owning standalone service's own changelog
  now (`content-service`'s `DKP-0031`, `ai-service`'s `DKP-0032`, `social-service`'s
  `DKP-0029`/`DKP-0030`, `task-service`'s `DKP-0028`), or — for `USER` — was retired with nothing
  replacing it here at all, since `identity-service`'s own `identity.USER` is the sole live `USER`
  table in this reactor now. `liquibase-core` was removed from `pom.xml` alongside the deletion
  (and with it, the `<build><resources>` block that used to expose `**/*.xml`/`**/*.sql` under
  `src/main/java` on the classpath — nothing left there to expose), and
  `spring.liquibase.*` was removed from both `application.yml` and `application-docker.yml`. The
  one genuinely-still-needed thing this tree used to bootstrap — the `keycloak` Postgres schema
  itself (`DKP-0024`; Keycloak's own internal migration assumes its schema already exists and fails
  otherwise) — moved to the repo root's `docker/postgres/init.sql` instead (`CREATE SCHEMA IF NOT
  EXISTS keycloak`), which already runs automatically before Postgres reports healthy, which every
  service's `depends_on` already waits on; see `docs/CHANGELOG.md`'s `[Unreleased]` entry and root
  `CLAUDE.md`'s Database Conventions section for the full reasoning. Don't add a changelog tree back
  here on the assumption this app still migrates something — if a future orchestration endpoint
  ever gives this app its own table again, that would be a fresh start, not a resurrection of this
  history.
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

**This app has no current-user resolution concern left at all.** It has zero REST controllers, so
zero `@CurrentUserId` consumers; its `KeycloakJwtAuthenticationConverter` builds a
`CustomOAuth2User` principal for Spring Security's own authorization checks (`SecurityConfig`'s
`hasRole("ADMIN")` rule, unmatched today since there's no `/api/v1/admin/**` controller here either)
and nothing else reads it back. If a future orchestration endpoint ever lands here (see "Rules
specific to this module" below), it would need to resolve the caller the same way
`task-service`/`content-service`/`ai-service` already do — read `jwt.getSubject()`/the
`CustomOAuth2User` principal's UUID directly, no database lookup — since this app has no local
`User`-shaped row to look anything up against anymore. Do not reference `UserUtils.getCurrentUser()`
either way; it doesn't exist (`common`'s `UserUtils` only has `getUserName()`/`isAuthenticated()`).

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
  `Integer ...Id`. Caught as a real bug during the chat feature build (back when this app still had
  controllers): `GroupService.addMember` correctly took a UUID for the new member, but
  `removeMember`/`changeRole` initially took a raw `Integer targetUserId` — would have leaked
  enumerable internal ids into REST URLs. Fixed to match `FriendService`'s convention before the
  controller layer was built on top. This rule applies wherever a controller lives now — a feature
  module today, since this app has none of its own.
- **No STOMP/WebSocket transport lives here anymore, and no test suite does either.** Both moved to
  `social-service` in the same extraction (own `WebSocketConfig`/`StompAuthChannelInterceptor`/
  `CurrentUserIdMessageArgumentResolver`, own `AbstractStompIntegrationTest`/
  `DmMessagingStompIntegrationTest`, own Testcontainers Postgres/MinIO/Keycloak harness — see that
  module's `CLAUDE.md`). This module currently has **no test suite of its own** — a future
  REST-only integration test for one of this app's own concerns (JWT verification) would be the
  first one; it wouldn't need the STOMP-specific Keycloak-admin-client/`persistUser()` machinery
  `social-service`'s suite carries, nor any JIT-provisioning assertions, since this app doesn't do
  that anymore either.
