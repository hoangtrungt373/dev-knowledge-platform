# CLAUDE.md — content-service

Module-local guidance for `content-service`. Read alongside the root `CLAUDE.md`.

## What lives here

Categories, tags, and content items (Q&A, articles) — the knowledge corpus surfaced by the RAG
pipeline. Package root: `com.ttg.devknowledgeplatform.content.*`.

- `entity/` — `Category`, `Tag`, `ContentItem`, `ContentItemTag`, `QuestionAnswer`, `Article`.
- `enums/` — `ContentType`, `ContentStatus`, `TagStatus`, `QuestionDifficulty`.
- `repository/` (+ `repository/spec/`) — Spring Data repositories and `Specification`s for the
  entities above.
- `service/` — `CategoryService`, `TagService`, `QuestionAnswerService`, `ArticleService` (+
  `impl/`), `CategoryTreeNode`, `QuestionAnswerCommands`, `ArticleCommands`, `service/seed/`
  (`CategorySeeder`, `TagSeeder`, `QuestionAnswerSeeder` — extend `infra`'s `CsvSeeder<T>` Template
  Method where the row shape fits; it moved out of this module once `social-service` needed it
  too, since the two can't depend on each other).
- `api/` (interfaces, HTTP annotations) + `api/impl/` (controllers, no HTTP annotations) —
  `CategoryApi`/`TagApi`/`ArticleApi`/`QuestionAnswerApi` (admin CRUD), moved in from `gateway`
  (named `api` at the time) so the REST layer for this module's own entities lives alongside them.
  `ai-service`'s `PublicContentApi`/`PublicContentController` (read-only published-content browsing)
  moved to `ai-service` instead, not here — it also spans the content+AI indexing orchestration
  concern, outside this module's own CRUD surface (though it does still reach into this module's
  `ArticleMapper`/`QuestionAnswerMapper`/`content.dto.*`, an already-allowed dependency direction).
  `ArticleController`/`QuestionAnswerController` resolve the authenticated principal's `User` via
  `common`'s `UserRepository` directly (not `identity-service`'s `UserService`) — see Rules below.
- `mapper/` — MapStruct mappers, moved in alongside `api/`: `CategoryMapper`, `TagMapper`,
  `ArticleMapper`, `QuestionAnswerMapper` (entity ↔ `dto/` below).
- `dto/` — REST request/response DTOs for this module's entities: `CategoryResponse`,
  `CreateCategoryRequest`, `UpdateCategoryRequest`, `CategoryTreeNodeResponse`, `TagResponse`,
  `CreateTagRequest`, `UpdateTagRequest`, `ArticleResponse`, `CreateArticleRequest`,
  `UpdateArticleRequest`, `QuestionAnswerResponse`, `CreateQuestionAnswerRequest`,
  `UpdateQuestionAnswerRequest`.
- `event/ContentPublishedEvent` — definition only; no publisher wired up yet (scaffold for a
  future auto-index-on-publish flow). Listened for by `ai-service`'s `ContentPublishedEventListener`
  (moved there from `gateway`, since it just calls that module's own `ContentIndexingService` —
  this module can't host the listener itself, since it must never depend on `ai-service`).
- `exception/ContentErrorCode` — `CATEGORY_*`/`TAG_*`/`QA_*`/`ARTICLE_*` codes, implements
  `common`'s `ErrorCode` interface.

Full detail: `docs/PROJECT_STRUCTURE.md`'s `## content-service` section;
`docs/CHANGELOG.md`'s "Extracted Category/Tag/ContentItem/QuestionAnswer/Article..." entry for the
extraction history and the reasoning behind every decision below.

## Rules specific to this module

- **Depends only on `common` + `infra`. Never add a dependency on `gateway`, `ai-service`, or
  `social-service`** — `ai-service` already depends on *this* module (for `ContentItem`), so a
  dependency back onto `ai-service` would be circular; a dependency on `gateway` would be circular by
  definition (every module `gateway` wires up must not depend back on `gateway`).
- **Services (`CategoryService`/`TagService`/`ArticleService`/`QuestionAnswerService`) still return
  entities, `Page<Entity>`, or a command/record type — never this module's own `dto/` classes.**
  Keep the service layer decoupled from the REST/JSON contract even though both now live in the
  same module: `dto/` classes exist for this module's own `api/` controllers to map to/from, not for services to
  accept or return. Use a command/record type (see `QuestionAnswerCommands`/`ArticleCommands`) for
  multi-field service inputs instead of threading a `Create*Request`/`Update*Request` down into the
  service layer.
- **REST controllers, DTOs, and mappers DO live here now** (`api/`, `api/impl/`, `mapper/`, `dto/`)
  — moved in from `gateway` so this module owns its own HTTP surface. They must still only reach
  `common`/`infra` types plus this module's own `entity`/`service`/`enums` — never a `gateway` or
  `identity-service` class. `ArticleController`/`QuestionAnswerController` need "the authenticated
  user's id" to stamp an author on create; they get it via `common`'s `UserRepository.findByEmail(...)`
  directly rather than `identity-service`'s `UserService.findByEmail(...)` (this module must never
  depend on `identity-service`), since `common` exposes `UserRepository` for exactly this reason.
  `@AuthenticationPrincipal CustomOAuth2User` needs `spring-boot-starter-oauth2-client` on the
  compile classpath (`optional=true`, type support only, same reasoning `common`'s pom documents
  for that dependency) — the actual security filter chain stays in `gateway`'s `SecurityConfig`.
- **Seeders live here, not `gateway`** — they persist directly via repositories, same as production
  service impls, so they belong with the rest of the domain logic. The actual seed data files
  (`data/csv/*.csv`, `data/question-answers/*.md`) stay under `gateway/src/main/resources/` — only the
  seeder *classes* live here. `gateway`'s `DataSeedingRunner` just injects and calls them in order.
- **Liquibase migrations for this module's tables still live under `gateway`'s changelog tree**
  (`gateway/.../database/sql/`) — new feature modules don't get their own changelog folder in this
  repo; don't create one.
- **`CategoryService.validateParentAssignment`-style invariants belong here, not `gateway`** — any
  business rule about these entities (uniqueness, cycle detection, in-use guards before delete)
  goes in this module's service impl, never in the `gateway` controller.
- If a new operation genuinely needs both this module and `ai-service` (e.g. triggering
  re-indexing), that orchestration goes in `gateway` — it's the only module allowed to depend on both.
