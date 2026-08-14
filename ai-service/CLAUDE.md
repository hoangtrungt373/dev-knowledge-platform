# CLAUDE.md — ai-service

Module-local guidance for `ai-service`. Read alongside the root `CLAUDE.md`.

## What lives here

RAG pipeline: embedding, vector search, LLM generation (LangChain4j). Package root:
`com.ttg.devknowledgeplatform.ai.*`. Full class-by-class detail lives in
`docs/PROJECT_STRUCTURE.md`'s `## ai-service` section — this file covers conventions and config,
not a restated file listing.

**Now a standalone Spring Boot application, not part of the monolith** — the sixth and final module
pulled out (following the `ecommerce-service`/`identity-service`/`task-service`/`social-service`/
`content-service` precedent — see the `project-microservices-extraction-plan` memory for the full
history of all six). `gateway` has **zero** embedded feature modules remaining after this — this was
the last one. Concretely: its own `AiServiceApplication` entry point
(`@ConfigurationPropertiesScan` + `@EnableAsync`), its own `ai` Postgres schema (same `dev-premier`
database, not a separate instance — per-service-per-schema, see root `CLAUDE.md`'s Database
Conventions), its own port (`8086`), and its own Liquibase changelog. This module already had no
Maven dependency on `content-service` going into this extraction (see the Rules section below); the
work here was giving it its own app shell/security/schema and severing `gateway`'s Maven dependency
on it — not another HTTP rewrite. Its own `Dockerfile` and
`dev-knowledge-platform-apps-docker-compose.yml`/`ai-service-liquibase.yml` wiring are in place now
(port `8086`); `gateway`-side HTTP proxying for end-user traffic to this service is not built yet,
same as every other standalone service in this reactor.

