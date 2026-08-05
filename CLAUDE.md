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
| `content-service` | Categories, tags, content items (Q&A, articles) — the RAG corpus; own REST/DTO/mapper layer | [`content-service/CLAUDE.md`](content-service/CLAUDE.md) |
| `ai-service` | RAG pipeline (embedding, vector search, LLM generation via LangChain4j), chat + content-indexing REST layer | [`ai-service/CLAUDE.md`](ai-service/CLAUDE.md) |
| `task-service` | Personal task/project management | [`task-service/CLAUDE.md`](task-service/CLAUDE.md) |
| `social-service` | Friend graph + chat (groups/channels, DMs); own REST/DTO/mapper layer | [`social-service/CLAUDE.md`](social-service/CLAUDE.md) |
| `identity-service` | Auth: local + OAuth2/OIDC login, JWT issuance, OTP registration, profile mutation | [`identity-service/CLAUDE.md`](identity-service/CLAUDE.md) |
| `ecommerce-service` | Study-project e-commerce vertical slice: catalog, cart/checkout, orders/inventory, payments, reviews/recommendations. **Standalone Spring Boot app, own database** — not a Maven dependency of `gateway` (see below) | [`ecommerce-service/CLAUDE.md`](ecommerce-service/CLAUDE.md) |
| `gateway` | Cross-module REST orchestration, security/JWT-filter/STOMP transport wiring, Liquibase, Spring Boot entry point | [`gateway/CLAUDE.md`](gateway/CLAUDE.md) |
| `gui` | React 18 + TypeScript + MUI frontend (Vite) | [`gui/CLAUDE.md`](gui/CLAUDE.md) |

Dependency order: `common` ← `infra` ← `content-service` ← `ai-service`;
`infra` ← `identity-service` ← `social-service`; `common` ← `infra` ← `task-service`.
`content-service`/`identity-service`/`task-service` are parallel siblings depending only on
`common`+`infra`; `ai-service` and `social-service` are each allowed a single, real,
one-directional dependency on a sibling (`ai-service` → `content-service` for the
`ContentEmbedding`→`ContentItem` FK; `social-service` → `identity-service` for `UserApi`'s
relationship-enriched profile/search endpoints). `task-service` used to have the same kind of
one-directional dependency on `content-service` (`Task`'s optional `ContentItem` link), but that
link was removed — see `docs/CHANGELOG.md`'s `[Unreleased]` entry. `gateway` depends on these five
feature modules; it's the only module allowed to depend on more than one, reserved for
orchestration that needs two feature modules with **no** dependency relationship possible between
them in either direction — currently nothing qualifies. `gui` is independent (talks to `gateway`
over HTTP only). Full detail, including the full rationale for each module owning its own
REST/DTO/mapper layer instead of centralizing them in one module, lives in
`docs/PROJECT_STRUCTURE.md`.

**`ecommerce-service` is not part of this dependency graph** — it's a deliberately standalone
Spring Boot application (own `EcommerceServiceApplication` entry point, own `ecommerce` Postgres
schema/database, own JWT verification), extracted specifically as a microservices-study exercise
(see the `project-ecommerce-service-module` memory for the full history). It still compiles
against `common`+`infra` as ordinary Maven library dependencies (shared-kernel style — no runtime
call to anything), but `gateway` no longer depends on it in Maven at all; the two run as separate
processes on separate ports. `gateway`-side HTTP proxying to it is not built yet (see
`ecommerce-service/CLAUDE.md`). This also means Epic 5's originally-planned
`ecommerce-service` → `ai-service` Maven dependency (for embedding generation) needs rethinking
once that epic is actually built: `ai-service` itself still only runs inside the monolith today,
so a standalone `ecommerce-service` can't reach it as a normal library dependency the way
`ai-service` → `content-service` works — it would need a real network call (likely through
`gateway`, once proxying exists), not a `pom.xml` entry. Don't add that Maven dependency back
without confirming this is still the intended shape.

When proposing a new **big feature area** (broad scope, likely to grow), default to a dedicated
Maven module mirroring this shape — owns its own entities/services *and* its own REST controllers/
DTOs/mappers (a full vertical slice), depends only on `common`+`infra` (or, if it has a genuine
one-directional data need on an existing sibling, that sibling too) — rather than adding it into
`gateway` directly. See `content-service`'s and `social-service`'s `CLAUDE.md` for the pattern to copy.

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

Content publishing → RAG ingestion spans three modules: `content-service` defines `ContentItem` and
(currently unwired) `ContentPublishedEvent`; `ai-service` owns the orchestration
(`ContentIndexingService`) that calls into its own `ContentIngestionService` to chunk +
embed + store `ContentEmbedding` rows. Async — does not block the API response. Today, indexing is
admin-triggered (`ai-service`'s `IngestionController`), not automatic on publish — see
`content-service/CLAUDE.md` for why the event has no publisher wired up yet.

### Security

Keycloak is the identity provider (hosted login page, Authorization Code + PKCE; Google/Facebook brokered inside Keycloak itself). `gateway` is a pure OAuth2 resource server — it only ever verifies bearer tokens against Keycloak's JWKS (`spring.security.oauth2.resourceserver.jwt.issuer-uri`), never issues them. `KeycloakJwtAuthenticationConverter` (`gateway`) JIT-provisions/refreshes the local `User` row from the token's claims via `identity-service`'s `UserService.findOrCreateFromKeycloak`, so `@CurrentUserId`/`@AuthenticationPrincipal CustomOAuth2User` keep resolving to the same local numeric PK every other module's FKs/queries depend on. Role-based access via `UserRole` enum, sourced from the token's `realm_access.roles` claim. This is a multi-phase migration in progress — see `docs/CHANGELOG.md`'s `[Unreleased]` entries for what's landed vs. still pending (`ecommerce-service`'s equivalent switch, the `gui` rework). Current-user resolution patterns: `gateway/CLAUDE.md`.

## Database Conventions

**Schema:** all tables in the `product` schema.

**Sequences:** each table has its own sequence (`TABLE_NAME_SEQ`).

**Audit columns** on all entities: `usrCreation`, `dteCreation`, `usrLastModification`, `dteLastModification`, `version`.

**Migrations — Liquibase:**
- Location: `gateway/src/main/java/com/ttg/devknowledgeplatform/database/sql/`
- Master changelog: `dev-knowledge-platform.xml`
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
