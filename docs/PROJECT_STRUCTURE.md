# Project Structure

## Module layout

```
dev-knowledge-platform/
├── common/          — shared entities, enums, exceptions, DTOs; depends on Spring Data JPA (for @Entity), validation, web, security (all as annotation/type support, not full autoconfiguration)
├── infra/           — shared Spring infrastructure: event base classes, composed annotations, MDC utilities
├── ai-service/      — RAG pipeline: embedding, vector search, LLM generation (LangChain4j)
├── social-service/  — friend graph: search visibility, requests, friendships, blocking (later: chat/groups/messaging)
├── api/             — REST endpoints, security, Liquibase migrations, Spring Boot entry point
└── gui/             — React 18 + TypeScript + MUI frontend (Vite)
```

Dependency order: `common` ← `infra` ← `ai-service`/`social-service` ← `api`. `gui` is independent.
`social-service` mirrors `ai-service`'s shape (a business-logic module `api` depends on and wires up via REST) —
it depends only on `common` + `infra`, never on `api`, so it cannot reuse `api`'s repositories or services.

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
│   ├── QuestionAnswer.java           — general dev-knowledge Q&A, not only interview prep;
│   │                                    difficulty/isCommon are nullable interview-specific metadata
│   ├── Tag.java
│   ├── User.java                     — userUuid, email, username, password, firstName, lastName, profilePicture,
│   │                                    provider (UserProvider), role (UserRole), providerId, emailVerified, status
│   │                                    (UserStatus, presence), enabled; referenced by FK from social-service's
│   │                                    FriendRequest/Friendship/UserBlock entities (which live there, not here —
│   │                                    see social-service section below)
│   ├── ChatSession.java              — userId, title, lastActivityAt, summary (TEXT); parent of ChatMessage rows
│   └── ChatMessage.java              — role, content, turnIndex; child of ChatSession
├── enums/
│   ├── ChatProvider.java             — OPENAI, ANTHROPIC; selects LangChain4j builder family per chat model profile
│   ├── ContentStatus.java            — DRAFT, PUBLISHED, …
│   ├── ContentType.java              — QUESTION_ANSWER, ARTICLE, BLOG_POST
│   ├── ParamKey.java                 — typed keys for SYS_PARAM.NAME; renaming a constant requires a DB migration;
│   │                                   includes PROMPT_INJECTION_PROTOTYPE_EMBEDDINGS (fingerprinted vector-list cache
│   │                                   for PromptGuardStage — see repository/service below)
│   └── UserRole.java
├── entity/
│   ├── ContentItem.java              — qualityScore (BigDecimal, nullable): mean centroid similarity set at indexing time;
│   │                                    seedId (String, nullable, DB SEED_ID): NULL for user/admin-created rows, set only
│   │                                    by QuestionAnswerSeeder — sole idempotency key for long-lived seed data (DKP-0013)
│   ├── Category.java / Tag.java      — seedId (String, nullable, DB SEED_ID): same purpose as ContentItem.seedId above,
│   │                                    set only by CategorySeeder/TagSeeder (DKP-0013)
│   └── SysParam.java                 — @Entity for SYS_PARAM; fields: name (ParamKey), value (TEXT), computedAt
├── repository/
│   └── SysParamRepository.java       — JpaRepository<SysParam, Integer>; findByName(ParamKey); moved here from
│                                        api/repository so ai-service (which cannot depend on api) can reach it
│                                        via SysParamService below
├── service/
│   ├── SysParamService.java          — interface: getValue(ParamKey), upsert(ParamKey, String); string-in/string-out,
│   │                                   no opinion on value encoding — callers own their own serialization format
│   └── impl/
│       └── SysParamServiceImpl.java  — find-or-create-and-save upsert pattern, shared by CorpusStatisticsServiceImpl (api)
│                                        and PromptGuardStage (ai-service)
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
    ├── EventHandler.java             — composed @EventListener + @Async("asyncEventExecutor"); enforces async on
    │                                    every listener and pins dispatch to a dedicated pool (bulkhead vs sseStreamExecutor)
    └── AsyncEventHandler.java        — abstract base class (Template Method); provides async dispatch via @EventHandler,
                                         MDC TRACE_ID binding (opt-in via resolveTraceId()), timing, and exception safety;
                                         subclasses implement doHandle(); subclasses that need DB writes declare @Transactional themselves
