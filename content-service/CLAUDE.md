# CLAUDE.md — content-service

Module-local guidance for `content-service`. Read alongside the root `CLAUDE.md`.

## What lives here

Categories, tags, and content items (Q&A, articles) — the knowledge corpus surfaced by the RAG
pipeline. Package root: `com.ttg.devknowledgeplatform.content.*`.

**Now a standalone Spring Boot application, not part of the monolith** — the sixth module pulled
out (fifth of the microservices-study extractions with a full plan, following the
`ecommerce-service`/`identity-service`/`task-service`/`social-service` precedent), and the hardest
so far: `ai-service`'s coupling to it was real, deep, and bidirectional (direct repository
injection, a real `ContentEmbedding`→`ContentItem` FK, a write-back on
`ContentItem.qualityScore`), not a removable leftover. See the
`project-microservices-extraction-plan` memory for the full 9-step extraction history. Concretely:
its own `ContentServiceApplication` entry point, its own `content` Postgres schema (same
`dev-premier` database, not a separate instance — per-service-per-schema, see root `CLAUDE.md`'s
Database Conventions), its own port (`8085`), and its own Liquibase changelog. `ai-service`'s
Maven dependency on this module was removed once its indexing pipeline switched to calling this
module's own `/internal/content-items/**` API over HTTP instead of injecting repositories
directly, and `PublicContentApi`/`PublicContentController` moved back here from `ai-service`
outright — see `ai-service/CLAUDE.md`. Its own `Dockerfile` and `docker-compose.apps.yml` wiring
(the consolidated `services-liquibase` job, not a dedicated `content-service-liquibase.yml` — this
module has no standalone single-service compose file of its own) are in place now (port `8085`);
`gateway`-side HTTP proxying for end-user traffic to this service is not built yet — `ai-service`'s
own `ContentServiceClient` (server-to-server, against `/internal/content-items/**`) is the one real
inter-service call that exists today.

