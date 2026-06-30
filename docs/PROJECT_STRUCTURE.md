# Project Structure

## Module layout

```
dev-knowledge-platform/
├── common/          — shared entities, enums, exceptions, DTOs; no Spring dependencies
├── infra/           — shared Spring infrastructure: event base classes, composed annotations, MDC utilities
├── ai-service/      — RAG pipeline: embedding, vector search, LLM generation (LangChain4j)
├── api/             — REST endpoints, security, Liquibase migrations, Spring Boot entry point
└── gui/             — React 18 + TypeScript + MUI frontend (Vite)
```

Dependency order: `common` ← `infra` ← `ai-service` ← `api`. `gui` is independent.

---

## common

```
common/src/main/java/com/ttg/devknowledgeplatform/common/
├── dto/
│   ├── ConversationContext.java       — rolling summary + recent verbatim turns; primary RAG context type
│   └── ConversationTurn.java         — role + content record for a single message
├── entity/
│   ├── AbstractEntity.java           — audit columns (usrCreation, dteCreation, version, …)
│   ├── Article.java
│   ├── Category.java                 — hierarchical; parent/children self-join
│   ├── ContentItem.java              — base content record (type, status, title, slug, category)
│   ├── ContentItemTag.java           — join entity for content ↔ tag
│   ├── InterviewQuestion.java
│   ├── Tag.java
│   ├── ChatSession.java              — userId, title, lastActivityAt, summary (TEXT); parent of ChatMessage rows
│   └── ChatMessage.java              — role, content, turnIndex; child of ChatSession
├── enums/
│   ├── ContentStatus.java            — DRAFT, PUBLISHED, …
│   ├── ContentType.java              — INTERVIEW_QUESTION, ARTICLE, BLOG_POST
│   ├── ParamKey.java                 — typed keys for SYS_PARAM.NAME; renaming a constant requires a DB migration
│   └── UserRole.java
├── entity/
│   ├── ContentItem.java              — qualityScore (Double, nullable): mean centroid similarity set at indexing time
│   └── SysParam.java                 — @Entity for SYS_PARAM; fields: name (ParamKey), value (TEXT), computedAt
└── exception/
    ├── ApiException.java
    ├── BusinessException.java
    ├── ErrorCode.java                — 88+ domain error codes
    └── ResourceNotFoundException.java
```

---

## infra

```
infra/src/main/java/com/ttg/devknowledgeplatform/infra/
├── context/
│   └── MdcKeys.java              — MDC key constants shared across modules (e.g. TRACE_ID = "traceId")
└── event/
    ├── ApplicationEventHandler.java  — marker interface; Find Implementations = full event bus registry across modules
    ├── EventHandler.java             — composed @EventListener + @Async; enforces async on every listener
    └── AsyncEventHandler.java        — abstract base class (Template Method); provides async dispatch via @EventHandler,
                                         MDC TRACE_ID binding (opt-in via resolveTraceId()), timing, and exception safety;
                                         subclasses implement doHandle(); subclasses that need DB writes declare @Transactional themselves
```

---

## ai-service

