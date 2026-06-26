# Project Structure

## Module layout

```
dev-knowledge-platform/
├── common/          — shared entities, enums, exceptions, DTOs; no Spring dependencies
├── ai-service/      — RAG pipeline: embedding, vector search, LLM generation (LangChain4j)
├── api/             — REST endpoints, security, Liquibase migrations, Spring Boot entry point
└── gui/             — React 18 + TypeScript + MUI frontend (Vite)
```

Dependency order: `common` ← `ai-service` ← `api`. `gui` is independent.

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

## ai-service

```
ai-service/src/main/java/com/ttg/devknowledgeplatform/ai/
├── config/
│   ├── AiServiceConfig.java          — wires ChatLanguageModel + StreamingChatLanguageModel beans
│   └── EmbeddingProperties.java      — @ConfigurationProperties at app.ai.embedding.*
│                                        fields: apiKey, model, dimensions, chunkSize, chunkOverlap,
│                                        chatModel, maxTokens, temperature, maxRetries,
│                                        topK, similarityThreshold, oversampleFactor, mmrLambda,
│                                        systemPrompt, inputEnrichmentPrompt, summarisationPrompt,
│                                        centroidRefreshInterval,
│                                        anomalyHardThreshold (0.20), anomalySoftThreshold (0.40),
│                                        anomalySoftSimilarityThreshold (0.82),
│                                        injectionDetection (nested InjectionDetectionProperties:
│                                          maxQueryLength, patterns, prototypes, similarityThreshold,
│                                          rejectionMessage)
├── converter/
│   └── FloatArrayToVectorConverter.java  — JPA AttributeConverter for pgvector column type
├── dto/
│   ├── RagAnswer.java                — answer text + List<RagSource>
│   └── RagSource.java                — contentItemId, sourceType, title, chunkText, similarity
├── entity/
│   └── ContentEmbedding.java         — embedding vector (1536-dim), chunkText, sourceType,
│                                        chunkIndex, modelName, tokenCount,
│                                        metadata (JSONB: categoryId, categoryName, tagIds, tagNames)
├── exception/
│   └── RagQueryException.java
├── pipeline/                         — Pipes-and-Filters RAG pipeline (Pipes-and-Filters pattern)
│   ├── RagPipelineContext.java       — mutable per-request carrier: inputs, stage outputs (contextualizedQuestion, enrichedQuestion, queryEmbedding, effectiveSimilarityThreshold, …), abort state
│   ├── RagPipelineStage.java         — @FunctionalInterface: void process(RagPipelineContext)
│   ├── RagPipelineRunner.java        — assembles ordered stages, stops on abort
│   ├── ScoredChunk.java              — package-private record: ContentEmbedding + float score
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
│   └── ContentEmbeddingRepository.java   — findTopSimilarIds (pgvector <=>), findAllByIdWithContentItem,
│                                           computeGlobalCentroid(), computeCentroidBySourceType(String)
└── service/
    ├── ContentIngestionService.java          — chunks text + stores embeddings
    ├── ConversationSummarisationService.java — compresses old turns into a rolling summary (LLM)
    ├── CorpusStatisticsService.java          — interface: getCentroidFor(RagFilter), refresh(); in ai-service so stages can inject it
    ├── EmbeddingService.java                 — wraps OpenAI embedding API
    ├── RagQueryService.java                  — interface: query() + queryStream();
    │                                            primary overloads accept ConversationContext + RagFilter
    ├── RagStreamHandler.java                 — SSE callback interface
    └── impl/
        ├── ConversationSummarisationServiceImpl.java — ChatLanguageModel-backed summarisation
        └── RagQueryServiceImpl.java          — thin orchestrator: create context → RagPipelineRunner
                                                 → call ChatLanguageModel / StreamingChatLanguageModel
```

---

## api

```
api/src/main/java/com/ttg/devknowledgeplatform/
├── api/                              — controller interfaces (HTTP annotations live here)
│   └── impl/
│       ├── ChatController.java       — POST /api/v1/chat, POST /api/v1/chat/stream;
│       │                               builds RagFilter from ChatRequest and passes to RagQueryService
│       └── …                         — other controllers
├── config/
│   ├── AsyncConfig.java              — ragStreamExecutor thread pool (10 core, 50 max, queue 100)
│   ├── SecurityConfig.java           — JWT + OAuth2 filter chain
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
│   ├── SysParamRepository.java       — JpaRepository<SysParam, Integer>; findByName(ParamKey); in api/ (ai-service has no JPA context)
│   ├── spec/                         — JPA Specification implementations for dynamic filtering
│   └── …
├── security/                         — JwtProvider, OAuth2 handlers, UserUtils
└── service/
    ├── ChatSessionService.java       — getOrCreateSessionId, getConversationContext (primary),
    │                                   getRecentTurns, addTurn (triggers rolling summary), listSessions, getHistory
    ├── ContentIndexingService.java   — index / reindex / deleteIndex per contentItemId
    ├── IndexingQualityService.java   — assess(contentItemId, contentType) → QualityVerdict; centroid distance check at indexing time
    ├── QualityVerdict.java           — record: boolean lowQuality, float score; factories pass/flag/skipped
    └── impl/
        ├── IndexingQualityServiceImpl.java  — loads embeddings from ContentEmbeddingRepository; mean centroid dotProduct; graceful cold-start
        ├── CorpusStatisticsServiceImpl.java — @PostConstruct loads centroids from SYS_PARAM; @Scheduled refresh
        │                                       recomputes via SQL avg(embedding); volatile float[] cache; upsert via SysParamRepository
        └── ContentIndexingServiceImpl.java  — type-specific ingestion; buildCommonMetadata()
                                               writes categoryId, categoryName, tagIds, tagNames
                                               to every chunk's JSONB metadata
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
