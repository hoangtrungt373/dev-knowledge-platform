# CLAUDE.md — gateway

Module-local guidance for `gateway` (formerly named `api` — renamed once its REST controller layer
finished moving out into the feature modules, see `docs/CHANGELOG.md`). Read alongside the root
`CLAUDE.md`. This is the module every other module gets wired up through — Spring Boot entry point,
security/JWT-filter/STOMP transport wiring, Liquibase migrations — so it depends on `common`,
`infra`, `content-service`, `ai-service`, `social-service`, and `identity-service`.

Every feature's REST controllers, DTOs, and MapStruct mappers now live in the feature module that
owns the underlying entities/services (`content-service`, `social-service`, `ai-service`,
`identity-service`) instead of centralized here — see root `CLAUDE.md`'s Module Structure table and
`docs/PROJECT_STRUCTURE.md` for the full per-module breakdown, and `docs/CHANGELOG.md`'s
`[Unreleased]` entry for why (microservices-readiness: each module owning its full vertical slice —
entity through REST controller — instead of one module centralizing every controller regardless of
which module owns the entity). **This module holds zero REST controllers of its own** — even the one
composed `UserApi.search`/`getPublicProfile` endpoint that used to live here moved to
`social-service` (it reaches into `identity-service` for the base lookup; see that module's
`CLAUDE.md`) once a one-directional sibling dependency became the better fit than a orchestration
endpoint sitting in the entry-point module.

## What lives here

- `security/` — transport/security **edge** infra, not auth business logic (that's
  `identity-service`, see below): `SecurityConfig` (JWT + OAuth2 filter chain — injects
  `identity-service`'s `CustomOAuth2UserService`/`CustomOidcUserService`/`OAuth2LoginSuccessHandler`
  across the module boundary), `JwtAuthenticationFilter` (verifies bearer tokens via
  `identity-service`'s `JwtTokenProvider`, populates `common.dto.CustomOAuth2User` on the
  `SecurityContext`), `CorsConfig`, `JsonAuthenticationEntryPoint`, `CurrentUserResolver`,
  `WebSocketConfig`, `StompAuthChannelInterceptor` (STOMP CONNECT/SUBSCRIBE auth — see `docs/
  PROJECT_STRUCTURE.md` for the mechanics; imports `GroupMessagingController`/
  `DmMessagingController` from `social-service`'s `social.api.impl` package,
  `GroupService.isChannelMember` from `social-service`'s service layer, and `JwtTokenProvider`/
  `security.jwt.*` claim types from `identity-service`).
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
- `service/seed/DataSeedingRunner` — orchestrates every seeder in dependency order (category → tag
  → question-answer → user → friend graph → blocks); every seeder it calls lives in the feature
  module owning what it seeds (`content-service`, `identity-service`'s own `UserSeeder`,
  `social-service`) — this runner is the one place that imports across all of them, same as
  `IngestionApi` used to be the "needs 2 modules" case before `ai-service` absorbed it. The actual
  seed data files (`data/csv/*.csv`, `data/question-answers/*.md`) stay under this module's own
  `src/main/resources/` regardless of which module the seeder Java class lives in.
- `database/sql/` — Liquibase changelogs for **every** module's tables, not just this module's own —
  `content-service`'s, `social-service`'s, and `identity-service`-adjacent `USER` tables are all
  migrated from here too (see root `CLAUDE.md`'s Database Conventions). New feature modules don't
  get their own changelog folder.

## Current-user resolution

Two real patterns — do not reference `UserUtils.getCurrentUser()`, it doesn't exist:

- **`@CurrentUserId Integer userId`** controller parameter (`common.annotation.CurrentUserId`,
  resolved by `CurrentUserIdArgumentResolver` in `config/web/`) — use when you only need the numeric
  `User` PK and the endpoint requires authentication (JWT).
- **`userService.resolveCurrentUser(CustomOAuth2User principal)`** (`identity-service`'s
  `UserService`) — use when the endpoint takes `@AuthenticationPrincipal CustomOAuth2User principal`
  (`common.dto.CustomOAuth2User`) and you need the full `User` entity, not just the id.
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
  one-directional dependency on a sibling (see `social-service` → `identity-service`, mirroring
  `ai-service` → `content-service`). Only reach for putting something in this module if it genuinely
  needs two feature modules that have **no** dependency relationship possible between them in either
  direction — that's now a narrow, rare case (there is currently no REST endpoint that qualifies).
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
  `AbstractStompIntegrationTest` (Testcontainers Postgres/Redis/MinIO, boots the full context) +
  `DmMessagingStompIntegrationTest`. It has to be here, not `social-service`: `WebSocketConfig`/
  `StompAuthChannelInterceptor` only ever get assembled together with `DmMessagingController` in a
  running app in this module, since `social-service` has no `@SpringBootApplication` of its own. A
  future STOMP/REST integration test for another feature's controller belongs here too, for the
  same reason — a slice test (`@WebMvcTest`, plain Mockito) can still live in the owning feature
  module if it doesn't need the real broker/security wiring.
- **Any new public STOMP topic needs a `SUBSCRIBE`-time authorization check** in
  `StompAuthChannelInterceptor`, the same way `/topic/channels/{id}` has one via `social-service`'s
  `GroupService.isChannelMember`. The simple in-memory broker has no per-destination ACL — anyone
  who knows the topic string can subscribe unless this interceptor rejects it. Prefer
  `convertAndSendToUser`'s private per-user queue over a public topic when a destination is
  inherently 1:1 (e.g. DMs) — it needs no such check at all.