```
ai-service/src/main/java/com/ttg/devknowledgeplatform/ai/
├── config/
│   ├── AiServiceConfig.java   — wires ChatLanguageModel + StreamingChatLanguageModel beans; injects OkHttpProperties for timeout
│   ├── ModelConfig.java       — @ConfigurationProperties at app.ai.model.*
│   │                             fields: apiKey, model, dimensions, chatModel, maxTokens, temperature, maxRetries
│   ├── OkHttpProperties.java  — @ConfigurationProperties at app.ai.okhttp.*; timeout (default 60s); passed to LangChain4j builders
│   ├── IndexingConfig.java    — @ConfigurationProperties at app.ai.indexing.*
│   │                             fields: chunkSize, chunkOverlap, centroidRefreshInterval, indexingCoherenceThreshold
│   ├── RetrievalConfig.java   — @ConfigurationProperties at app.ai.retrieval.*
│   │                             fields: topK, similarityThreshold, oversampleFactor, mmrLambda, outlierGapThreshold
│   ├── GuardConfig.java       — @ConfigurationProperties at app.ai.guards.*
│   ├── MonitoringConfig.java  — @ConfigurationProperties at app.ai.monitoring.*;
│   │                             slowRequestThresholdMs (default 5000), highCostThresholdUsd (default 0.01); 0 = disabled
│   │                             fields: anomaly thresholds, evidence thresholds, answer thresholds,
│   │                             conversationTopicShiftThreshold, outOfScopeAnswer, evidenceInsufficientAnswer,
│   │                             injectionDetection (nested: maxQueryLength, patterns, prototypes,
│   │                             similarityThreshold, rejectionMessage)
│   ├── LabelsConfig.java      — @ConfigurationProperties at app.ai.labels.*
│   │                             fields: contextSummaryLabel, contextFollowUpLabel, historySummaryLabel,
│   │                             historySummaryAck, compressionPreviousSummaryLabel, compressionTurnsLabel
│   ├── LoadedPrompts.java     — record holding 6 prompt strings loaded from classpath at startup
│   └── PromptsLoader.java     — @Configuration that reads prompts/*.txt and produces LoadedPrompts bean
├── converter/
│   └── FloatArrayToVectorConverter.java  — JPA AttributeConverter for pgvector column type
├── dto/
│   ├── AnswerQualityVerdict.java          — record: boolean drifted, float contextSimilarity, float querySimilarity; skipped() sentinel
│   ├── EmbedResult.java                   — record: float[] vector + int tokenCount; return type of EmbeddingService.embed()
│   ├── MetricsPeriod.java                 — enum: LAST_24H / LAST_7_DAYS / LAST_30_DAYS; each holds Duration getLookback()
│   ├── PipelineMetricsSummary.java        — record: aggregated cost/latency response; nested TokenUsageSummary record
│   ├── PipelineMetricsSummaryProjection.java — Spring Data JPA interface projection for native aggregate query
│   ├── EmbeddingStatsProjection.java      — interface projection: contentItemId, chunkCount, totalTokens, modelName, lastIndexedAt;
│   │                                        returned by ContentEmbeddingRepository.findStatsByContentItemIds for admin embedding list
│   ├── RagAnswer.java                     — answer text + List<RagSource>
│   ├── RagSource.java                     — contentItemId, sourceType, title, chunkText, similarity
│   ├── ScoredChunk.java                   — record: ContentEmbedding + float score (post-scoring candidates)
│   └── StageSpan.java                     — record: stage name, durationMs, aborted flag; one per pipeline stage per request
├── event/
│   ├── PipelineCompletedEvent.java         — record event published by RagQueryServiceImpl after each pipeline execution;
│   │                                        carries RagPipelineContext + AnswerQualityVerdict
│   └── PipelineCompletedEventListener.java — extends AsyncEventHandler<PipelineCompletedEvent>; @Transactional;
│                                            maps event → PipelineMetrics entity; resolveTraceId() binds MDC for logging
├── entity/
│   ├── ContentEmbedding.java         — embedding vector (1536-dim), chunkText, sourceType,
│   │                                    chunkIndex, modelName, tokenCount,
│   │                                    metadata (JSONB: categoryId, categoryName, tagIds, tagNames)
│   └── PipelineMetrics.java          — append-only analytics entity (no AbstractEntity); columns: traceId, createdAt,
│                                        abortedAt, candidateCount, afterScoringCount, selectedCount,
│                                        evidenceMeanScore, effectiveSimThreshold, answerContextSim, answerQuerySim, answerDrifted;
│                                        latency: contextualizationMs, embeddingMs, retrievalMs, llmGenerationMs, totalPipelineMs;
│                                        tokens: contextualizationInputTokens, contextualizationOutputTokens, embeddingTokens,
│                                        qualityEmbeddingTokens, generationInputTokens, generationOutputTokens, estimatedCostUsd;
│                                        attribution: userId (no FK — analytics rows must survive user deletion)
├── exception/
│   └── RagQueryException.java
├── pipeline/                         — Pipes-and-Filters RAG pipeline (Pipes-and-Filters pattern)
│   ├── RagPipelineContext.java       — mutable per-request carrier: inputs, stage outputs, abort state;
│   │                                    trace: traceId (UUID), spans (List<StageSpan>), elapsedMs();
│   │                                    cost/latency: llmGenerationMs, contextualizationInput/OutputTokens,
│   │                                    embeddingTokens, qualityEmbeddingTokens, generationInput/OutputTokens;
│   │                                    attribution: userId (nullable)
│   ├── RagPipelineStage.java         — @FunctionalInterface: process(ctx) + default execute(ctx) (Template Method: times process + records span)
│   ├── RagPipelineRunner.java        — assembles ordered stages, stops on abort; emits PIPELINE_TRACE log after every run
│   ├── VectorUtils.java              — package-private: dotProduct, toVectorString
│   ├── PromptGuardStage.java         — FIRST stage: user-input injection guard (length + lexical + semantic similarity); runs before any LLM call
│   ├── ContextualizationStage.java   — LLM enrichment: resolves pronouns → STANDALONE (for embedding) + CONTEXT/TASK/CONSTRAINTS/OUTPUT_FORMAT (for generation)
│   ├── EmbeddingStage.java           — OpenAI embed of contextualized question
│   ├── QueryAnomalyStage.java        — cosine similarity vs L2-normalised corpus centroid; hard abort or soft threshold raise
│   ├── RetrievalStage.java           — pgvector ANN search + eager-load; always oversamples topK×oversampleFactor
│   ├── ScoringStage.java             — AND-compose filter predicates from RagFilter + dot-product + threshold (effectiveSimilarityThreshold takes precedence); aborts if empty
│   ├── RetrievalAnomalyStage.java    — largest-gap pruning of scored chunks; removes relative outliers before MMR
│   ├── DeduplicationStage.java       — NOT in active pipeline; retained for reference (see class Javadoc)
│   ├── MmrStage.java                 — greedy MMR selection of topK from scored chunks; handles diversity
│   ├── RetrievedContentGuardStage.java — pre-MMR corpus data-channel guard: lexical scan of scoredChunks; removes infected chunks so MMR fills every topK slot from safe candidates
│   ├── EvidenceQualityStage.java     — post-MMR hallucination guard: mean score + min chunk count; aborts if either fails
│   └── MessageBuildingStage.java     — assembles List<ChatMessage> + List<RagSource>
├── filter/                           — dynamic post-retrieval filter package
│   └── RagFilter.java                — Java 21 record: sourceTypes, tags, categoryId
├── repository/
│   ├── ContentEmbeddingRepository.java   — findTopSimilarIds (pgvector <=>), findAllByIdWithContentItem,
│   │                                       findStatsByContentItemIds(List<Integer>) → List<EmbeddingStatsProjection>
│   │                                       (JPQL: COUNT/SUM/MAX grouped by content item ID),
│   │                                       computeGlobalCentroid(), computeCentroidBySourceType(String)
│   └── PipelineMetricsRepository.java    — JpaRepository<PipelineMetrics, Integer>; append-only analytics writes;
│                                            fetchSummary(Instant) — native query using percentile_cont WITHIN GROUP
└── service/
    ├── ContentIngestionService.java             — chunks text + stores embeddings
    ├── ConversationSummarisationService.java    — compresses old turns into a rolling summary (LLM)
    ├── CorpusStatisticsService.java             — interface: getCentroidFor(RagFilter), refresh(); in ai-service so stages can inject it
    ├── EmbeddingService.java                    — wraps OpenAI embedding API; embed() returns EmbedResult
    ├── AnswerQualityService.java                — post-generation drift detection: answer vs context centroid + answer vs query
    ├── ConversationTopicGuardService.java       — pre-pipeline topic shift guard: embeds question + history fingerprint; strips recent turns on shift
    ├── PipelineMetricsSummaryService.java       — interface: getSummary(MetricsPeriod); returns PipelineMetricsSummary
    ├── RagQueryService.java                     — interface: query() + queryStream();
    │                                               primary overloads accept ConversationContext + RagFilter + userId
    ├── RagStreamHandler.java                    — SSE callback interface
    └── impl/
        ├── AnswerQualityServiceImpl.java             — embeds answer; computes normalised context centroid from selectedChunks;
        │                                               evaluates contextSimilarity + querySimilarity; logs WARN on drift
        ├── ConversationSummarisationServiceImpl.java — ChatLanguageModel-backed summarisation
        ├── ConversationTopicGuardServiceImpl.java    — embedBatch(question + historyFingerprint); strips recentTurns on shift
        ├── PipelineMetricsSummaryServiceImpl.java    — @Transactional(readOnly=true); calls fetchSummary(); maps projection to record
        └── RagQueryServiceImpl.java                  — thin orchestrator: topicGuard → pipeline → recordPipelineMetrics()
                                                         (6 Micrometer instruments) → LLM call + timing + token capture
                                                         → assessAnswerQuality() → publishEvent(PipelineCompletedEvent)
```

