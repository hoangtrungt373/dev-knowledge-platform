# CLAUDE.md — ai-service

Module-local guidance for `ai-service`. Read alongside the root `CLAUDE.md`.

## What lives here

RAG pipeline: embedding, vector search, LLM generation (LangChain4j). Package root:
`com.ttg.devknowledgeplatform.ai.*`. Full class-by-class detail lives in
`docs/PROJECT_STRUCTURE.md`'s `## ai-service` section — this file covers conventions and config,
not a restated file listing.

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
tuning; picked up automatically by `@ConfigurationPropertiesScan` on the main application class in
`gateway`, same as every other `@ConfigurationProperties` bean in this module). It had no consumers
outside the chat feature, so the move was pure relocation with no API surface change.

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
`gateway`'s `WebMvcConfig.configureAsyncSupport` reads it from here (the reference works in this
direction only — `gateway` already depends on `ai-service`, never the reverse).

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
chat endpoint. `ChatMvcConfig` (`config/web/`) registers `ChatRateLimitInterceptor` for
`/api/v1/chat/**` via its own `WebMvcConfigurer` bean — Spring composes every `WebMvcConfigurer` in
the context automatically, so this module doesn't need `gateway`'s `WebMvcConfig` to register
interceptors on its behalf.

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

`sseStreamExecutor` (configured in `gateway`, 10 core / 50 max / queue 100) feeds SSE streaming and MVC
async dispatch; `@EventHandler`-annotated listeners (e.g. `PipelineCompletedEventListener`) run on
the separate `asyncEventExecutor` pool instead — now configured in `infra` itself (its own
`AsyncEventThreadPoolConfig`), see `infra/CLAUDE.md`. Don't assume both event dispatch and SSE
streaming share one pool; they're deliberately separate bulkheads. `sseStreamExecutor`'s bean
still lives in `gateway` (`ThreadPoolConfig`) even though `ChatController`/`SseStreamTemplate` now
live here — it's injected by type/qualifier, no package coupling either way.

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
  `app.content-service.*` — `base-url` points at this same process today and will change to that
  module's own host:port once it's actually extracted; the shared `X-Internal-Api-Key` header must
  match `content-service`'s own `app.internal-api.key`). `dto/client/ContentItemDto` is this
  module's own duplicated copy of that API's response shape — same "duplicate, don't share
  cross-service DTOs" convention every `KeycloakJwtAuthenticationConverter` duplicate in this
  codebase already follows. **Never add a dependency on `gateway`, `social-service`, or
  `content-service`** here — any future need for `content-service` data must go through
  `ContentServiceClient`/a new internal-API endpoint, never a resurrected Maven dependency. This is
  why `SseStreamTemplate` owns its own `SSE_TIMEOUT_MS` constant instead of reading `gateway`'s
  `WebMvcConfig` — the reference must only ever point from `gateway` inward.
- **`SearchDocument` was deleted, not converted** — an audit while doing the above found it had
  zero consumers anywhere in the reactor (no repository, no service, no controller) and mapped to a
  `SEARCH_DOCUMENT` table that no Liquibase changeset ever created; with `hibernate.ddl-auto:
  validate` active, this entity would have failed application startup the moment anything forced
  Hibernate to validate it. Dead code from a scaffold that was never wired up — removed outright
  rather than given the same `contentItemId` treatment as `ContentEmbedding`.
- Since the REST layer moved in (see above), this module also declares
  `spring-boot-starter-web` (`@RestController`/`ResponseEntity`/`SseEmitter`/`MediaType`),
  `spring-boot-starter-validation` (`@Valid` on `ChatRequest`), `spring-boot-starter-security`
  (method-security annotations only — `@PreAuthorize("hasRole('ADMIN')")` on `IngestionApi`;
  `@EnableMethodSecurity` itself is still declared once, in `gateway`'s `SecurityConfig`, and applies
  across the whole Spring context regardless of which module the annotated method lives in),
  `spring-boot-starter-data-redis` + `bucket4j-redis` (`ChatRateLimiter`'s per-user buckets), and
  `spring-boot-starter-oauth2-client` (`optional=true`, type support only — `ChatRateLimitInterceptor`
  reads `common.dto.CustomOAuth2User` off the `SecurityContext`).
- **New pgvector-typed fields must use `FloatArrayToVectorConverter` + `@JdbcType(PgVectorJdbcType.class)`
  together** — `@JdbcTypeCode(SqlTypes.OTHER)` does *not* work as a substitute for this
  Hibernate 6 + PostgreSQL combination (resolves to the wrong `JdbcType` and fails on write). See
  `ContentEmbedding.embedding`'s Javadoc for the full explanation if this needs revisiting.
- **Content+AI indexing orchestration belongs here, not `gateway`, whenever it only needs
  `content-service` + `ai-service`.** The previous rule (keep it in `gateway` because that's "the only
  module allowed to depend on two feature modules") no longer applies to this specific pairing —
  `ai-service` already depends on `content-service` in its own right (see above), so it's the more
  specific owner. Only escalate new orchestration to `gateway` if it genuinely needs a *third* module
  (e.g. `social-service`) that `ai-service` must never depend on.
- Every RAG-affecting threshold (similarity, top-k, guard thresholds) should be a
  `@ConfigurationProperties` field with a sensible default, not a hardcoded constant — that's the
  established pattern for every existing stage.
