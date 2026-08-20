# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

This file covers project-wide, slow-changing rules only. Each Maven module has its own `CLAUDE.md`
with that module's local conventions and constraints (dependency direction, what does/doesn't
belong there, module-specific config) — Claude Code auto-loads the `CLAUDE.md` for whatever
directory you're actually working in, layered on top of this one. **Read the relevant module's
`CLAUDE.md` before making changes there; don't assume this file alone has the full picture.**

## Project Overview

**Dev Knowledge Platform** — a RAG-powered knowledge management system for developers. Users can manage articles, tags, categories, and question-and-answer content via a REST API backed by a PostgreSQL pgvector store, plus a friend graph (search, requests, blocking). A streaming chat endpoint answers questions using semantic search + LLM generation.

**Tech stack:** Java 21, Spring Boot 3, Maven multi-module, PostgreSQL + pgvector, Redis, MinIO, LangChain4j, React 18 + TypeScript + MUI.

## Module Structure

| Module | Purpose | Details |
|---|---|---|
| `common` | Shared entities (`AbstractEntity`), enums, exceptions, base DTOs, `@CurrentUserId` | [`common/CLAUDE.md`](common/CLAUDE.md) |
| `infra` | Shared Spring infra: async event framework, `SlugService`, `StorageService` (MinIO) | [`infra/CLAUDE.md`](infra/CLAUDE.md) |
| `content-service` | Categories, tags, content items (Q&A, articles) — the RAG corpus; own REST/DTO/mapper layer. **Standalone Spring Boot app, own `content` schema, own port (8085)** — not a Maven dependency of `gateway` (see below) | [`content-service/CLAUDE.md`](content-service/CLAUDE.md) |
| `ai-service` | RAG pipeline (embedding, vector search, LLM generation via LangChain4j), chat + content-indexing REST layer. **Standalone Spring Boot app, own `ai` schema, own port (8086)** — not a Maven dependency of `gateway` (see below) | [`ai-service/CLAUDE.md`](ai-service/CLAUDE.md) |
| `identity-service` | Keycloak JIT-provisioning/profile mutation for its own local `User` copy. **Standalone Spring Boot app, own `identity` schema, own port (8082)** — not a Maven dependency of `gateway` (see below) | [`identity-service/CLAUDE.md`](identity-service/CLAUDE.md) |
| `ecommerce-service` | Study-project e-commerce vertical slice: catalog, cart/checkout, orders/inventory, payments, reviews/recommendations. **Standalone Spring Boot app, own schema** — not a Maven dependency of `gateway` (see below) | [`ecommerce-service/CLAUDE.md`](ecommerce-service/CLAUDE.md) |
| `task-service` | Personal task/project management. **Standalone Spring Boot app, own `task` schema, own port (8083)** — not a Maven dependency of `gateway` (see below) | [`task-service/CLAUDE.md`](task-service/CLAUDE.md) |
| `social-service` | Friend graph + chat (groups/channels, DMs), incl. its own WebSocket/STOMP transport. **Standalone Spring Boot app, own `social` schema, own port (8084)** — not a Maven dependency of `gateway` (see below) | [`social-service/CLAUDE.md`](social-service/CLAUDE.md) |
| `gateway` | Security/JWT-filter wiring, HTTP routing to all six standalone services (Spring Cloud Gateway Server MVC), Spring Boot entry point. **Zero embedded feature modules, zero REST controllers, zero Liquibase story of its own** — a future cross-module REST orchestration endpoint is the only thing that would still land here | [`gateway/CLAUDE.md`](gateway/CLAUDE.md) |
| `gui` | React 18 + TypeScript + MUI frontend (Vite) | [`gui/CLAUDE.md`](gui/CLAUDE.md) |