---

## api

```
api/src/main/java/com/ttg/devknowledgeplatform/
├── api/                              — controller interfaces (HTTP annotations live here)
│   ├── EmbeddingIndexApi.java        — GET /api/v1/admin/embeddings?page&size&q&contentType&contentStatus&indexed (admin-only)
│   ├── PipelineMetricsApi.java       — GET /api/v1/admin/pipeline-metrics/summary?period=LAST_7_DAYS (admin-only)
│   └── impl/
│       ├── ChatController.java            — POST /api/v1/chat, POST /api/v1/chat/stream;
│       │                                    builds RagFilter from ChatRequest and passes to RagQueryService
│       ├── EmbeddingIndexController.java  — delegates to EmbeddingIndexService; no mapping logic
│       ├── PipelineMetricsController.java — delegates to PipelineMetricsSummaryService; no mapping logic
│       └── …                              — other controllers
├── config/
│   ├── SecurityConfig.java           — JWT + OAuth2 filter chain
│   ├── thread/
│   │   ├── ThreadPoolProperties.java — @ConfigurationProperties at app.threads.*;
│   │   │                               nested SseExecutor: corePoolSize (10), maxPoolSize (50),
│   │   │                               queueCapacity (100), awaitTerminationSeconds (30); env-var overrides
│   │   └── ThreadPoolConfig.java     — Factory Method: creates sseStreamExecutor bean, registers
│   │                                   ExecutorServiceMetrics (Micrometer Decorator); all pool sizing from ThreadPoolProperties
│   ├── web/
│   │   └── WebMvcConfig.java         — @EnableAsync; injects sseStreamExecutor; configureAsyncSupport (timeout 60 s);
│   │                                   rate-limit interceptor; CurrentUserIdArgumentResolver
│   └── sse/
│       └── SseStreamTemplate.java    — SSE writer abstraction
├── database/
│   └── sql/                          — Liquibase changelogs (master: dev-knowledge-platform.xml)
├── dto/
│   └── chat/
│       ├── ChatRequest.java          — question, sessionId, sourceTypes, categoryId, tags
│       ├── ChatResponse.java
│       ├── ChatSessionHistoryDto.java
│       └── ChatSessionSummaryDto.java
├── mapper/                           — MapStruct mappers (DTO ↔ entity)
├── repository/
│   ├── SysParamRepository.java       — JpaRepository<SysParam, Integer>; findByName(ParamKey)
│   ├── spec/                         — JPA Specification implementations for dynamic filtering
│   └── …
├── security/                         — JwtProvider, OAuth2 handlers, UserUtils
└── service/
    ├── ChatSessionService.java       — getOrCreateSessionId, getConversationContext (primary),
    │                                   getRecentTurns, addTurn (triggers rolling summary), listSessions, getHistory
    ├── ContentIndexingService.java   — index / reindex / deleteIndex per contentItemId
    ├── EmbeddingIndexService.java    — list(page,size,q,contentType,contentStatus,indexed) → PagedResponse<EmbeddingIndexItemResponse>
    ├── IndexingQualityService.java   — assess(contentItemId, contentType) → QualityVerdict; centroid distance check at indexing time
    ├── QualityVerdict.java           — record: boolean lowQuality, float score; factories pass/flag/skipped
    └── impl/
        ├── IndexingQualityServiceImpl.java  — loads embeddings from ContentEmbeddingRepository; mean centroid dotProduct; graceful cold-start
        ├── CorpusStatisticsServiceImpl.java — @PostConstruct loads centroids from SYS_PARAM; @Scheduled refresh
        │                                       recomputes via SQL avg(embedding); volatile float[] cache; upsert via SysParamRepository
        ├── ContentIndexingServiceImpl.java  — type-specific ingestion; buildCommonMetadata()
        │                                       writes categoryId, categoryName, tagIds, tagNames
        │                                       to every chunk's JSONB metadata
        └── EmbeddingIndexServiceImpl.java   — two-query pattern: Specification page query (ContentItemRepository)
                                               + batch JPQL aggregate (ContentEmbeddingRepository.findStatsByContentItemIds);
                                               EXISTS subquery Specification for indexed filter
```