- `ContentServiceApplication` — `@SpringBootApplication` + `@ComponentScan(basePackages =
  {"...content", "...infra"})` + `@ConfigurationPropertiesScan` entry point (the
  `@ConfigurationPropertiesScan` is required here — this module no longer rides on `gateway`'s).
  The explicit `@ComponentScan` was added reactor-wide once an audit found no standalone service
  actually reached `infra`'s sibling package by default — here, this module injects
  `infra.service.SlugService` and its seeders extend `infra.service.seed.CsvSeeder`; see
  `infra/CLAUDE.md`'s `JacksonConfig` note for the full reactor-wide finding. **No
  `@EntityScan`/`@EnableJpaRepositories`** — this module doesn't touch `common.entity.User`/
  `common.repository.UserRepository` at all (see the "No local `User` copy" rule below). Default
  scanning already covers this module's own `entity`/`repository` packages.
- `security/` — this app's own filter chain, independent of `gateway`'s, since it now runs on its
  own port and must guard its own endpoints regardless of whether `gateway` is proxying to it
  (mirrors `identity-service`'s/`ecommerce-service`'s/`task-service`'s `security/`).
  `SecurityConfig` mirrors the three-way rule set `gateway`'s own `SecurityConfig` used to apply to
  these same paths before extraction: `/api/v1/public/**` permits all (read-only published-content
  browsing), `/internal/**` permits all too (see `InternalApiKeyFilter` below — it, not Spring
  Security, is what actually enforces that path), `/actuator/**` permits all, `/api/v1/admin/**`
  requires `ROLE_ADMIN`, everything else requires authentication. **No
  `security/KeycloakRealmRoleConverter`/`KeycloakJwtAuthenticationConverter` classes of this
  module's own anymore** — both moved to `infra.security` as shared beans (see `infra/CLAUDE.md`),
  picked up via this module's existing `@ComponentScan` reaching `infra`.
  `infra.security.KeycloakJwtAuthenticationConverter` builds the `CustomOAuth2User` principal
  directly from the verified JWT's claims — no local `User` row at all, mirroring
  `ecommerce-service`'s/`task-service`'s converter rather than `gateway`'s/`identity-service`'s
  (see the "No local `User` copy" rule below). **No local `CurrentUserResolver` anymore** — this
  module uses the shared `infra.security.CurrentUserResolver.resolveUserUuid(...)` instead (see
  `infra/CLAUDE.md`), picked up via this module's existing `@ComponentScan` reaching `infra`.
  `config/security/InternalApiKeyFilter` + `config/InternalApiProperties` are a separate,
  server-to-server concern (see below), not part of this end-user JWT chain.
- `config/web/` — `WebMvcConfig` (registers `CurrentUserIdArgumentResolver`) and
  `CurrentUserIdArgumentResolver` (resolves `common.annotation.CurrentUserId String`-annotated
  controller parameters via `infra.security.CurrentUserResolver`, assigning the shared
  `resolveUserUuid` result to this module's own `authorUuid` vocabulary); no STOMP transport here,
  so no message-argument-resolver counterpart is needed.
- `entity/` — `Category`, `Tag`, `ContentItem`, `ContentItemTag`, `QuestionAnswer`, `Article`. None
  of these hardcode `@Table(schema = "product")` anymore — same bug class as `common.entity.User`'s
  incident (an explicit `@Table(schema=...)` always wins over `hibernate.default_schema`) — so they
  resolve via this app's own `hibernate.default_schema: content`. `ContentItem.authorUuid` is a
  plain `String` column (the Keycloak JWT's `sub` claim), never a `@ManyToOne` FK onto a `User` row
  — see the "No local `User` copy" rule below.
- `enums/` — `TagStatus` (only enum still local to this module — no consumer outside it;
  `ContentType`/`ContentStatus`/`QuestionDifficulty` moved to `common`'s `enums/` package, since
  `ai-service` uses all three on its own public REST contracts, not just as plumbing here).
- `repository/` (+ `repository/spec/`) — Spring Data repositories and `Specification`s for the
  entities above.
- `service/` — `CategoryService`, `TagService`, `QuestionAnswerService`, `ArticleService` (+
  `impl/`), `CategoryTreeNode`, `QuestionAnswerCommands`, `ArticleCommands`, `service/seed/`
  (`CategorySeeder`, `TagSeeder`, `QuestionAnswerSeeder` — extend `infra`'s `CsvSeeder<T>` Template
  Method where the row shape fits; `DataSeedingRunner` — this module's own, orchestrating those
  three seeders in dependency order, moved here from `gateway`'s once that app could no longer
  inject them).
- `api/` (interfaces, HTTP annotations) + `api/impl/` (controllers, no HTTP annotations) —
  `CategoryApi`/`TagApi`/`ArticleApi`/`QuestionAnswerApi` (admin CRUD), moved in from `gateway`
  (named `api` at the time) so the REST layer for this module's own entities lives alongside them.
  `PublicContentApi`/`PublicContentController` (`/api/v1/public/**`, read-only published-content
  browsing) moved back here from `ai-service` — it only ever fronted this module's own
  `ArticleService`/`QuestionAnswerService`/`ArticleMapper`/`QuestionAnswerMapper`/
  `ContentItemRepository`, never anything ai-service-specific, so keeping it in `ai-service` was
  drift left over from before this module owned its own REST layer at all. `ArticleController`/
  `QuestionAnswerController` take `@CurrentUserId String authorUuid` directly (not
  `@AuthenticationPrincipal CustomOAuth2User`) — see the "No local `User` copy" rule below.
- `mapper/` — MapStruct mappers, moved in alongside `api/`: `CategoryMapper`, `TagMapper`,
  `ArticleMapper`, `QuestionAnswerMapper` (entity ↔ `dto/` below).
- `dto/` — REST request/response DTOs for this module's entities: `CategoryResponse`,
  `CreateCategoryRequest`, `UpdateCategoryRequest`, `CategoryTreeNodeResponse`, `TagResponse`,
  `CreateTagRequest`, `UpdateTagRequest`, `ArticleResponse`, `CreateArticleRequest`,
  `UpdateArticleRequest`, `QuestionAnswerResponse`, `CreateQuestionAnswerRequest`,
  `UpdateQuestionAnswerRequest`.
- `event/ContentPublishedEvent` — definition only; no publisher wired up yet (scaffold for a
  future auto-index-on-publish flow). `ai-service` used to carry a listener for it
  (`ContentPublishedEventListener`, moved there from `gateway`), but it was deleted as dead code
  during this module's extraction — it had never fired (no publisher exists) and an in-process
  Spring event can't cross a service boundary now that `ai-service` and this module are separate
  deployables. If auto-index-on-publish is ever built, it will need a real mechanism that works
  across services (e.g. `ai-service` polling/webhook, or a message broker), not a resurrected
  in-process listener.
- `exception/ContentErrorCode` — `CATEGORY_*`/`TAG_*`/`QA_*`/`ARTICLE_*`/`CONTENT_ITEM_*` codes,
  implements `common`'s `ErrorCode` interface.
- `api/InternalContentApi` + `api/impl/InternalContentController` (`/internal/content-items/**`) +
  `service/InternalContentService`/`Impl` + `mapper/InternalContentItemMapper` +
  `dto/internal/{InternalContentItemResponse,UpdateQualityScoreRequest}` +
  `repository/spec/ContentItemSpecification` — the server-to-server indexing API `ai-service` calls
  over HTTP. `ai-service`'s `ContentIndexingServiceImpl`/`EmbeddingIndexServiceImpl` call this API
  via their own `ContentServiceClient`/`ContentItemDto` instead of injecting this module's
  repositories directly — see `ai-service/CLAUDE.md`. `InternalContentItemResponse` is deliberately
  a different shape from `ArticleResponse`/`QuestionAnswerResponse` (this module's own admin REST
  DTOs): it flattens `ContentItem` + whichever of `QuestionAnswer`/`Article` applies into one
  object, and carries `categoryName`/`tagNames` (not just ids) because `ai-service`'s
  `ContentEmbeddingMetadata` needs the human-readable names and can no longer dereference
  `Category`/`Tag` itself over a live JPA association.
  `config/InternalApiProperties` (`app.internal-api.key`) + `config/security/InternalApiKeyFilter`
  gate every `/internal/**` request behind a shared-secret header (`X-Internal-Api-Key`) instead of
  an end-user JWT, since `ai-service` has no end-user principal to attach to a server-to-server
  indexing call — see that filter's Javadoc for the alternative considered (OAuth2 client-credentials
  against Keycloak) and why it was deferred. This module's own `SecurityConfig` marks `/internal/**`
  `permitAll()` so Spring Security's own filter chain lets the request through unauthenticated
  before `InternalApiKeyFilter` enforces the key.

Full detail: `docs/PROJECT_STRUCTURE.md`'s `## content-service` section;
`docs/CHANGELOG.md`'s `[Unreleased]` entries for the extraction history and the reasoning behind
every decision below.

## Rules specific to this module

- **Standalone app now — this module is not part of the monolith's Maven/Spring graph.** It
  compiles against `common`+`infra` as ordinary library dependencies (shared-kernel style, no
  runtime call) but has **no Maven dependency on any other feature module, and `gateway`/`ai-service`
  have none on it.** Never re-add a `gateway`/`ai-service` → `content-service` Maven dependency —
  that would put this module's beans back on that app's classpath and cause both apps to run them
  simultaneously (see root `CLAUDE.md`'s note on the `SecurityConfig`-conflict risk this caused
  mid-extraction). Cross-service communication happens over HTTP (`ai-service`'s
  `ContentServiceClient` against this module's `InternalContentApi`) or, for `gateway`, once a
  proxy layer is built — never a compile-time dependency again.
- **No local `User` copy — `ContentItem.authorUuid` is a plain column, never a `@ManyToOne User`
  foreign key.** An earlier revision of this module resolved the authenticated author via
  `common`'s `UserRepository.findByEmail(...).map(User::getId)`, stamping a plain `Integer
  authorId` — reverted once this module was extracted, since that lookup only ever needs "who is
  the caller," never another user's profile data, and `authorId`/`authorUuid` is write-once at
  creation, never read back or joined through anywhere in this module.
  `KeycloakJwtAuthenticationConverter` builds `CustomOAuth2User` straight from the verified JWT's
  claims (`sub` → `userUuid`), same as `ecommerce-service`'s/`task-service`'s converter, and
  `infra.security.CurrentUserResolver`/`config.web.CurrentUserIdArgumentResolver` read that UUID directly
  with zero database access — see the `project-microservices-extraction-plan` memory's "Option C"
  discussion (written for `ecommerce-service`, equally applicable here). If a future feature needs
  to *display* another user's profile info (e.g. an author byline), reach for an event-driven
  read-model projection ("Option B") rather than resurrecting a persisted `User` copy.
- **Services (`CategoryService`/`TagService`/`ArticleService`/`QuestionAnswerService`) still return
  entities, `Page<Entity>`, or a command/record type — never this module's own `dto/` classes.**
  Keep the service layer decoupled from the REST/JSON contract even though both now live in the
  same module: `dto/` classes exist for this module's own `api/` controllers to map to/from, not for services to
  accept or return. Use a command/record type (see `QuestionAnswerCommands`/`ArticleCommands`) for
  multi-field service inputs instead of threading a `Create*Request`/`Update*Request` down into the
  service layer.
- **REST controllers, DTOs, and mappers live here** (`api/`, `api/impl/`, `mapper/`, `dto/`) — this
  module owns its full HTTP surface. They must still only reach `common`/`infra` types plus this
  module's own `entity`/`service`/`enums` — never a `gateway`, `ai-service`, or `identity-service`
  class.
- **Seeders and their data files both live here now** — the CSV/Markdown seed files
  (`data/csv/categories.csv`, `data/csv/tags.csv`, `data/question-answers/*.md`) moved from
  `gateway/src/main/resources/` into this module's own `src/main/resources/` alongside the seeder
  classes, since `gateway`'s classpath no longer includes this module's jar. `gateway`'s own
  `DataSeedingRunner` was narrowed to `UserSeeder` only as a result.
- **Liquibase migrations for this module's tables live in this module's own changelog tree now**
  (`database/sql/content-service.xml` + `2026/0.0.2/DKP-0031__add_content_service_tables.sql`), the
  opposite of every embedded feature module (which still migrate via `gateway`'s changelog tree per
  root `CLAUDE.md`'s Database Conventions). It's a fresh snapshot of the final table shape in the
  new `content` schema, not a replay of `gateway`'s incremental history — same convention
  `task-service`'s `DKP-0028` and `social-service`'s `DKP-0029`/`DKP-0030` already followed.
  `DKP-0031` also carries `AUTHOR_UUID` directly (not `AUTHOR_ID`) since it was edited in place
  before ever running against a real database, mirroring `task-service`'s own
  `ownerId`→`ownerUuid` correction. Applied via the consolidated `services-liquibase` job in
  `docker-compose.apps.yml` (`docker compose -f docker-compose.infra.yml -f
  docker-compose.apps.yml run --rm services-liquibase`) — this module has no standalone
  `content-service-liquibase.yml` file of its own, unlike `task-service`/`social-service`; not yet
  run against a real database in this session, same unverified-at-runtime caveat every standalone
  extraction in this repo has carried at this stage. Don't move future migrations back under
  `gateway`'s tree; this module owns its own schema lifecycle now.
- **`CategoryService.validateParentAssignment`-style invariants belong here, not `gateway`** — any
  business rule about these entities (uniqueness, cycle detection, in-use guards before delete)
  goes in this module's service impl, never in a `gateway` controller (which no longer exists for
  this module's entities anyway).
- If a new operation genuinely needs both this module and `ai-service`, that orchestration goes in
  `gateway` (the only module that could depend on both, once a proxy layer exists) or is built as a
  real HTTP call from whichever side initiates it — never a resurrected Maven dependency between
  the two feature modules themselves.