`gateway` now depends only on `common`+`infra` — **zero embedded feature modules remain.** This file
used to describe a chain ending in `gateway` → `ai-service` (and, before that, `common` ← `infra` ←
`content-service` ← `ai-service`); both are now history. `ai-service` was the sixth and final module
extracted into a standalone Spring Boot application, following the exact same precedent as
`ecommerce-service`/`identity-service`/`task-service`/`social-service`/`content-service` — see the
Long-term direction paragraph below and `docs/CHANGELOG.md`'s `[Unreleased]` entries for the full
extraction. `ai-service` itself carries no Maven dependency on `content-service` (removed during
`content-service`'s own extraction: `ContentEmbedding`/`SearchDocument` carry a plain
`contentItemId` column instead of a JPA association, `ai-service`'s indexing pipeline calls a
`ContentServiceClient` (HTTP, against `content-service`'s own `/internal/content-items/**` API)
instead, and `PublicContentApi`/`PublicContentController` moved back into `content-service`
outright). `social-service` used to have its own one-directional dependency on `identity-service`
(`UserApi`'s relationship-enriched profile/search endpoints) before that module was extracted into a
standalone service; `task-service` used to have its own one-directional dependency on
`content-service` (`Task`'s optional `ContentItem` link, removed as unused) before it too was
extracted — see `docs/CHANGELOG.md`'s `[Unreleased]` entries for both removals and both modules' own
extractions. The "only module allowed to depend on more than one feature module" rule is now moot
in practice, more so than ever — there are zero embedded feature modules left, so there is nothing
for `gateway` to depend on more than one of, until/unless a future orchestration endpoint genuinely
needs two standalone services with no dependency relationship between them (which would need a real
network call, not a Maven dependency — see each module's own `CLAUDE.md`). `gui` is independent
(talks to `gateway` over HTTP only). Full detail, including the full rationale for each module
owning its own REST/DTO/mapper layer instead of centralizing them in one module, lives in
`docs/PROJECT_STRUCTURE.md`.

**`ecommerce-service`, `identity-service`, `task-service`, `social-service`, `content-service`, and
`ai-service` are not part of this dependency graph** — all six are deliberately standalone Spring
Boot applications (own `@SpringBootApplication` entry point, own Postgres schema, own JWT
verification/resource-server config, own port), extracted one at a time as a microservices-study
exercise (see the `project-ecommerce-service-module` and `project-microservices-extraction-plan`
memories for the full history of each). All six still compile against `common`+`infra` as ordinary
Maven library dependencies (shared-kernel style — no runtime call to anything), but `gateway` no
longer depends on any of them in Maven at all; all seven run as separate processes on separate ports
(`gateway` 8080, `ecommerce-service` 8081, `identity-service` 8082, `task-service` 8083,
`social-service` 8084, `content-service` 8085, `ai-service` 8086), each with its own Dockerfile and
`docker-compose.apps.yml` entry now. `ai-service`'s own `ContentServiceClient`
HTTP call to `content-service` is one real inter-service call that exists today; `gateway` itself now
proxies external client traffic to all six over HTTP too, via Spring Cloud Gateway Server MVC
(`gateway/routing/GatewayRoutesConfig` — see that module's `CLAUDE.md` and root `CLAUDE.md`'s
Architecture section for the full routing table). Routing is the only piece of "single entry point"
built so far — CORS consolidation, rate limiting, and the other cross-cutting concerns discussed
when this work started are still individual per-service today, not yet centralized at the edge.
**`identity-service` is now the sole deployable that persists a
`User` row at all** — its own `User` entity, moved there outright from `common` once `gateway`
dropped its own local copy (see `docs/CHANGELOG.md`'s `[Unreleased]` entry) — into `identity.USER`.
`gateway` used to JIT-provision an independent local copy of the same shape into `product.USER`
(`common.entity.User` back when it was a shared-kernel class both apps mapped), but that table —
along with every other now-orphaned table `product` still held from the six embedded-module
extractions — was dropped outright, since `gateway` never actually read that row back for anything
(zero REST controllers of its own, and the authorization decision was always driven by the JWT's
own claims, never the row — see the Security section below). `ecommerce-service` and `task-service`
both deliberately do **not** persist any local copy at all, for two different concrete reasons that
land on the same "Option C" shape (see the `project-microservices-extraction-plan` memory's
"Option C" decision): `ecommerce-service` has no entity with a foreign key onto a user at all, so
the only thing it ever needed from Keycloak was "who is the caller, and are they an admin";
`task-service`'s `Project`/`Task` entities *do* reference an owner, but only ever to answer "is this
row's owner the caller" — a plain `ownerUuid` column compared against the verified JWT's own `sub`
claim answers that with zero DB access. `content-service` landed on the exact same shape as
`task-service` for the exact same reason: `ContentItem.authorUuid` is a plain column (not a foreign
key), stamped once at creation and never read back or joined through anywhere in the module — see
`content-service/CLAUDE.md`. `ai-service` landed on that same shape too, for its own `ChatSession`/
`PipelineMetrics` rows: `ChatSession.userUuid`/`PipelineMetrics.userUuid` are plain columns (not a
`User` foreign key) compared against the caller's own JWT `sub` claim, since a chat session or a
pipeline-metrics row only ever needs "is this the caller's own session," never another user's
profile data — see `ai-service/CLAUDE.md`. `gateway` is now a fifth module on this exact same
"claims only, no persisted row" shape, for the simplest reason of all: it has no entity of its own
left at all, not even one with a plain `ownerUuid`/`authorUuid`-style column, now that it has zero
embedded feature modules. `social-service` is the one remaining distinct shape: it
genuinely needs to search/list/join across *other* users' profile data (friend search, group
membership, DM threads), which claims-based identity can't satisfy — but rather than reusing
`common.entity.User` the way `gateway`/`identity-service` do, it persists its own **lean,
module-local** `SocialProfile` entity (`social.PROFILE`) with only the columns this module's own
code actually reads/writes (no password/provider/role/emailVerified/enabled) — seeded fully
independently, no shared class or table with any other deployable (see
`social-service/CLAUDE.md`'s "No coupling to `common.entity.User`" rule and the
`project-microservices-extraction-plan` memory for the full reasoning). If a future
`ecommerce-service`/`task-service`/`content-service` feature needs to *display* another user's
info, the plan is an event-driven read-model projection ("Option B"), not a resurrected/shared
`User` copy. This also means Epic 5's originally-planned `ecommerce-service` → `ai-service` Maven
dependency (for embedding generation) needs rethinking once that epic is actually built: even now
that `ai-service` is itself standalone, a standalone `ecommerce-service` still can't reach it as a
normal library dependency — it would need a real network call (likely through `gateway`, once
proxying exists, or the same `ContentServiceClient`-style `RestClient` pattern `ai-service` itself
uses to reach `content-service`), not a `pom.xml` entry. Don't add that Maven dependency back
without confirming this is still the intended shape.

**Long-term direction:** `ai-service` was the sixth and final module extracted into a standalone
service (own entry point, own `ai` schema/Liquibase changelog, own port `8086`, own duplicated JWT
verification, own Dockerfile/compose wiring), following the
`ecommerce-service`/`identity-service`/`task-service`/`social-service`/`content-service` precedent —
`gateway` now has **zero embedded feature modules remaining**. This closes out the
microservices-extraction-plan project: every module originally identified as a standalone-service
candidate has now been extracted, and there is no scheduled next candidate — this is a natural
stopping point for this microservices-study exercise, not a pause partway through one. `gateway` is
now JWT verification (claims-based, no persisted row of any kind — see the Security section below)
plus routing: it proxies external client requests for all six standalone services over HTTP, via
Spring Cloud Gateway Server MVC (`routing/GatewayRoutesConfig` — see `gateway/CLAUDE.md`). `product`
schema itself holds zero live tables (every one of the 23 tables it ever held, including its own
last holdout `USER`, was dropped outright once nothing mapped any of them anymore — see
`docs/CHANGELOG.md`'s `[Unreleased]` entry and the Database Conventions section below). `gateway`
has **no Liquibase story left at all** — its whole changelog tree (every already-run historical
changeset, plus the one-off drop changeset that retired `product`'s last 23 tables) was deleted
outright once nothing in it was needed anymore, and the one genuinely-still-needed thing it used to
bootstrap (the `keycloak` Postgres schema) moved to `docker/postgres/init.sql` instead — see the
Database Conventions section below. A future **big new feature area** still gets its own module per
the rule immediately below — that's unrelated to the routing work above, since routing forwards to
an existing standalone service's own REST layer rather than adding a new one. Cross-cutting
edge concerns beyond routing itself (CORS consolidation, rate limiting, timeouts, retry, circuit
breaker) remain individually per-service today, not yet centralized at `gateway`. See
`docs/CHANGELOG.md`'s `[Unreleased]` entry for the Docker/compose scaffolding already in place for
all seven services (`gateway`, `ecommerce-service`, `identity-service`, `task-service`,
`social-service`, `content-service`, `ai-service`).

**Post-extraction hardening: `infra` beans are now reached via explicit `@Import`/
`@EnableConfigurationProperties`, not broad package scanning — the end state of three rounds of
component-scan bugs.** None of the six standalone services' `@SpringBootApplication` classes
originally widened their scan to reach `infra`'s sibling package at all (Spring Boot's default
`@ComponentScan` is rooted at the annotated class's own package and does not recurse into a
sibling; only `gateway`, whose main class sits at this reactor's root package, ever picked up
`infra` "for free") — this had been silently breaking real, already-shipping code
(`identity-service`'s/`social-service`'s injection of `infra.service.StorageService`,
`ecommerce-service`'s/`content-service`'s injection of `infra.service.SlugService`,
`social-service`'s/`ai-service`'s `@EventHandler` listeners needing `infra`'s
`AsyncEventThreadPoolConfig` bean), never caught until each app was actually booted for the first
time. The first fix — widening `@ComponentScan`/`@ConfigurationPropertiesScan` to
`basePackages = {<own-package>, "com.ttg.devknowledgeplatform.infra"}` — worked, but went through
its own two rounds of bugs getting there (a bare `@ConfigurationPropertiesScan` doesn't reach a
sibling package any more than a bare `@ComponentScan` does; `infra.config.thread.AsyncEventThreadPoolConfig`
got instantiated — and failed to construct — on every service whose scan reached `infra`,
regardless of whether that service ever dispatches an `@EventHandler`, since a package scan can't
distinguish "reachable" from "actually needed"). That whole class of bug is why this reactor moved
off broad package-scanning into `infra` entirely: each of the six non-`gateway` entry-point classes
(`TaskServiceApplication`, `EcommerceServiceApplication`, `IdentityServiceApplication`,
`ContentServiceApplication`, `AiServiceApplication`, `SocialServiceApplication`) now names the
*exact* `infra` classes it uses via `@Import({...})` (for `@Component`/`@Configuration` classes) and
`@EnableConfigurationProperties(...)` (for the one bare `@ConfigurationProperties` POJO with no
`@Component`, `AsyncEventThreadPoolProperties`) — no `@ComponentScan`/`@ConfigurationPropertiesScan`
reaching `infra` at all anymore. A service keeps a bare, package-scoped `@ConfigurationPropertiesScan`
only if it has its own local `@ConfigurationProperties` classes to bind (`content-service`'s
`InternalApiProperties`, `ai-service`'s dozen-plus `config/*`/`config/chat/*` classes) — that's
always been safe, since those classes live in the service's own package tree, not a sibling.
Concrete benefit beyond just avoiding scan bugs: `AsyncEventThreadPoolConfig` (and its Micrometer
instrumentation) is now only created in the two services that actually dispatch an
`@EventHandler` (`ai-service`, `social-service`) — `task-service`/`ecommerce-service`/
`identity-service`/`content-service` no longer instantiate it at all, where a package scan always
would have. `social-service` also needed `@EnableAsync` fixed in the same overall pass — missing
entirely, which doesn't error the way a missing bean does (Spring just silently runs `@Async`
methods synchronously instead), so its two friend-request event listeners had been dispatching on
the calling thread instead of the dedicated pool the whole time. `JacksonConfig` moved from
`gateway`'s (nonexistent, since it has no REST controllers) own `config/` package into `infra`'s
`config/json/` as part of this same effort — every one of the six standalone services now
`@Import`s it explicitly, same as `TraceContextFilter`. See each service's own
`CLAUDE.md`/entry-point Javadoc and `docs/CHANGELOG.md`'s `[Unreleased]` entry for the full
per-service import list and the full three-round bug history. Any *new* module added to this
reactor should follow this same `@Import`/`@EnableConfigurationProperties` shape from the start for
whichever specific `infra` beans it actually needs — don't reach for a broad
`@ComponentScan(basePackages = {..., "com.ttg.devknowledgeplatform.infra"})` the way the very first
attempt at this did.

When proposing a new **big feature area** (broad scope, likely to grow), default to a dedicated
Maven module mirroring this shape — owns its own entities/services *and* its own REST controllers/
DTOs/mappers (a full vertical slice), depends only on `common`+`infra` (or, if it has a genuine
one-directional data need on an existing sibling, that sibling too) — rather than adding it into
`gateway` directly. See `content-service`'s `CLAUDE.md` for the pattern to copy.

## Build & Run Commands

```bash
# Build all modules
./mvnw clean package

# Build skipping tests
./mvnw clean package -DskipTests

# Build a single module and everything it depends on
./mvnw -pl <module> -am clean package

# Run all tests
./mvnw clean test

# Run a single test class
./mvnw -pl social-service -Dtest=FriendServiceImplTest test

# Start infrastructure (PostgreSQL pgvector, Redis, MinIO, Mailpit, Keycloak) — also creates the
# keycloak schema and pgvector/uuid-ossp extensions on first-ever run, via docker/postgres/init.sql
docker-compose -f docker-compose.infra.yml up -d

# Run Liquibase migrations for all six standalone services (gateway has none of its own anymore —
# see this file's Database Conventions section). This is one consolidated job living inside
# docker-compose.apps.yml, so it must be combined with docker-compose.infra.yml — it depends on
# that file's "postgres" service by Compose service name, which only resolves when both files are
# loaded into the same project:
docker compose -f docker-compose.infra.yml -f docker-compose.apps.yml run --rm services-liquibase

# task-service and social-service additionally still have their own standalone one-shot compose
# file (a leftover from before the consolidated job above existed), which hits Postgres's
# host-exposed port directly (host.docker.internal) instead of the Compose-internal service name —
# usable on its own, without docker-compose.infra.yml running as a Compose project:
docker-compose -f task-service-liquibase.yml up
docker-compose -f social-service-liquibase.yml up

# Build and run all seven independently-runnable Spring Boot processes — gateway +
# ecommerce-service + identity-service + task-service + social-service + content-service +
# ai-service — as containers, alongside the infra containers above (must combine both compose
# files in one command — see docs/PROJECT_STRUCTURE.md's Deployment section for why)
docker compose -f docker-compose.infra.yml -f docker-compose.apps.yml up -d --build

# Frontend
cd gui && npm install && npm run build
```

`gui` has no test framework configured yet — don't assume `npm test` works; see `gui/CLAUDE.md`.

**Active Spring profiles:** `local` for local dev, `docker` for containerised. Pass `-Dspring.profiles.active=local`.

**JDK note:** if `./mvnw` fails with `invalid flag: --release`, your shell's default `JAVA_HOME` is
pointed at an older JDK — this project needs 21. Locate a JDK 21 install and export `JAVA_HOME`/`PATH`
for the command rather than changing the system default.

## Documentation Protocol

Before starting any task:
1. Read `docs/PROJECT_STRUCTURE.md` — current module layout, packages, architecture.
2. Read `docs/CHANGELOG.md` — recent changes; avoid duplicating or conflicting work.
3. Read the `CLAUDE.md` of every module you're about to touch (see Module Structure above).

After completing a task, update those files if your changes affected modules, packages, entities, endpoints, DB schema, dependencies, security rules, or GUI pages. Use the `[Unreleased]` section in CHANGELOG; format follows [Keep a Changelog](https://keepachangelog.com/). If the change altered a module's local conventions or constraints, update that module's `CLAUDE.md` too — don't let it drift out of sync the way this file itself had (see git history around 2026-07-06 for examples of the drift this caused: a JWT provider class rename, a `UserUtils.getCurrentUser()` method that never existed, a removed `npm test` script — all went undocumented until caught during an unrelated task).

**`docs/CHANGELOG.md` was cut into a real `[0.0.2]` release on 2026-08-11 and archived to `docs/CHANGELOG-ARCHIVE.md`**, once the live file's single `[Unreleased]` section had grown past ~3700 lines without ever being cut into an actual version — see that file's own intro for the reasoning. `[0.0.1]` is the original monolith; `[0.0.2]` is everything accumulated since, up to and including the full microservices break-up — matching the Maven `<version>` on every module's own `pom.xml` (`0.0.2-SNAPSHOT` as of this cut). `docs/CHANGELOG.md` now starts with a fresh, empty `[Unreleased]`; every existing `CLAUDE.md` reference to "`docs/CHANGELOG.md`'s `[Unreleased]` entry" for something that landed before that date now resolves to `docs/CHANGELOG-ARCHIVE.md`'s `[0.0.2]` section instead — those references were not swept and rewritten one by one (a large, low-value mechanical pass), so don't be surprised to find one still pointing at "`[Unreleased]`" when the content actually lives in the archive's `[0.0.2]`. When a `CLAUDE.md` reference to CHANGELOG content turns out stale for this reason, fix it opportunistically the same way any other doc drift gets fixed — see the note above.

## Architecture

### Routing

`gateway` is the single entry point for external clients — every request the GUI makes goes to
`gateway` (port `8080`), which proxies it to whichever standalone service owns that path, via
Spring Cloud Gateway Server MVC (`gateway/routing/GatewayRoutesConfig`, one `RouterFunction` bean
per backend service). Paths are forwarded unchanged — no prefix stripping/rewriting — since every
backend already expects exactly the path it was called with from before this gateway existed.

Routing is resource-specific, not top-level-prefix-based, because three prefixes are shared by more
than one service and only disambiguate one segment deeper:

| Prefix | Owner(s) | Disambiguated by |
|---|---|---|
| `/api/v1/users/**` | `identity-service` (own profile) **and** `social-service` (other users) | `identity-service` owns `/me/**`; `social-service` owns `/public/**` and `/search` |
| `/api/v1/public/**` | `content-service` **and** `ecommerce-service` | `content-service` owns `/question-answers/**`/`/articles/**`; `ecommerce-service` owns `/products/**` and `/product-categories/**` (the latter backs the storefront's category filter rail, since a logged-out shopper can't reach the admin-gated `/api/v1/admin/product-categories/**`) |
| `/api/v1/admin/**` | `ecommerce-service`, `content-service`, **and** `ai-service` | each one's own resource segment never collides with another's (`/products/**` vs `/articles/**` vs `/embeddings/**`, etc.) |

Every other path maps to exactly one service by its own resource prefix — `/api/v1/auth/**` →
`identity-service`, `/api/v1/projects/**`/`/api/v1/tasks/**` → `task-service`,
`/api/v1/dms/**`/`/friends/**`/`/groups/**`/`/channels/**` → `social-service`,
`/api/v1/chat/sessions/**` → `ai-service`. See `GatewayRoutesConfig`'s own Javadoc for the
complete, current table — this section is a summary, not the source of truth; re-derive it from
the actual `@RequestMapping`s (via a reactor-wide grep) rather than trusting either copy if a
service's routes ever change.

**Not routed through `GatewayRoutesConfig` on purpose:**
- `content-service`'s `/internal/content-items/**` — service-to-service traffic (`ai-service`
  calls it directly on `content-service`'s own port, gated by a shared `X-Internal-Api-Key`
  header, not a JWT), never meant for external clients.
- `ai-service`'s `/api/v1/chat/stream` (the SSE streaming chat response) — **but this one is still
  proxied by `gateway`, just not through `GatewayRoutesConfig`'s usual `RouterFunction` DSL.**
  Spring Cloud Gateway Server MVC has real, documented problems proxying Server-Sent Events
  (connection leaks, broken chunked streaming), so `routing/ChatStreamProxyController` relays this
  one path by hand instead — a purpose-built streaming proxy using the JDK's own `HttpClient`
  (chosen over Spring's `RestClient` specifically because it needs the upstream status code
  available before committing to stream the body, which `RestClient.exchange()`'s callback-scoped
  API doesn't fit). The GUI never calls `ai-service` directly for anything anymore as a result —
  its `chatApi.streamChat` points back at `gateway`, same origin as everything else.
  `listSessions`/`getSessionHistory` are unaffected either way — both are plain REST and go
  through `gateway`'s normal `RouterFunction` routing via `/api/v1/chat/sessions/**` above.
- The GUI's WebSocket/STOMP connection (`social-service`'s chat transport) — never routed at all;
  Gateway Server MVC only proxies plain HTTP, not a protocol upgrade, and `ChatStreamProxyController`
  is HTTP-only too. The GUI's `socket.ts` talks to `social-service`'s own origin directly,
  independent of wherever the REST API points.

**CORS is fully consolidated at `gateway` now — zero exceptions.** `gateway/security/CorsConfig`
is the sole CORS source in this reactor; every other service's own copy (only `ai-service` ever had
one) was deleted outright once `ChatStreamProxyController` landed, since nothing calls any backend
service directly from a browser anymore, for any path. `ai-service`'s own `SecurityConfig` dropped
its `.cors(...)` wiring entirely as a result — a server-to-server call (which is all it receives
now) never triggers CORS in the first place. **Backend
base URLs** are configured per-service (`app.services.*`, `GatewayServicesProperties`) —
`localhost:<port>` by default (running each service directly on the host), overridden in
`application-docker.yml` to each service's Compose DNS name. Rate limiting, timeouts, retry, and
circuit breaker are still individually per-service, not yet centralized at `gateway`; load
balancing and service discovery are deliberately not built, since there is exactly one instance of
each service at a fixed address in this deployment.

### Request Flow

```
GUI (React) → ai-service/ChatController → ai-service/RagQueryService
                                      ├── EmbeddingService (OpenAI text-embedding-3-small)
                                      ├── ContentEmbeddingRepository (pgvector cosine similarity)
                                      └── StreamingChatLanguageModel (LangChain4j; model configurable per-request)
```

Chat responses stream token-by-token over SSE. The SSE event sequence is:
1. `event: sources` — JSON array of `RagSource` objects
2. `event: token` — repeating, one token per event
3. `event: done` — payload `"[DONE]"`

Pipeline stage detail, config properties, and thresholds: `ai-service/CLAUDE.md`.

### Content Indexing

Content publishing → RAG ingestion spans two modules, over HTTP rather than a shared JPA context:
`content-service` defines `ContentItem` and exposes it (plus its `QuestionAnswer`/`Article` subtype
fields, flattened) over its own `/internal/content-items/**` API; `ai-service` owns the
orchestration (`ContentIndexingService`) that calls that API via `ContentServiceClient`, then
chunks + embeds + stores `ContentEmbedding` rows via its own `ContentIngestionService`. Async — does
not block the API response. Today, indexing is admin-triggered (`ai-service`'s
`IngestionController`), not automatic on publish — `content-service`'s `ContentPublishedEvent` has
never had a publisher wired up, and the listener that used to react to it
(`ContentPublishedEventListener`) was deleted as dead code during the content-service extraction's
step 5 rather than rewired, since an in-process Spring event can't cross a service boundary anyway
— see `content-service/CLAUDE.md` and `docs/CHANGELOG.md`'s `[Unreleased]` entry.

### Security

Keycloak is the identity provider (hosted login page, Authorization Code + PKCE; Google/Facebook brokered inside Keycloak itself). Every deployable (`gateway`, `ecommerce-service`, `identity-service`, `task-service`, `social-service`, `content-service`, `ai-service`) is a pure OAuth2 resource server — each only ever verifies bearer tokens against Keycloak's JWKS (`spring.security.oauth2.resourceserver.jwt.issuer-uri`, same realm), never issues them. `KeycloakRealmRoleConverter` (maps `realm_access.roles` to `ROLE_*` authorities) and the claims-only variant of `KeycloakJwtAuthenticationConverter` are now shared via `infra.security` (see `infra/CLAUDE.md`) rather than duplicated seven times — `gateway`/`ecommerce-service`/`task-service`/`content-service`/`ai-service` all use the shared beans directly. `identity-service` and `social-service` still keep their own local `KeycloakJwtAuthenticationConverter`, because they don't all do the same thing with it: **`identity-service` is now the only deployable that JIT-provisions/refreshes a persisted user row at all** — its own `User` entity (moved out of `common`, see the dependency-order section above) into `identity.USER`, via its own in-process `UserService.findOrCreateFromKeycloak` (both live in the same standalone app, so no duplication is needed the way every other converter in this reactor has to). `social-service` also JIT-provisions/refreshes a local row, but a lean **module-local** `SocialProfile` entity into `social.PROFILE` — never `identity-service`'s `User` — since it needs real search/list/join capability across users but has no auth-lifecycle concern to justify a full shared entity, and can't reach `identity-service` in-process anyway now that both are standalone (see `social-service/CLAUDE.md`'s "No coupling to `common.entity.User`" rule — the name predates this move but the reasoning is unchanged). `gateway`, `ecommerce-service`, `task-service`, `content-service`, and `ai-service` all persist no caller-identity row at all — each converter builds the `CustomOAuth2User` principal straight from the JWT's claims (`sub` standing in for `userUuid`), for reasons landing on the same shape, though `gateway`'s is the simplest of the five: it has no entity of its own left at all (zero embedded feature modules), so there was never anything to key a plain `ownerUuid`/`authorUuid`-style column against in the first place, and the authorization decision (`ROLE_ADMIN` or not) was always read straight off the token's `realm_access.roles` claim, never a database row — see `docs/CHANGELOG.md`'s `[Unreleased]` entry for the JIT-provisioning it used to do into `product.USER` before that table was dropped outright. `ecommerce-service` has no entity with a foreign key onto a user at all; `task-service`'s `Project`/`Task` and `content-service`'s `ContentItem` each reference an author/owner, but only via a plain `ownerUuid`/`authorUuid` column compared against (or stamped from) the JWT's own `sub` claim, never a `User` foreign key, since every check there only ever needs "is this row's owner/author the caller," never another user's profile data (see `ecommerce-service/CLAUDE.md`, `task-service/CLAUDE.md`'s "No local `User` copy" rule, and `content-service/CLAUDE.md`'s equivalent rule, for the "Option C" reasoning all three follow). `ai-service` is on this same shape too, with a wrinkle: unlike `ecommerce-service`, it *does* persist domain rows that reference the caller (`ChatSession`, `PipelineMetrics`), but only via a plain `userUuid` column compared against the JWT's own `sub` claim, never a `User` foreign key — same reasoning as `task-service`'s `ownerUuid`/`content-service`'s `authorUuid`, just applied to a chat session/analytics row instead of an owned task or authored article. `@CurrentUserId` resolves differently per deployable as a result: in `identity-service`/`social-service` it's `Integer`, that deployable's own local numeric PK (`social-service`'s own `SocialProfile.id`, not `identity-service`'s `User` PK — the two are unrelated tables in unrelated schemas); in `task-service`/`content-service`/`ai-service` it's `String`, the caller's Keycloak UUID read straight off the principal with no database lookup at all; `gateway` has no `@CurrentUserId` consumer left at all (zero REST controllers) — there is no single cross-service `User` PK regardless. Role-based access via `UserRole` enum, sourced from the token's `realm_access.roles` claim (`social-service` has no admin-gated endpoint, so it never branches on this; `content-service` and `ai-service` do, for their `/api/v1/admin/**` surfaces). This is a multi-phase migration in progress — see `docs/CHANGELOG.md`'s `[Unreleased]` entries for what's landed vs. still pending (the `gui` rework). Current-user resolution patterns: `gateway/CLAUDE.md`.

## Database Conventions

**Schema:** `gateway` retains **zero live tables** in the `product` schema, one shared Postgres
database (`dev-premier`) — every one of the 23 tables `product` ever held (its own last holdout,
`USER`, included) was dropped outright once nothing in this reactor mapped any of them anymore, see
the Liquibase bullet below and `docs/CHANGELOG.md`'s `[Unreleased]` entry. The schema container
itself is left in place (harmless, unused namespace) — only its tables were dropped. Each standalone
service that persists its own tables gets its own schema in that same database instead of its own
database instance — `ecommerce-service` → `ecommerce`, `identity-service` → `identity`,
`task-service` → `task`, `social-service` → `social`, `content-service` → `content`, `ai-service` →
`ai` — per-service-per-schema, not per-service-per-database (see the
`project-microservices-extraction-plan` memory for why). `content-service`'s own `application.yml`
now sets `hibernate.default_schema: content` and its entities no longer hardcode
`@Table(schema = "product")`, so its schema is live in code, and its own
`content-service-liquibase.yml` compose file now exists to create it — but neither has actually been
run/booted against a real Postgres yet in this session, so treat `content.CATEGORY`/etc. as
unverified-at-runtime the same way every other standalone service's schema was when it first landed;
`ai-service`'s own `ai` schema carries the identical caveat. `identity-service`'s own `User` entity
(moved out of `common` — see the dependency-order section above) is the sole entity in this whole
reactor mapped to a `USER` table today, into `identity.USER`; its `@Table` deliberately does **not**
hardcode a schema — this app's own `hibernate.default_schema: identity` property resolves it at
runtime, same convention every entity in this reactor follows now. Never hardcode a schema on a
class shared across deployables (this doesn't apply to `User` anymore now that only one deployable
maps it, but still applies to genuinely shared classes like `common.entity.AbstractEntity`); do that
instead per-app via `hibernate.default_schema` (see `identity-service/User`'s Javadoc for the
incident this rule comes from — a hardcoded schema on this same class, back when it lived in
`common` and both `gateway` and `ecommerce-service` mapped it, silently broke `ecommerce-service`'s
isolation for months; the same bug class was caught and fixed pre-emptively in `task-service`'s,
`social-service`'s, and `content-service`'s own entities, before any of them ever ran against a real
database with the new schema live).

**Sequences:** each table has its own sequence (`TABLE_NAME_SEQ`).

**Audit columns** on all entities: `usrCreation`, `dteCreation`, `usrLastModification`, `dteLastModification`, `version`.

**Migrations — Liquibase:**
- **`gateway` has no Liquibase changelog at all anymore.** Its whole tree
  (`gateway/src/main/java/com/ttg/devknowledgeplatform/database/sql/`, master changelog
  `dev-knowledge-platform.xml`) was deleted outright — every changeset that ever created a
  `product` table (`USER`, `CATEGORY`/`TAG`/`CONTENT_ITEM`/etc., the old friend-graph/chat tables,
  `PROJECT`/`TASK`) plus the final `DKP-0033` changeset that dropped all 23 of those tables in one
  statement (naming every one, `CASCADE` for the FKs between them). Nothing in this reactor needs
  any of that history anymore: every table it ever described either moved to a standalone service's
  own fresh-snapshot changelog (below) or was dropped outright with nothing replacing it (`USER`
  itself, now solely `identity-service`'s `identity.USER`). `liquibase-core` was removed from
  `gateway/pom.xml` alongside the deletion — this app no longer has any Liquibase dependency,
  config, or classpath-resources wiring at all. The one genuinely-still-needed thing the old tree
  used to bootstrap — the `keycloak` Postgres schema itself, since Keycloak's own internal
  migration assumes it already exists — moved to `docker/postgres/init.sql` instead (`CREATE SCHEMA
  IF NOT EXISTS keycloak`), which already runs automatically before Postgres reports healthy, which
  every service's `depends_on` already waits on. Don't add a new changelog tree back here; any
  future `gateway`-owned table (should one ever exist again) would start fresh, same as any other
  module's first migration.
- Each standalone service migrates its own schema from its own changelog tree instead
  (`ecommerce-service/.../database/sql/ecommerce-service.xml`,
  `identity-service/.../database/sql/identity-service.xml`,
  `task-service/.../database/sql/task-service.xml`,
  `social-service/.../database/sql/social-service.xml`,
  `content-service/.../database/sql/content-service.xml`,
  `ai-service/.../database/sql/ai-service.xml`) — never add a new module's tables to `gateway`'s
  changelog once that module is a standalone service. All six run via one consolidated
  `services-liquibase` job baked into `docker-compose.apps.yml` (a single container looping
  `liquibase ... update` over each service's mounted changelog directory in turn, against the
  Compose-internal `postgres` service — must be run combined with `docker-compose.infra.yml`, see
  the Build & Run Commands section above). **Only `task-service` and `social-service` additionally
  have their own standalone single-service `*-liquibase.yml` compose file at the repo root** (a
  leftover from before that consolidated job existed, hitting Postgres's host-exposed port via
  `host.docker.internal` instead) — `ecommerce-service`, `identity-service`, `content-service`, and
  `ai-service` do **not** have an equivalent standalone file of their own, despite several
  `CLAUDE.md`/`pom.xml`/`application.yml` comments across this reactor claiming otherwise until this
  was caught and fixed on 2026-08-16; the consolidated job is their only migration path today.
  `content-service`'s own changelog (a fresh snapshot of the final table shape, not a replay of
  `gateway`'s incremental history) and `ai-service`'s own changelog (`DKP-0032`, another fresh
  snapshot rather than a replay of `gateway`'s incremental `CONTENT_EMBEDDING`/`CHAT_SESSION`/etc.
  history, into the new `ai` schema) are both still unverified-at-runtime, same caveat every
  standalone extraction has carried at this stage.
- Naming: `YYYY/VERSION/YYYYMMDDHHMI__VERSION__TICKET__description.sql`
  - Example: `2026/0.0.2/202608080001__0.0.2__DKP-0032__add_ai_service_tables.sql` — `ai-service`'s own
    changelog, real and current. Every standalone service's changelog tree was renamed from a
    `0.0.1` version segment to `0.0.2` on 2026-08-11, alongside the changelog's own retroactive cut
    of `[0.0.2]` (see the note earlier in this file and `docs/CHANGELOG.md`'s intro) — `0.0.1` is the
    monolith, `0.0.2` is everything since, including these six fresh-snapshot changelogs. None of
    these changesets had been run against a real database at the time of the rename (see each
    module's own `CLAUDE.md`), so the rename didn't risk a Liquibase checksum mismatch.

## Operational Rules

These rules govern how Claude must behave in this project. Follow them strictly on every task.

### Clarify before acting
- **Never guess** about intent, scope, or ambiguous requirements. If a task is underspecified, ask targeted questions before writing any code.
- Batch all clarifying questions into a single message — do not ask one at a time.
- If confidence is low about an approach, say so explicitly and propose alternatives rather than silently picking one.

### Observe before acting
- Read the relevant files and understand the existing pattern before making changes.
- If a similar feature already exists elsewhere in the codebase, follow that pattern unless there is a clear reason not to.
- Before adding a new class, check whether the abstraction already exists in `common/`.

### Design pattern recommendation
- When solving a non-trivial design problem, suggest applicable **GoF design patterns** before writing code. Categorise the suggestion:
  - **Creational** (Factory, Builder, Singleton, Prototype, Abstract Factory)
  - **Structural** (Adapter, Bridge, Composite, Decorator, Facade, Flyweight, Proxy)
  - **Behavioral** (Chain of Responsibility, Command, Iterator, Mediator, Memento, Observer, State, Strategy, Template Method, Visitor)
- Briefly explain why the pattern fits and note any trade-offs.
- If multiple patterns are applicable, compare them before recommending one.

### Javadoc
- Write Javadoc for every generated **class, interface, enum, and public method**.
- Minimum: one-sentence `/** ... */` summary on the type; `@param`, `@return`, `@throws` on public methods where non-obvious.
- Do not write Javadoc that only restates the method name (e.g., avoid `/** Gets the name. */` on `getName()`).
- DTOs and simple getters/setters are exempt from `@param`/`@return` but still need a class-level summary.

### Changelog & structure docs
- After any task that adds, removes, or renames a class / endpoint / DB table / dependency: update `docs/CHANGELOG.md` (under `[Unreleased]`) and `docs/PROJECT_STRUCTURE.md`.

### Don't run a full build to verify
- **Never run `./mvnw clean package`/`./mvnw clean install` (or any other full-reactor build) as a self-check after making changes.** This is a 9-module Maven reactor; a clean build is slow, and running it — especially more than once in the same task (e.g. once after code changes, again after a docs-only pass) — burns significant wall-clock time for little benefit.
- Rely on reading the code carefully, IDE diagnostics if available, and targeted `-pl <module> -am` compiles only if there's a specific, concrete reason to suspect a compile error (e.g. a signature change with many call sites). The user builds and runs the project themselves when they're ready to.
- If a task's own instructions explicitly ask for a build (e.g. "build and confirm this compiles"), that's an explicit request, not a self-imposed verification step — follow it.

### Batch independent file edits
- When a task touches multiple files that don't depend on each other's content (e.g. updating several modules' own `CLAUDE.md`, or `docs/PROJECT_STRUCTURE.md` alongside `docs/CHANGELOG.md`), read/edit them in parallel tool calls within the same message rather than one at a time in sequence. Each Read/Edit round trip has its own latency; doing ten of them back-to-back when none of them needs the previous one's result just stacks that latency for no benefit.
- Only go sequential when there's a real dependency — e.g. an edit to file B needs to reference exact wording just added to file A, or a later edit's `old_string` depends on an earlier edit having already landed in the same file.
- This applies most often to the documentation-update pass required by "Changelog & structure docs" above and to updating multiple modules' `CLAUDE.md` files after a cross-cutting change — exactly the kind of task that touches many independent files at once.

### Loop detection
- If the same fix fails twice for the same root cause, stop, explain the situation, and ask for direction rather than attempting a third variation.

### Explain the "why"
- For every non-trivial implementation decision, explain the reasoning — not just what was done, but why this approach over alternatives.
- When introducing a design pattern, pair it with a real-world analogy and tie it back to the specific problem in the codebase.
- When writing a Liquibase migration, briefly explain the SQL reasoning (index type choice, data type trade-offs, constraint rationale).

### Always offer an alternative
- For any non-trivial feature, present at least one alternative implementation with a short trade-off comparison before writing code.
- If one alternative uses a different design pattern or a different layer of the stack, prefer that — it broadens exposure more.
- Label clearly: `Option A (chosen):` and `Option B (alternative):`.

### Use and teach modern Java
- Proactively reach for Java 21 features when they improve clarity: records for immutable DTOs, sealed classes + pattern matching for exhaustive type handling, text blocks for multiline strings, `SequencedCollection` where applicable.
- When using a modern feature, add a one-line comment explaining what it is if it may be unfamiliar — not in production code, but in the response text.
- Virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`) are worth suggesting for I/O-bound work; flag when the current thread pool config could benefit.

### Proactive ideas
- After completing a task, suggest one optional follow-up experiment or enhancement — label it clearly as `Idea:` so it is easy to skip.
- Ideas should stretch into adjacent territory: a new Spring Boot 3 feature, an unexplored LangChain4j capability, a pgvector index strategy, a React 18 concurrent feature, etc.
- Prioritise ideas that would introduce a concept not yet used in this codebase.

### Surface adjacent knowledge
- When a concept appears in the implementation (e.g., HNSW indexing, Bucket4j token bucket, SSE backpressure), briefly explain the underlying concept in 2–3 sentences in the response — not in code comments.
- If a well-known paper, RFC, or resource is directly relevant, mention it by name.

### Code smell as learning moment
- When spotting a code smell (God class, primitive obsession, feature envy, etc.), name the smell explicitly, explain what it signals, and show both the quick fix and the deeper refactor — let the user choose.
- Never silently fix a smell without pointing it out; the explanation is the value.

---

## Code Conventions

Project-wide conventions only — module-specific ones (e.g. exactly which package a mapper lives in,
where a given service's repository is) are in that module's `CLAUDE.md`.

- **DTOs ↔ entities** — always use MapStruct mappers. Never map manually. Mappers and DTOs live in the
  same module as the entity/service they front (`content-service`, `social-service`, `ai-service`,
  `identity-service`) — each feature module owns its full vertical slice, entity through REST
  controller. `gateway` holds no DTOs/mappers of its own today; it would only gain one for a
  cross-module orchestration endpoint that genuinely can't live in a single feature module (see
  `gateway/CLAUDE.md`).
- **Dynamic filtering** — use the Specification pattern; each module owns its own
  `repository/spec/` package for its own entities. Do not build JPQL strings for filtering.
- **Domain exceptions** — services throw `BusinessException`/`ApiException` with an `ErrorCode`.
  `ErrorCode` is an interface (`common/exception/`); `CommonErrorCode` (`common`) holds
  cross-cutting codes, and each feature module that owns entities/error conditions has its own
  `*ErrorCode` enum implementing the interface (e.g. `content-service`'s `ContentErrorCode`). When
  adding a new error code, check the owning module's `CLAUDE.md` for which enum it belongs in.
  Never leak Spring exceptions to callers.
- **Dates** — use `Instant` everywhere (via `common`'s `DateUtils`). No `java.util.Date`.
- **Current user** — see `gateway/CLAUDE.md`; do not reference `UserUtils.getCurrentUser()`, it does
  not exist (`UserUtils` only has `getUserName()`/`isAuthenticated()`).