```

---

## social-service

```
social-service/src/main/java/com/ttg/devknowledgeplatform/social/
├── entity/
│   ├── FriendRequest.java         — requester/addressee (User, common), status (FriendRequestStatus)
│   ├── Friendship.java            — user1/user2 (User), canonically ordered (user1.id < user2.id) so each
│   │                                 pair has exactly one row regardless of who sent the original request
│   └── UserBlock.java             — blocker/blocked (User); directional, independent of Friendship/FriendRequest
├── enums/
│   ├── FriendRequestStatus.java   — PENDING, ACCEPTED, REJECTED, CANCELLED
│   └── RelationshipStatus.java    — STRANGER, REQUEST_SENT, REQUEST_RECEIVED, FRIENDS, BLOCKED; computed
│                                     (not persisted) per profile/search-result view from the viewer's perspective
├── repository/
│   ├── FriendRequestRepository.java
│   ├── FriendshipRepository.java  — findFriendUserIds() used by the service for mutual-friend-count set intersection
│   ├── UserBlockRepository.java
│   ├── SocialUserRepository.java  — read access to User for this module; deliberately not named
│   │                                 UserRepository — api already has one over the same entity, and two Spring
│   │                                 Data repositories with the same simple name in different packages collide
│   │                                 on the default bean name at startup
│   └── spec/
│       └── UserSpecification.java — fuzzy username/name match, exact email match, excludes any user blocked
│                                     in either direction relative to the viewer
├── event/
│   ├── FriendRequestSentEvent.java     — record; published right after a pending FriendRequest is created
│   └── FriendRequestAcceptedEvent.java — record; published when a Friendship is created (explicit accept
│                                          or mutual auto-accept)
└── service/
    ├── FriendService.java           — sendRequest, accept/reject/cancelRequest, unfriend, block/unblock,
    │                                   getRelationshipStatus, countMutualFriends, listFriends/Incoming/Outgoing/
    │                                   BlockedUsers, searchUsers; returns entities, not REST DTOs — api's
    │                                   FriendMapper does entity→response mapping (same split as ai-service's
    │                                   RagQueryService → api's ChatResponse)
    └── impl/
        └── FriendServiceImpl.java   — mutual-request auto-accept; block cascades (removes friendship + pending
                                        request between the pair before recording the block); mutual invisibility
                                        (a lookup of a user who has blocked the viewer throws USER_NOT_FOUND, same
                                        as a nonexistent UUID, never a distinguishable "blocked" error)
```

Chat/groups/messaging (deferred) will be added here as new packages when that phase starts, not as a
separate module — see `docs/CHANGELOG.md` for the reasoning.

---

## ai-service

```
ai-service/src/main/java/com/ttg/devknowledgeplatform/ai/
├── config/
│   ├── AiServiceConfig.java   — builds Map<String,ChatLanguageModel> + Map<String,StreamingChatLanguageModel>,
│   │                             one entry per ChatModelsConfig.ChatModelProfile (OpenAI or Anthropic builder
│   │                             depending on provider), keyed by profile id; injects OkHttpProperties for timeout
│   ├── ModelConfig.java       — @ConfigurationProperties at app.ai.embedding-model.* (embedding settings only)
│   │                             fields: apiKey, model, dimensions
│   ├── ChatModelsConfig.java  — @ConfigurationProperties at app.ai.chat-models.*
│   │                             fields: defaultModel, profiles (List<ChatModelProfile>: id, provider,
│   │                             apiKey, maxTokens, temperature, maxRetries) — each profile self-contained;
│   │                             the profile list is the request-time allow-list for ChatRequest.chatModel
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
│   ├── PricingConfig.java     — @ConfigurationProperties at app.ai.pricing.*
│   │                             fields: embeddingCostPerToken (flat), chatModels (Map<String,ChatModelPricing>
│   │                             keyed by chat model id: inputCostPerToken, outputCostPerToken);
│   │                             consumed by PipelineCompletedEventListener#computeEstimatedCost();
│   │                             update whenever a profile is added to ChatModelsConfig or a provider's rates change
│   ├── LabelsConfig.java      — @ConfigurationProperties at app.ai.labels.*
│   │                             fields: contextSummaryLabel, contextFollowUpLabel, historySummaryLabel,
│   │                             historySummaryAck, compressionPreviousSummaryLabel, compressionTurnsLabel
│   ├── LoadedPrompts.java     — record holding 6 prompt strings loaded from classpath at startup
│   └── PromptsLoader.java     — @Configuration that reads prompts/*.txt and produces LoadedPrompts bean
├── converter/
│   ├── FloatArrayToVectorConverter.java  — JPA AttributeConverter for pgvector column type;
│   │                                        any field using it also needs @JdbcType(PgVectorJdbcType.class)
│   │                                        (see ContentEmbedding.embedding) or writes fail — a plain
│   │                                        varchar-typed bind doesn't implicitly cast to vector, and
│   │                                        @JdbcTypeCode(SqlTypes.OTHER) does NOT work as a substitute
│   │                                        (resolves to VarbinaryJdbcType for this Hibernate+PG combo)
│   └── PgVectorJdbcType.java             — custom JdbcType binding via setObject(index, value, Types.OTHER);
│                                            required companion to FloatArrayToVectorConverter, see its javadoc
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
│                                        attribution: userId (no FK — analytics rows must survive user deletion),
│                                        chatModel (id of the resolved chat model profile; NULL pre-DKP-0012 rows)
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
│   ├── PromptGuardStage.java         — FIRST stage: user-input injection guard (length + lexical + semantic similarity); runs before any LLM call;
│   │                                   caches prototype embeddings in SYS_PARAM (via SysParamService) keyed by a SHA-256
│   │                                   fingerprint of the embedding model + prototype list, so restarts skip re-embedding
│   │                                   until either config value actually changes
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
    ├── ChatModelResolver.java                   — interface: resolveBlocking(modelId), resolveStreaming(modelId),
    │                                               resolveModelId(modelId); null modelId falls back to ChatModelsConfig.defaultModel;
    │                                               throws BusinessException(AI_MODEL_UNSUPPORTED) for an unconfigured id
    ├── ConversationTopicGuardService.java       — pre-pipeline topic shift guard: embeds question + history fingerprint; strips recent turns on shift
    ├── PipelineMetricsSummaryService.java       — interface: getSummary(MetricsPeriod); returns PipelineMetricsSummary
    ├── RagQueryService.java                     — interface: query() + queryStream();
    │                                               primary overloads accept ConversationContext + RagFilter + userId + chatModel
    ├── RagStreamHandler.java                    — SSE callback interface
    └── impl/
        ├── AnswerQualityServiceImpl.java             — embeds answer; computes normalised context centroid from selectedChunks;
        │                                               evaluates contextSimilarity + querySimilarity; logs WARN on drift
        ├── ChatModelResolverImpl.java                 — looks up Map<String,ChatLanguageModel> / Map<String,StreamingChatLanguageModel>
        │                                                (built by AiServiceConfig) by resolved model id
        ├── ConversationSummarisationServiceImpl.java — ChatLanguageModel-backed summarisation
        ├── ConversationTopicGuardServiceImpl.java    — embedBatch(question + historyFingerprint); strips recentTurns on shift
        ├── PipelineMetricsSummaryServiceImpl.java    — @Transactional(readOnly=true); calls fetchSummary(); maps projection to record
        └── RagQueryServiceImpl.java                  — thin orchestrator: resolve chat model (before any pipeline work) →
                                                         topicGuard → pipeline → recordPipelineMetrics() (6 Micrometer instruments)
                                                         → LLM call + timing + token capture → assessAnswerQuality()
                                                         → publishEvent(PipelineCompletedEvent)
```

---

## api

```
api/src/main/java/com/ttg/devknowledgeplatform/
├── api/                              — controller interfaces (HTTP annotations live here)
│   ├── EmbeddingIndexApi.java        — GET /api/v1/admin/embeddings?page&size&q&contentType&contentStatus&indexed (admin-only)
│   ├── PipelineMetricsApi.java       — GET /api/v1/admin/pipeline-metrics/summary?period=LAST_7_DAYS (admin-only)
│   ├── UserApi.java                  — PUT /me, POST /me/avatar, GET /public/{userUuid} (enriched with
│   │                                    relationshipStatus/mutualFriendCount for authenticated viewers —
│   │                                    404s instead of leaking a "blocked" state if the target blocked the
│   │                                    viewer), GET /search?q= (fuzzy name/username, exact email)
│   ├── FriendApi.java                — /api/v1/friends/**: send/accept/reject/cancel requests, list
│   │                                    incoming/outgoing/friends, unfriend, block/unblock, list blocked users;
│   │                                    delegates to social-service's FriendService
│   └── impl/
│       ├── ChatController.java            — POST /api/v1/chat, POST /api/v1/chat/stream;
│       │                                    builds RagFilter from ChatRequest and passes to RagQueryService
│       ├── EmbeddingIndexController.java  — delegates to EmbeddingIndexService; no mapping logic
│       ├── PipelineMetricsController.java — delegates to PipelineMetricsSummaryService; no mapping logic
│       ├── UserController.java            — see UserApi above
│       ├── FriendController.java          — see FriendApi above
│       └── …                              — other controllers
├── config/
│   ├── SecurityConfig.java           — JWT + OAuth2 filter chain
│   ├── thread/
│   │   ├── ThreadPoolProperties.java — @ConfigurationProperties at app.threads.*;
│   │   │                               nested SseExecutor: corePoolSize (10), maxPoolSize (50),
│   │   │                               queueCapacity (100), awaitTerminationSeconds (30);
│   │   │                               nested AsyncEventExecutor: corePoolSize (5), maxPoolSize (20),
│   │   │                               queueCapacity (200), awaitTerminationSeconds (30); env-var overrides
│   │   └── ThreadPoolConfig.java     — Factory Method: creates sseStreamExecutor (SSE/MVC async dispatch) and
│   │                                   asyncEventExecutor (@EventHandler dispatch) beans as separate bulkheads;
│   │                                   registers both with ExecutorServiceMetrics (Micrometer Decorator); all
│   │                                   pool sizing from ThreadPoolProperties
│   ├── web/
│   │   └── WebMvcConfig.java         — @EnableAsync; wires sseStreamExecutor into configureAsyncSupport
│   │                                   (timeout 60 s) only — @Async dispatch uses asyncEventExecutor via an
│   │                                   explicit qualifier on @EventHandler; rate-limit interceptor;
│   │                                   CurrentUserIdArgumentResolver
│   └── sse/
│       └── SseStreamTemplate.java    — SSE writer abstraction
├── database/
│   └── sql/                          — Liquibase changelogs (master: dev-knowledge-platform.xml)
├── dto/
│   ├── chat/
│   │   ├── ChatRequest.java          — question, sessionId, sourceTypes, categoryId, tags, chatModel
│   │   │                                (chatModel: optional model id, e.g. "claude-sonnet-5"; null = server default)
│   │   ├── ChatResponse.java
│   │   ├── ChatSessionHistoryDto.java
│   │   └── ChatSessionSummaryDto.java
│   └── friend/                       — Java records (immutable), not the older Lombok @Data/@Builder style
│       ├── UserSummaryResponse.java       — userUuid, username, firstName, lastName, profilePicture, status;
│       │                                    nested inside the three below rather than repeating the fields
│       ├── UserSearchResultResponse.java  — UserSummaryResponse + relationshipStatus + mutualFriendCount
│       ├── FriendRequestResponse.java     — id, requester, addressee, status, createdAt
│       └── FriendSummaryResponse.java     — UserSummaryResponse + friendsSince
├── mapper/                           — MapStruct mappers (DTO ↔ entity)
│   └── FriendMapper.java             — abstract class (not interface) like UserMapper — needs an injected
│                                        StorageService for presigned avatar URLs, and MapStruct interfaces
│                                        can't hold instance fields; maps social-service entities to dto/friend/*
├── repository/
│   ├── spec/                         — JPA Specification implementations for dynamic filtering
│   └── …
├── security/                         — JwtProvider, OAuth2 handlers, UserUtils
│   └── jwt/
│       ├── TokenClaims.java          — sealed interface; typed JWT claim shape, permits
│       │                                AccessTokenClaims/RefreshTokenClaims; TokenClaims.parse(Claims)
│       ├── AccessTokenClaims.java    — record: userUuid, email, username, role
│       └── RefreshTokenClaims.java   — record: userUuid, username, role (type=refresh claim added
│                                        by toClaimsMap(); no email claim, unlike access tokens)
├── service/
│   ├── ChatSessionService.java       — getOrCreateSessionId, getConversationContext (primary),
│   │                                   getRecentTurns, addTurn (triggers rolling summary), listSessions, getHistory
│   ├── ContentIndexingService.java   — index / reindex / deleteIndex per contentItemId
│   ├── EmbeddingIndexService.java    — list(page,size,q,contentType,contentStatus,indexed) → PagedResponse<EmbeddingIndexItemResponse>
│   ├── IndexingQualityService.java   — assess(contentItemId, contentType) → QualityVerdict; centroid distance check at indexing time
│   ├── QualityVerdict.java           — record: boolean lowQuality, float score; factories pass/flag/skipped
│   ├── seed/                         — startup data seeding; format chosen per content shape
│   │   ├── CsvSeeder.java                — abstract Template Method for flat, single-file CSV
│   │   │                                   sources: owns read/iterate/skip-or-insert loop;
│   │   │                                   subclasses supply alreadyExists()/buildEntity()/persist()
│   │   ├── CategorySeeder.java           — extends CsvSeeder; data/csv/categories.csv (id, name,
│   │   │                                   parentId); identity by seedId (findBySeedId — NOT
│   │   │                                   name/slug, see DKP-0013: seed data is long-lived
│   │   │                                   alongside user content, so the idempotency key must
│   │   │                                   survive a NAME edit); parentId resolved via
│   │   │                                   findBySeedId too, not by parent name; rejects an id
│   │   │                                   reused for a different name, and a name collision with
│   │   │                                   an existing category; slug always generated via
│   │   │                                   SlugService.generateUniqueSlug, never authored; parent
│   │   │                                   rows must precede children
│   │   ├── TagSeeder.java                — extends CsvSeeder; data/csv/tags.csv (id, name,
│   │   │                                   status); same seedId-identity + generated-slug pattern
│   │   │                                   as CategorySeeder (no hierarchy, so no parentId)
│   │   ├── QuestionAnswerSeeder.java     — does NOT extend CsvSeeder (one-file-per-record
│   │   │                                   directory, not CSV rows — different iteration shape);
│   │   │                                   reads data/question-answers/*.md (YAML front
│   │   │                                   matter + markdown body via SnakeYAML/SafeConstructor);
│   │   │                                   identity by seedId (required front-matter id, NOT
│   │   │                                   slug/title); categoryId/tagIds resolved via
│   │   │                                   findBySeedId (Category/Tag's own id, not name/slug);
│   │   │                                   slug stays a separate, optional, production-URL-only
│   │   │                                   field unrelated to idempotency; difficulty/isCommon
│   │   │                                   optional (general Q&A, not only interview prep);
│   │   │                                   builds ContentItem + QuestionAnswer per file;
│   │   │                                   requires categories/tags seeded first
│   │   └── DataSeedingRunner.java        — ApplicationRunner, @ConditionalOnProperty(app.seed.enabled);
│   │                                       runs seeders in order: category → tag → questionAnswer
│   └── impl/
│       ├── IndexingQualityServiceImpl.java  — loads embeddings from ContentEmbeddingRepository; mean centroid dotProduct; graceful cold-start
│       ├── CorpusStatisticsServiceImpl.java — @PostConstruct loads centroids from SYS_PARAM; @Scheduled refresh
│       │                                       recomputes via SQL avg(embedding); volatile float[] cache; persistence
│       │                                       delegated to common's SysParamService (find-or-create-and-save owned there)
│       ├── ContentIndexingServiceImpl.java  — type-specific ingestion; buildCommonMetadata()
│       │                                       writes categoryId, categoryName, tagIds, tagNames
│       │                                       to every chunk's JSONB metadata
│       └── EmbeddingIndexServiceImpl.java   — two-query pattern: Specification page query (ContentItemRepository)
│                                              + batch JPQL aggregate (ContentEmbeddingRepository.findStatsByContentItemIds);
│                                              EXISTS subquery Specification for indexed filter
```

`api/src/main/resources/data/` (separate resources tree, not nested under the Java sources above):

```
data/
├── csv/                              — DataSeedingRunner input (see service/seed above); no
│   │                                    slug column — CategorySeeder/TagSeeder always generate
│   │                                    it via SlugService; identity AND cross-references are by
│   │                                    id (→ seedId), never name/slug
│   ├── categories.csv                    — id, name, parentId (parentId references another row's id)
│   └── tags.csv                           — id, name, status
├── question-answers/                 — one Markdown file per question; references Category/Tag
│   │                                    by id (categoryId/tagIds), not name or slug — see
│   │                                    docs/SEED_DATA_AUTHORING_GUIDE.md; 100 files (qa-*.md),
│   │                                    spread across all 12 leaf categories; general dev-knowledge
│   │                                    Q&A, not only interview prep
└── init-admin-user.sql               — local-dev admin bootstrap; NOT run by DataSeedingRunner or
                                         any other mechanism — apply manually against the local DB
```

Before writing new `question-answers/*.md` files, read `docs/SEED_DATA_AUTHORING_GUIDE.md` —
schema, mechanical rules the seeder enforces, content quality criteria, and the RAG-chunking
constraints that shape how sections should be written.

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

## Content domains

The platform is not limited to dev knowledge — additional knowledge domains (e.g. legal, medical) are
expected over time, and are modelled as data, not schema:

- A "domain" is a **root-level `Category` node** (e.g. "Dev Knowledge"), not a `ContentType` or schema
  concept. `Category`'s existing hierarchy (parent/children self-join, cycle-checked by
  `CategoryServiceImpl.validateParentAssignment`) already covers this with zero code change.
- `ContentType` (`QUESTION_ANSWER`, `ARTICLE`, `BLOG_POST`) discriminates content *shape*
  (which JOINed subtype table + `ContentIndexingServiceImpl` ingestion path applies), never subject
  matter. `QuestionAnswer` was originally named `InterviewQuestion` and scoped to dev-interview prep;
  it was broadened (see `CHANGELOG.md`) once it became clear the same question/answer shape is
  useful general dev-knowledge content across any domain, not just interview prep — `difficulty`/
  `isCommon` are now nullable, interview-specific metadata rather than defining characteristics.
- Adding a domain is a pure data operation: create a root `Category`, publish `Article`/`BlogPost`
  rows under it. No migration, no enum change, no new subtype table.
- Scoping retrieval/chat to one domain is a query-time filter, not new plumbing —
  `RagFilter.categoryId` already selects a category subtree independently of `sourceTypes`/`tags`.
- Revisit this convention only if a future domain needs its own structured fields (a genuinely new
  content *shape*, not just a new subject) — that's when `ContentType`'s closed-enum + JOINed-table
  model would need to become extensible (e.g. Strategy pattern over ingestion, open type registry).

---

## Database

- Schema: `product`
- Sequences: one per table (`TABLE_NAME_SEQ`)
- Audit columns on every entity via `AbstractEntity`
- pgvector HNSW index on `content_embedding.embedding` (cosine distance, `vector_cosine_ops`)
- `SYS_PARAM` — general-purpose key-value table; stores corpus centroid vectors and future AI/config parameters
- `FRIEND_REQUEST` / `FRIENDSHIP` / `USER_BLOCK` (DKP-0015) — friend graph, backing `social-service`'s
  entities of the same names. `FRIENDSHIP` stores each pair once with `USER_ID_1 < USER_ID_2` enforced by
  a check constraint. `FRIEND_REQUEST` has a partial unique index on the unordered pair
  `WHERE STATUS = 'PENDING'` — only pending rows are constrained, so a rejected/cancelled request doesn't
  block a later re-request. `USER_BLOCK` is directional (no implied reverse row).
- Migrations: `api/src/main/java/com/ttg/devknowledgeplatform/database/sql/` (Liquibase config lives in
  `api` regardless of which module owns the entities the migration backs — `social-service`'s tables are
  migrated from here too, same as `ai-service`'s)
  - Naming: `YYYY/VERSION/YYYYMMDDHHMI__VERSION__TICKET__description.sql`
