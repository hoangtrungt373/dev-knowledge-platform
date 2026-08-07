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
| `common` | Shared entities (`User`), enums, exceptions, base DTOs, `@CurrentUserId` | [`common/CLAUDE.md`](common/CLAUDE.md) |
| `infra` | Shared Spring infra: async event framework, `SlugService`, `StorageService` (MinIO) | [`infra/CLAUDE.md`](infra/CLAUDE.md) |
| `content-service` | Categories, tags, content items (Q&A, articles) — the RAG corpus; own REST/DTO/mapper layer. **Standalone Spring Boot app, own `content` schema, own port (8085)** — not a Maven dependency of `gateway` (see below) | [`content-service/CLAUDE.md`](content-service/CLAUDE.md) |
| `ai-service` | RAG pipeline (embedding, vector search, LLM generation via LangChain4j), chat + content-indexing REST layer. **Standalone Spring Boot app, own `ai` schema, own port (8086)** — not a Maven dependency of `gateway` (see below) | [`ai-service/CLAUDE.md`](ai-service/CLAUDE.md) |
| `identity-service` | Keycloak JIT-provisioning/profile mutation for its own local `User` copy. **Standalone Spring Boot app, own `identity` schema, own port (8082)** — not a Maven dependency of `gateway` (see below) | [`identity-service/CLAUDE.md`](identity-service/CLAUDE.md) |
| `ecommerce-service` | Study-project e-commerce vertical slice: catalog, cart/checkout, orders/inventory, payments, reviews/recommendations. **Standalone Spring Boot app, own schema** — not a Maven dependency of `gateway` (see below) | [`ecommerce-service/CLAUDE.md`](ecommerce-service/CLAUDE.md) |
| `task-service` | Personal task/project management. **Standalone Spring Boot app, own `task` schema, own port (8083)** — not a Maven dependency of `gateway` (see below) | [`task-service/CLAUDE.md`](task-service/CLAUDE.md) |
| `social-service` | Friend graph + chat (groups/channels, DMs), incl. its own WebSocket/STOMP transport. **Standalone Spring Boot app, own `social` schema, own port (8084)** — not a Maven dependency of `gateway` (see below) | [`social-service/CLAUDE.md`](social-service/CLAUDE.md) |
| `gateway` | Security/JWT-filter wiring, Liquibase, Spring Boot entry point. **Zero embedded feature modules, zero REST controllers of its own** — a future cross-module REST orchestration endpoint is the only thing that would still land here | [`gateway/CLAUDE.md`](gateway/CLAUDE.md) |
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
`dev-knowledge-platform-apps-docker-compose.yml` entry now. `ai-service`'s own `ContentServiceClient`
HTTP call to `content-service` is the one real inter-service call that exists today; a
general-purpose `gateway`-side HTTP proxy to any of the six for end-user traffic is not built yet
(see each module's own `CLAUDE.md`). `gateway` and `identity-service` persist a `User` row today by reusing `common.entity.User` directly
— each JIT-provisions its own independent local copy from the same Keycloak identity (via its own
duplicated `KeycloakJwtAuthenticationConverter`, never a shared class) into its own schema's `USER`
table (`product.USER`/`identity.USER`). `ecommerce-service` and `task-service` both deliberately do
**not** persist any local copy at all, for two different concrete reasons that land on the same
"Option C" shape (see the `project-microservices-extraction-plan` memory's "Option C" decision):
`ecommerce-service` has no entity with a foreign key onto a user at all, so the only thing it ever
needed from Keycloak was "who is the caller, and are they an admin"; `task-service`'s
`Project`/`Task` entities *do* reference an owner, but only ever to answer "is this row's owner the
caller" — a plain `ownerUuid` column compared against the verified JWT's own `sub` claim answers
that with zero DB access. `content-service` landed on the exact same shape as `task-service` for
the exact same reason: `ContentItem.authorUuid` is a plain column (not a foreign key), stamped
once at creation and never read back or joined through anywhere in the module — see
`content-service/CLAUDE.md`. `ai-service` landed on that same shape too, for its own `ChatSession`/
`PipelineMetrics` rows: `ChatSession.userUuid`/`PipelineMetrics.userUuid` are plain columns (not a
`User` foreign key) compared against the caller's own JWT `sub` claim, since a chat session or a
pipeline-metrics row only ever needs "is this the caller's own session," never another user's
profile data — see `ai-service/CLAUDE.md`. `social-service` is a fourth, distinct shape: it
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
now purely JWT verification/JIT-provisioning of its own local `product.USER` row, Liquibase
migrations for its own (mostly frozen/orphaned) tables, and `UserSeeder`. A future
**big new feature area** still gets its own module per the rule immediately below, and a future
general-purpose `gateway`-side HTTP proxy for end-user traffic to any of the six standalone services
remains unbuilt and undecided — but there is no more embedded-module extraction work left to
schedule. See `docs/CHANGELOG.md`'s `[Unreleased]` entry for the Docker/compose scaffolding already
in place for all seven services (`gateway`, `ecommerce-service`, `identity-service`, `task-service`,
`social-service`, `content-service`, `ai-service`).

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

# Start infrastructure (PostgreSQL pgvector, Redis, MinIO, Mailpit, Keycloak)
docker-compose -f dev-knowledge-platform-docker-compose.yml up -d

# Run Liquibase migrations
docker-compose -f dev-knowledge-platform-liquibase.yml up

# Build and run all seven independently-runnable Spring Boot processes — gateway +
# ecommerce-service + identity-service + task-service + social-service + content-service +
# ai-service — as containers, alongside the infra containers above (must combine both compose
# files in one command — see docs/PROJECT_STRUCTURE.md's Deployment section for why)
docker compose -f dev-knowledge-platform-docker-compose.yml -f dev-knowledge-platform-apps-docker-compose.yml up -d --build

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

## Architecture

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

Keycloak is the identity provider (hosted login page, Authorization Code + PKCE; Google/Facebook brokered inside Keycloak itself). Every deployable (`gateway`, `ecommerce-service`, `identity-service`, `task-service`, `social-service`, `content-service`, `ai-service`) is a pure OAuth2 resource server — each only ever verifies bearer tokens against Keycloak's JWKS (`spring.security.oauth2.resourceserver.jwt.issuer-uri`, same realm), never issues them. Each has its own `KeycloakJwtAuthenticationConverter`, but they don't all do the same thing with it: `gateway` JIT-provisions/refreshes **its own local `User` row** from the token's claims into `product.USER` by inlining the find-or-create logic directly via `common`'s `UserRepository`; `identity-service` does the same JIT-provisioning into `identity.USER`, but its converter delegates to its own in-process `UserService.findOrCreateFromKeycloak` instead, since both live in the same standalone app. `social-service` also JIT-provisions/refreshes a local row, but a lean **module-local** `SocialProfile` entity into `social.PROFILE` — never `common.entity.User` — since it needs real search/list/join capability across users but has no auth-lifecycle concern to justify the full shared entity (see `social-service/CLAUDE.md`'s "No coupling to `common.entity.User`" rule). `ecommerce-service`, `task-service`, `content-service`, and `ai-service` all persist no caller-identity row at all — each converter builds the `CustomOAuth2User` principal straight from the JWT's claims (`sub` standing in for `userUuid`), for reasons landing on the same shape: `ecommerce-service` has no entity with a foreign key onto a user at all; `task-service`'s `Project`/`Task` and `content-service`'s `ContentItem` each reference an author/owner, but only via a plain `ownerUuid`/`authorUuid` column compared against (or stamped from) the JWT's own `sub` claim, never a `User` foreign key, since every check there only ever needs "is this row's owner/author the caller," never another user's profile data (see root `CLAUDE.md`'s dependency-order section, `ecommerce-service/CLAUDE.md`, `task-service/CLAUDE.md`'s "No local `User` copy" rule, and `content-service/CLAUDE.md`'s equivalent rule, for the "Option C" reasoning all three follow). `ai-service` is a fourth module on this same shape, with a wrinkle: unlike `ecommerce-service`, it *does* persist domain rows that reference the caller (`ChatSession`, `PipelineMetrics`), but only via a plain `userUuid` column compared against the JWT's own `sub` claim, never a `User` foreign key — same reasoning as `task-service`'s `ownerUuid`/`content-service`'s `authorUuid`, just applied to a chat session/analytics row instead of an owned task or authored article. `@CurrentUserId` resolves differently per deployable as a result: in `gateway`/`identity-service`/`social-service` it's `Integer`, that deployable's own local numeric PK (`social-service`'s own `SocialProfile.id`, not a `common.entity.User` PK); in `task-service`/`content-service`/`ai-service` it's `String`, the caller's Keycloak UUID read straight off the principal with no database lookup at all — there is no single cross-service `User` PK. Role-based access via `UserRole` enum, sourced from the token's `realm_access.roles` claim (`social-service` has no admin-gated endpoint, so it never branches on this; `content-service` and `ai-service` do, for their `/api/v1/admin/**` surfaces). This is a multi-phase migration in progress — see `docs/CHANGELOG.md`'s `[Unreleased]` entries for what's landed vs. still pending (the `gui` rework). Current-user resolution patterns: `gateway/CLAUDE.md`.

## Database Conventions

**Schema:** `gateway` retains only its own `USER` table (plus its now-frozen, orphaned historical
tables — see the Liquibase bullet below) in the `product` schema, one shared Postgres database
(`dev-premier`) — `product` no longer holds any *live* feature-module tables at all now that
`ai-service` has its own schema too. Each standalone service that persists its own tables gets its
own schema in that same database instead of its own database instance — `ecommerce-service` →
`ecommerce`, `identity-service` → `identity`, `task-service` → `task`, `social-service` → `social`,
`content-service` → `content`, `ai-service` → `ai` — per-service-per-schema, not
per-service-per-database (see the `project-microservices-extraction-plan` memory for why).
`content-service`'s own `application.yml` now sets `hibernate.default_schema: content` and its
entities no longer hardcode `@Table(schema = "product")`, so its schema is live in code, and its own
`content-service-liquibase.yml` compose file now exists to create it — but neither has actually been
run/booted against a real Postgres yet in this session, so treat `content.CATEGORY`/etc. as
unverified-at-runtime the same way every other standalone service's schema was when it first landed;
`ai-service`'s own `ai` schema carries the identical caveat. `gateway`'s own `product.CATEGORY`/
`TAG`/`CONTENT_ITEM`/etc. and `product.CONTENT_EMBEDDING`/`CHAT_SESSION`/`CHAT_MESSAGE`/`SYS_PARAM`/
`PIPELINE_METRICS` are now orphaned — `gateway`'s Spring context maps no entity to any of them
anymore, now that it has zero embedded feature modules, same as `product.PROJECT`/`TASK` and the old
friend-graph tables before them. `common.entity.User` is mapped only by the deployables that
actually persist a `User` row via that shared entity (`gateway`, `identity-service` — not
`ecommerce-service`/`task-service`/`social-service`/`content-service`/`ai-service`, see the Security
section above; `social-service` persists its own unrelated `SocialProfile` entity instead), and its
`@Table` deliberately does **not** hardcode a schema — each app's own `hibernate.default_schema`
property resolves it to that app's own schema at runtime. Never hardcode a schema on a class shared
across deployables; do that instead per-app via `hibernate.default_schema` (see
`common.entity.User`'s Javadoc for the incident this rule comes
from — a hardcoded schema there silently broke `ecommerce-service`'s isolation for months; the same
bug class was caught and fixed pre-emptively in `task-service`'s, `social-service`'s, and
`content-service`'s own entities, before any of them ever ran against a real database with the new
schema live).

**Sequences:** each table has its own sequence (`TABLE_NAME_SEQ`).

**Audit columns** on all entities: `usrCreation`, `dteCreation`, `usrLastModification`, `dteLastModification`, `version`.

**Migrations — Liquibase:**
- `gateway`'s tables (the embedded monolith's, `product` schema): `gateway/src/main/java/com/ttg/devknowledgeplatform/database/sql/`, master changelog `dev-knowledge-platform.xml`. Its `CATEGORY`/`TAG`/`CONTENT_ITEM`/`CONTENT_ITEM_TAG`/`QUESTION_ANSWER`/`ARTICLE` changesets and its `CONTENT_EMBEDDING`/`CHAT_SESSION`/`CHAT_MESSAGE`/`SYS_PARAM`/`PIPELINE_METRICS` changesets (`DKP-0005`, `DKP-0006`, `DKP-0007`, `DKP-0008`, `DKP-0010`, `DKP-0011`, `DKP-0012`) are now all frozen, already-run history, same status as the old `PROJECT`/`TASK`/friend-graph changesets before them — `gateway`'s Spring context maps no entity to any of these tables anymore, now that `content-service` and `ai-service` are both standalone services with their own schemas/changelogs (below). Don't add new `content-service`/`ai-service` schema changes here anymore.
- Each standalone service migrates its own schema from its own changelog tree instead
  (`ecommerce-service/.../database/sql/ecommerce-service.xml`,
  `identity-service/.../database/sql/identity-service.xml`,
  `task-service/.../database/sql/task-service.xml`,
  `social-service/.../database/sql/social-service.xml`,
  `content-service/.../database/sql/content-service.xml`,
  `ai-service/.../database/sql/ai-service.xml`), applied via its own standalone
  `*-liquibase.yml` compose file at the repo root — never add a new module's tables to `gateway`'s
  changelog once that module is a standalone service. `content-service`'s own changelog (a fresh
  snapshot of the final table shape, not a replay of `gateway`'s incremental history) now has its
  own standalone `content-service-liquibase.yml` compose file too — not yet run against a real
  database in this session, same unverified-at-runtime caveat every standalone extraction has
  carried at this stage. `ai-service`'s own changelog (`DKP-0032`, another fresh snapshot rather
  than a replay of `gateway`'s incremental `CONTENT_EMBEDDING`/`CHAT_SESSION`/etc. history, into the
  new `ai` schema) carries the same caveat via its own `ai-service-liquibase.yml`.
- Naming: `YYYY/VERSION/YYYYMMDDHHMI__VERSION__TICKET__description.sql`
  - Example: `2026/0.0.1/202606170001__0.0.1__DKP-0005__init_ai_tables.sql`

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