---

## Request flow

```
GUI (React)
  └─→ ChatController (POST /api/v1/chat[/stream])
        getConversationContext (summary + recent turns)
        builds RagFilter from request fields
        └─→ RagQueryServiceImpl
              creates RagPipelineContext
              └─→ RagPipelineRunner (Pipes-and-Filters)
                    PromptGuardStage        — user-input injection guard: length + lexical + semantic prototype similarity
                    ContextualizationStage  — LLM enrichment (STANDALONE + Context+Task+Constraints+OutputFormat)
                    EmbeddingStage          — OpenAI text-embedding-3-small
                    QueryAnomalyStage       — cosine sim vs corpus centroid; hard abort or soft threshold raise
                    RetrievalStage          — pgvector ANN (HNSW <=>); always oversamples topK×oversampleFactor
                    ScoringStage            — AND-compose RagFilter predicates + dotProduct + threshold
                    RetrievalAnomalyStage   — largest-gap pruning; removes relative outliers from scored chunks
                    RetrievedContentGuardStage — corpus data-channel guard: lexical scan of scoredChunks; removes infected chunks before MMR
                    MmrStage                — greedy MMR topK selection from clean candidate pool; handles cross-doc + within-doc diversity
                    EvidenceQualityStage    — post-MMR hallucination guard: mean score + min chunk count
                    MessageBuildingStage    — List<ChatMessage> + List<RagSource>
              └─→ ChatLanguageModel (blocking) OR StreamingChatLanguageModel (SSE)
```

---

## Database

- Schema: `product`
- Sequences: one per table (`TABLE_NAME_SEQ`)
- Audit columns on every entity via `AbstractEntity`
- pgvector HNSW index on `content_embedding.embedding` (cosine distance, `vector_cosine_ops`)
- `SYS_PARAM` — general-purpose key-value table; stores corpus centroid vectors and future AI/config parameters
- Migrations: `api/src/main/java/com/ttg/devknowledgeplatform/database/sql/`
  - Naming: `YYYY/VERSION/YYYYMMDDHHMI__VERSION__TICKET__description.sql`