- `AiServiceApplication` — `@SpringBootApplication` + `@ComponentScan(basePackages = {"...ai",
  "...infra"})` + `@ConfigurationPropertiesScan` (required here — this module no longer rides on
  `gateway`'s scan) + `@EnableAsync` (drives `PipelineCompletedEventListener`'s `@EventHandler`
  dispatch and the SSE/MVC async support this module's own `ChatMvcConfig` configures). The explicit
  `@ComponentScan` was added reactor-wide once an audit found no standalone service actually reached
  `infra`'s sibling package by default — here, `PipelineCompletedEventListener` extending `infra`'s
  `AsyncEventHandler` needs `infra`'s own `AsyncEventThreadPoolConfig` bean to exist in this context;
  see `infra/CLAUDE.md`'s `JacksonConfig` note for the full reactor-wide finding. **No
  `@EntityScan`/`@EnableJpaRepositories`** — this module doesn't touch `common.entity.User`/
  `common.repository.UserRepository` at all, same shape as
  `content-service`'s/`task-service`'s/`ecommerce-service`'s application classes. Default scanning
  already covers this module's own `entity`/`repository` packages.
- `security/` — this app's own filter chain, independent of `gateway`'s, since it now runs on its
  own port and must guard its own endpoints regardless of whether `gateway` is proxying to it
  (mirrors `content-service`'s/`task-service`'s/`ecommerce-service`'s `security/` exactly).
  `SecurityConfig` — `@EnableWebSecurity` + `@EnableMethodSecurity` (this module's own copy now
  declares it, not `gateway`'s — needed here because `IngestionApi` is the only thing in the whole
  reactor with a `@PreAuthorize("hasRole('ADMIN')")` method); rules: `/actuator/**` permits all,
  `/api/v1/admin/**` requires `ROLE_ADMIN`, everything else requires authentication. **No
  `security/KeycloakRealmRoleConverter`/`KeycloakJwtAuthenticationConverter` classes of this
  module's own anymore** — both moved to `infra.security` as shared beans (see `infra/CLAUDE.md`),
  picked up via this module's existing `@ComponentScan` reaching `infra`.
  `infra.security.KeycloakJwtAuthenticationConverter` builds the `CustomOAuth2User` principal
  directly from the verified JWT's claims (`sub` → `userUuid`) — no local `User` row at all, mirroring
  `content-service`'s/`task-service`'s converter rather than `gateway`'s/`identity-service`'s (see
  the Rules section below for why `ChatSession`/`PipelineMetrics` don't need one either).
  **No local `CurrentUserResolver` anymore** — this module uses the shared
  `infra.security.CurrentUserResolver.resolveUserUuid(...)` instead (see `infra/CLAUDE.md`), picked
  up via this module's existing `@ComponentScan` reaching `infra`. **No local
  `JsonAuthenticationEntryPoint` class anymore either** — also moved to `infra.security` as a
  shared bean (byte-identical to `gateway`'s own former copy, the only other service that wired
  one up — see `infra/CLAUDE.md`). **No `CorsConfig` here anymore, and
  `SecurityConfig` dropped its `.cors(...)` wiring entirely too** — this module's own `CorsConfig`
  used to survive `gateway`'s first CORS-consolidation pass, narrowed to just
  `/api/v1/chat/stream` (the SSE streaming chat response), since Spring Cloud Gateway Server MVC
  can't safely proxy Server-Sent Events. Deleted outright once `gateway`'s own
  `routing/ChatStreamProxyController` landed — a purpose-built streaming proxy that relays that
  one path by hand instead, so the GUI never calls this module directly for anything anymore.
  Every request this module receives now comes from another server (`gateway`, via a plain JDK
  `HttpClient`), never a browser directly — CORS is a browser-enforced mechanism a
  server-to-server call never triggers, so there's nothing left for a `CorsConfigurationSource`
  bean to do here. See `gateway/CLAUDE.md`'s `routing/` bullet and root `CLAUDE.md`'s
  Architecture → Routing section. Don't add a `CorsConfig` back here on the assumption some
  endpoint still needs one — re-verify with a grep for real browser-origin callers first.
- `config/web/CurrentUserIdArgumentResolver` (`@Component`, resolves
  `common.annotation.CurrentUserId String`-annotated controller parameters via
  `infra.security.CurrentUserResolver`, assigning the shared `resolveUserUuid` result to this
  module's own `userUuid` vocabulary); no STOMP transport here, so no message-argument-resolver
  counterpart is needed.
  Registered via the existing `ChatMvcConfig`'s `addArgumentResolvers` (alongside its pre-existing
  `addInterceptors` registration of `ChatRateLimitInterceptor` — see the Config classes table below).

`exception/AiErrorCode` — `AI_*` codes (`AI_SERVICE_UNAVAILABLE`/`AI_EMBEDDING_FAILED`/
`AI_MODEL_UNSUPPORTED`), implements `common`'s `ErrorCode` interface, same pattern as
`content-service`'s `ContentErrorCode`. `exception/ChatErrorCode` — `CHAT_*` codes owned by
`ChatSessionServiceImpl` (moved here with the rest of the chat feature — see below); same pattern.

### REST layer (`api/`, `api/impl/`) — moved in from `gateway` (named `api` at the time)

This module now owns its own REST controllers, not just the pipeline behind them:

- `api/ChatApi` + `api/impl/ChatController` — the RAG chat endpoint (`/api/v1/chat`), SSE
  streaming, and session history/listing.
- `api/IngestionApi` + `api/impl/IngestionController` — admin manual (re)index / bulk-index /
  corpus-refresh endpoints (`/api/v1/admin/indexing`).
- `api/EmbeddingIndexApi` + `api/impl/EmbeddingIndexController` — admin embedding-index listing
  (`/api/v1/admin/embeddings`).
- `api/PipelineMetricsApi` + `api/impl/PipelineMetricsController` — admin cost/latency monitoring
  (`/api/v1/admin/pipeline-metrics`).
**`api/PublicContentApi`/`api/impl/PublicContentController` used to live here** (read-only
published-content browsing, `/api/v1/public/**`), fronting `content-service`'s own
`ArticleService`/`QuestionAnswerService` directly. Moved back into `content-service` as step 5 of
that module's extraction (see root `CLAUDE.md`'s Long-term direction section) — it never actually
needed anything ai-service-specific, so keeping it here was drift left over from before
`content-service` owned its own REST layer at all. This move (plus step 4's HTTP rewrite of the
indexing pipeline, below) eliminated this module's Maven dependency on `content-service` entirely.

The content+AI indexing orchestration layer moved alongside its controllers:
`service/ContentIndexingService`/`Impl`, `service/IndexingQualityService`/`Impl`,
`service/QualityVerdict`, `service/EmbeddingIndexService`/`Impl`, `dto/admin/EmbeddingIndexItemResponse`.
**`event/ContentPublishedEventListener` used to live here too** (listened for `content-service`'s
`ContentPublishedEvent`, called this module's own `ContentIndexingService.index(...)`) but was
deleted as dead code in the same pass that moved `PublicContentApi` back — the event it listened
for has never had a publisher wired up, and an in-process Spring event couldn't cross a service
boundary once `content-service` is actually standalone anyway.
**Why the indexing orchestration itself is no longer a `gateway`-only concern:** the old rule
("only `gateway` can depend on two feature modules, so cross-module orchestration lives there") was
a tiebreaker for orchestration that genuinely needed both `content-service` and `ai-service` from a
module that couldn't otherwise see both. Back when `ai-service` still depended on `content-service`
(real FK coupling via `ContentEmbedding`/`ContentItem`), the tiebreaker never applied: `ai-service`
was always the more specific module able to see both without `gateway`'s help. Leaving it in
`gateway` was drift, not a real constraint. That FK coupling is gone now (see the Rules section
below), but the orchestration stays here regardless — it's `ai-service`'s own indexing pipeline,
just reaching `content-service` over HTTP instead of a live entity.

The self-contained chat-session feature also moved in full: `service/ChatSessionService`/`Impl`,
`repository/ChatSessionRepository`/`ChatMessageRepository`, `exception/ChatErrorCode`,
`dto/chat/{ChatRequest,ChatResponse,ChatSessionHistoryDto,ChatSessionSummaryDto}`,
`config/chat/ChatSessionProperties` (`app.chat.session.*` — session TTL and rolling-summarisation
tuning; picked up automatically by `@ConfigurationPropertiesScan` on this module's own
`AiServiceApplication`, same as every other `@ConfigurationProperties` bean in this module). It had
no consumers outside the chat feature, so the move was pure relocation with no API surface change.

A later audit of every class in `common` (grepping real imports across the whole reactor, not
Javadoc mentions) found several more that had drifted there without ever gaining a second real
consumer — all moved here as a result: `entity/{ChatSession,ChatMessage,SysParam}`,
`enums/{ChatMessageRole,ChatProvider,ParamKey}`, `dto/{ConversationContext,ConversationTurn}`,
`repository/SysParamRepository`, `service/SysParamService`(`Impl`). `ChatSession`/`ChatMessage`'s
repositories already lived here (see above) — only the entities themselves got left behind in
`common`. `SysParamService`/`SysParamRepository` were originally placed in `common` on the
assumption a second module would need the generic key-value store; none ever did — its only two
callers, `PromptGuardStage` (prompt-injection prototype embedding cache) and
`CorpusStatisticsServiceImpl` (corpus centroid cache), were always both in this module. Pure
relocation, no logic change; `common`'s `GlobalExceptionHandler`/`ErrorResponse`/
`RateLimitExceededException` were considered for the same audit and correctly stayed — see
`common/CLAUDE.md`.

`config/sse/SseStreamTemplate` + `SseEmitterWriter` — the reusable SSE-endpoint helper `ChatController`
streams through, moved here too since it only exists to serve this module's own controllers.
`SseStreamTemplate.SSE_TIMEOUT_MS` is now the single source of truth for the 60s SSE/async timeout;
this module's own `config/web/ChatMvcConfig.configureAsyncSupport` reads it locally now — no
cross-module reference at all anymore. (This used to read "`gateway`'s `WebMvcConfig` reads it from
here" — `gateway`'s `WebMvcConfig` was deleted outright once this module went standalone; there is
no other consumer of this constant left to reference.)

`config/chat/{ChatRateLimiter,RateLimitProperties}` (`app.ai.rate-limit`) and
`config/web/{ChatRateLimitInterceptor,ChatMvcConfig}` moved in from `gateway` too, alongside
`ChatController` — see the Config classes table below.

### RAG pipeline (Pipes-and-Filters, `pipeline/`)

`RagPipelineRunner` runs an ordered list of `RagPipelineStage`s over a `RagPipelineContext`,
stopping on the first abort:

```
PromptGuardStage → ContextualizationStage → EmbeddingStage → QueryAnomalyStage → RetrievalStage
  → ScoringStage → RetrievalAnomalyStage → RetrievedContentGuardStage → MmrStage
  → EvidenceQualityStage → MessageBuildingStage
```

- `PromptGuardStage` — user-input injection guard (length + lexical + semantic similarity),
  runs before any LLM call.
- `RetrievalStage` — pgvector ANN search (HNSW `<=>`), always oversamples `topK × oversampleFactor`.
- `ScoringStage` / `RetrievalAnomalyStage` — filter + prune before `MmrStage` picks the final topK
  (diversity via Maximal Marginal Relevance).
- `EvidenceQualityStage` — post-MMR hallucination guard (mean score + min chunk count).
- `DeduplicationStage` exists but is **not** in the active pipeline — retained for reference only,
  don't wire it back in without checking why it was pulled (see its own Javadoc).
- Adding a new stage: implement `RagPipelineStage` (a `@FunctionalInterface`), insert it into
  `RagQueryServiceImpl`'s stage list in the right position, and add a corresponding config class if
  it needs tunable thresholds (see the config table below).

### Config classes (`config/`, all `@ConfigurationProperties` under `app.ai.*`)

No single flat `app.ai.embedding` block — settings are split by concern:

| Class | Prefix | Purpose |
|---|---|---|
| `ModelConfig` | `app.ai.embedding-model` | Embedding provider (OpenAI only): API key, model, dimensions, retries |
| `ChatModelsConfig` | `app.ai.chat-models` | Selectable chat models (OpenAI + Anthropic): default model id, per-profile settings |
| `RetrievalConfig` | `app.ai.retrieval` | top-k, similarity-threshold, oversample-factor, MMR lambda, outlier-gap-threshold |
| `GuardConfig` | `app.ai.guards` | Anomaly / evidence-quality / answer-drift / prompt-injection thresholds |
| `IndexingConfig` | `app.ai.indexing` | Chunk size/overlap, centroid refresh interval, coherence threshold |
| `LabelsConfig` | `app.ai.labels` | Prompt label strings for conversation summarisation |
| `MonitoringConfig` | `app.ai.monitoring` | Slow-request / high-cost alert thresholds |
| `PricingConfig` | `app.ai.pricing` | Per-token USD rates used to estimate request cost |
| `OkHttpProperties` | `app.ai.okhttp` | HTTP client timeout for OpenAI/Anthropic calls |

`RateLimitProperties` (`app.ai.rate-limit`) lives in `config/chat/` alongside `ChatRateLimiter` —
moved in from `gateway` once it became clear rate limiting only ever protected this module's own
chat endpoint. `ChatMvcConfig` (`config/web/`) registers both `ChatRateLimitInterceptor` for
`/api/v1/chat/**` (`addInterceptors`) and this module's own `CurrentUserIdArgumentResolver`
(`addArgumentResolvers`, added during this module's standalone extraction — see the top of this
file) — Spring composes every `WebMvcConfigurer` in the context automatically, so this module
doesn't need any `gateway`-hosted `WebMvcConfig` to register either on its behalf; `gateway` has no
`WebMvcConfig` of its own left at all now that it has zero embedded feature modules.

```yaml
app:
  ai:
    embedding-model:
      api-key: ${OPENAI_API_KEY}
      model: text-embedding-3-small
      dimensions: 1536
      max-retries: 3
    chat-models:
      default-model: gpt-5.4-mini
      profiles:
        - id: gpt-5.4-mini
          provider: OPENAI
          api-key: ${OPENAI_API_KEY}
        - id: claude-sonnet-5
          provider: ANTHROPIC
          api-key: ${ANTHROPIC_API_KEY}
    retrieval:
      top-k: 5
      similarity-threshold: 0.75
```

`AiServiceConfig` wires `Map<String, ChatLanguageModel>` / `Map<String, StreamingChatLanguageModel>`
beans, one per `chat-models.profiles` entry; `ChatModelResolver` picks the entry matching
`ChatRequest.chatModel` per request, falling back to `chat-models.default-model`, and throws
`BusinessException(AiErrorCode.AI_MODEL_UNSUPPORTED)` for an unconfigured id.

### Async

`sseStreamExecutor` (`config/thread/ThreadPoolConfig`/`ThreadPoolProperties`, `app.threads.sse-executor.*`,
10 core / 50 max / queue 100) feeds SSE streaming and MVC async dispatch; `@EventHandler`-annotated
listeners (e.g. `PipelineCompletedEventListener`) run on the separate `asyncEventExecutor` pool
instead — configured in `infra` itself (its own `AsyncEventThreadPoolConfig`), see `infra/CLAUDE.md`.
Don't assume both event dispatch and SSE streaming share one pool; they're deliberately separate
bulkheads. `sseStreamExecutor`'s bean was moved here **verbatim** from `gateway`'s now-deleted
`config/thread/ThreadPoolConfig`/`ThreadPoolProperties` as part of this module's standalone
extraction — `ChatController`/`SseStreamTemplate` already lived here, so the executor they run on now
does too; no behavioral change, injected by type/qualifier the same way it always was.

## Rules specific to this module

- **No longer depends on `content-service` at all — just `common` + `infra` now.** As of the
  content-service extraction's steps 4 and 5 (see root `CLAUDE.md`'s Long-term direction section
  and `content-service/CLAUDE.md`), `ContentEmbedding` carries a plain `contentItemId` column (not
  a `@ManyToOne` FK), `ContentIngestionService.ingest(...)` takes
  `Integer contentItemId, ContentType sourceType` instead of a live `ContentItem`, and
  `PublicContentApi`/`PublicContentController` moved back into `content-service` outright (they
  only ever fronted that module's own `ArticleService`/`QuestionAnswerService`, never anything
  ai-service-specific). `event/ContentPublishedEventListener` was deleted as dead code in the same
  pass — the event it listened for (`content-service`'s `ContentPublishedEvent`) has never had a
  publisher wired up, and an in-process Spring event couldn't have crossed a service boundary once
  `content-service` is actually standalone anyway. `ContentIndexingServiceImpl`/
  `EmbeddingIndexServiceImpl` now read/write content-item data exclusively through
  `service/ContentServiceClient` (a `RestClient` call to `content-service`'s
  `/internal/content-items/**` API, configured via `config/ContentServiceClientProperties`/
  `app.content-service.*` — `base-url` points at `content-service`'s own genuinely separate
  host:port (`http://localhost:8085` locally, `http://content-service:8085` in the apps compose file)
  now that both modules are standalone processes; the shared `X-Internal-Api-Key` header must
  match `content-service`'s own `app.internal-api.key`). `dto/client/ContentItemDto` is this
  module's own duplicated copy of that API's response shape — same "duplicate, don't share
  cross-service DTOs" convention every `KeycloakJwtAuthenticationConverter` duplicate in this
  codebase already follows.
  - **`service/impl/TraceparentClientHttpRequestInterceptor`** — registered on
    `ContentServiceClientImpl`'s `RestClient.Builder` via `.requestInterceptor(...)`, stamps a
    `traceparent` header on every outgoing call, derived from whatever `infra`'s
    `tracing.TraceContextFilter` bound to this thread's MDC (`MdcKeys.TRACE_ID`/`SPAN_ID`) when
    this app received whatever request led to this call. **Known, deliberate gap:** when this call
    happens on the background thread driving this module's own async indexing pipeline (see the
    "Async" note above), MDC is typically empty — `@Async`'s default executor doesn't copy the
    triggering thread's MDC onto the worker thread — so the interceptor falls back to a fresh,
    disconnected trace rather than one linked to whatever admin request kicked off indexing. Fixing
    that means wiring an MDC-propagating `TaskDecorator` onto this module's own async executor — a
    separate piece of work, not solved here; see the interceptor's own Javadoc.
  **Never add a Maven dependency on `gateway`, `social-service`, or
  `content-service`** here — any future need for `content-service` data must go through
  `ContentServiceClient`/a new internal-API endpoint, never a resurrected Maven dependency; a real
  cross-service call always means HTTP now that every module in this reactor is (or, for `gateway`,
  never was) a standalone deployable. This is also why `SseStreamTemplate` owns its own
  `SSE_TIMEOUT_MS` constant, read locally by this module's own `ChatMvcConfig` — there is no
  `gateway`-hosted `WebMvcConfig` left anywhere in this reactor to read it from anymore.
- **`SearchDocument` was deleted, not converted** — an audit while doing the above found it had
  zero consumers anywhere in the reactor (no repository, no service, no controller) and mapped to a
  `SEARCH_DOCUMENT` table that no Liquibase changeset ever created; with `hibernate.ddl-auto:
  validate` active, this entity would have failed application startup the moment anything forced
  Hibernate to validate it. Dead code from a scaffold that was never wired up — removed outright
  rather than given the same `contentItemId` treatment as `ContentEmbedding`.
- Since the REST layer moved in (see above), this module also declares
  `spring-boot-starter-web` (`@RestController`/`ResponseEntity`/`SseEmitter`/`MediaType`),
  `spring-boot-starter-validation` (`@Valid` on `ChatRequest`), `spring-boot-starter-oauth2-resource-server`
  (this module's own JWT verification, now that it's standalone), `spring-boot-starter-security`
  (`@PreAuthorize("hasRole('ADMIN')")` on `IngestionApi` plus this module's own filter chain — its own
  `security/SecurityConfig` now declares `@EnableWebSecurity` **and** `@EnableMethodSecurity` itself;
  this used to be declared once in `gateway`'s `SecurityConfig` and apply reactor-wide regardless of
  which module the annotated method lived in — that stopped being possible the moment this module
  became a separate deployable, and `IngestionApi` is the only `@PreAuthorize`-annotated method left
  in the whole reactor now that `gateway` has no controllers of its own), `spring-boot-starter-data-redis`
  + `bucket4j-redis` (`ChatRateLimiter`'s per-user buckets, `config/RedisConfig`'s
  `bucket4jRedisConnection` bean — see the dead-code finding below), and `spring-boot-starter-oauth2-client`
  (a real runtime dependency now, not `optional=true` — needed for `CustomOAuth2User`'s `OAuth2User`
  supertype and `ChatRateLimitInterceptor`'s read of it off the `SecurityContext`).
- **`config/RedisConfig` carries only the `bucket4jRedisConnection` bean, not a full
  `@EnableCaching`/`RedisCacheManager` setup — because that other half was dead code, not because it
  didn't fit here.** While extracting this module, a reactor-wide grep for
  `@Cacheable`/`@CacheEvict`/`@CachePut` came back with **zero real usages anywhere in the whole
  codebase**. `gateway`'s old `RedisCacheConfig` declared `@EnableCaching` plus a `cacheManager`/
  `baseRedisCacheConfiguration` bean pair (backed by `infra`'s `CacheTtlProperties`/`CacheNames`) that
  had been wired up and shipped but never actually consumed by a single Spring-managed cache
  annotation — the same class of finding as `SearchDocument` (see above): a scaffold that looked
  load-bearing but wasn't. Rather than move dead beans into this module's own `config/`, they were
  deleted outright, along with `infra.CacheTtlProperties`/`CacheNames` (now fully orphaned once
  `RedisCacheConfig` was gone). Only the one bean this module's own `ChatRateLimiter` actually needs
  (`bucket4jRedisConnection`, a raw Lettuce `StatefulRedisConnection` for Bucket4j's binary bucket
  state) moved here, into its own `RedisConfig`. If a real `@Cacheable` use case shows up later, add
  `@EnableCaching`/a `RedisCacheManager` back here from scratch rather than assuming the deleted
  classes are worth resurrecting as-is.
- **New pgvector-typed fields must use `FloatArrayToVectorConverter` + `@JdbcType(PgVectorJdbcType.class)`
  together** — `@JdbcTypeCode(SqlTypes.OTHER)` does *not* work as a substitute for this
  Hibernate 6 + PostgreSQL combination (resolves to the wrong `JdbcType` and fails on write). See
  `ContentEmbedding.embedding`'s Javadoc for the full explanation if this needs revisiting.
- **`ChatSession.userId: Integer` → `userUuid: String`, `PipelineMetrics.userId: Integer` →
  `userUuid: String`, claims-based, mirroring `task-service`'s `ownerUuid`/`content-service`'s
  `authorUuid`.** Confirmed via an explicit user-facing `AskUserQuestion` before applying it (the
  claims-based option was recommended and chosen) — a chat session or a pipeline-metrics row only
  ever needs "is this the caller's own session/row," never another user's profile data, so there's no
  reason to justify a persisted `User` copy just for this module. Every method that took/returned
  `Integer userId` in the chat+pipeline-metrics feature was renamed to `String userUuid` throughout:
  `ChatSessionRepository`, `ChatSessionService`/`Impl`, `RagQueryService`/`Impl`,
  `RagPipelineContext`, `PipelineCompletedEventListener`, `ChatApi`/`ChatController`. The columns
  renamed the same way (`USER_ID` → `USER_UUID`) in `DKP-0032` (see below) — no FK to any `USER`
  table either before or after, since these are append-only/session rows that must survive a user's
  deletion from whichever app JIT-provisioned them.
- **Two native-SQL schema literals needed fixing on top of the usual `@Table(schema=...)` sweep**,
  since these don't go through Hibernate's schema resolution at all: `ContentEmbeddingRepository`'s
  three native queries (`product.content_embedding` → `ai.content_embedding`) and
  `PipelineMetricsRepository`'s native aggregate query (`product.PIPELINE_METRICS` →
  `ai.PIPELINE_METRICS`). A plain `@Table(schema=...)` removal (done here too, on `ChatSession`/
  `PipelineMetrics`/`ChatMessage`/`SysParam` — `ContentEmbedding` never had one) only fixes
  Hibernate-generated SQL; a hand-written native query with a hardcoded schema prefix silently keeps
  pointing at the old schema regardless, and this module has both kinds. Grep for `product\.` /
  `"product"` across `repository/` whenever a future native query is added here.
- **Content+AI indexing orchestration belongs here, not `gateway`.** The old rule ("only `gateway`
  can depend on two feature modules, so cross-module orchestration lives there") stopped applying to
  this specific pairing back when `ai-service` still had a real Maven dependency on `content-service`
  — this module was always the more specific owner able to see both without `gateway`'s help. That
  Maven dependency is gone now (see above; the two talk over HTTP only), and `gateway` itself has no
  embedded feature modules left to orchestrate between at all — but the orchestration still belongs
  here regardless, as this module's own indexing pipeline reaching out to a sibling service, not a
  `gateway` concern reborn. A genuinely new orchestration need spanning two *standalone* services with
  no dependency relationship between them would be the one case worth revisiting this for, and would
  need a real HTTP call either way, not a resurrected Maven dependency.
- Every RAG-affecting threshold (similarity, top-k, guard thresholds) should be a
  `@ConfigurationProperties` field with a sensible default, not a hardcoded constant — that's the
  established pattern for every existing stage.
