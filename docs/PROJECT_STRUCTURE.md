# Project Structure

## Module layout

```
dev-knowledge-platform/
├── common/          — shared entities, enums, exceptions, DTOs; depends on Spring Data JPA (for @Entity), validation, web, security (all as annotation/type support, not full autoconfiguration)
├── infra/           — shared Spring infrastructure: event base classes, composed annotations, MDC utilities, SlugService
├── content-service/ — categories, tags, and content items (Q&A, articles) — the knowledge corpus surfaced by the RAG pipeline
├── ai-service/      — RAG pipeline: embedding, vector search, LLM generation (LangChain4j)
├── social-service/  — friend graph (search visibility, requests, friendships, blocking) plus chat:
│                       groups/channels (open-add, role-gated) and 1:1 DMs (friend-gated)
├── api/             — REST endpoints, security, Liquibase migrations, Spring Boot entry point
└── gui/             — React 18 + TypeScript + MUI frontend (Vite)
```

Dependency order: `common` ← `infra` ← `content-service` ← `ai-service`/`social-service` ← `api`. `gui` is independent.
`content-service` and `social-service` mirror each other's shape (a business-logic module `api` depends on and
wires up via REST) — both depend only on `common` + `infra`, never on `api`, so neither can reuse `api`'s
repositories or services. `ai-service` additionally depends on `content-service`: `ContentEmbedding` has a real
`@ManyToOne` FK to `ContentItem`, and `ContentIngestionService.ingest(...)` takes a `ContentItem` parameter —
unlike `social-service`, which has zero compile-time coupling from `ai-service`.

---

## common

```
common/src/main/java/com/ttg/devknowledgeplatform/common/
├── dto/
│   ├── ConversationContext.java       — rolling summary + recent verbatim turns; primary RAG context type
│   └── ConversationTurn.java         — role + content record for a single message
├── entity/
│   ├── AbstractEntity.java           — audit columns (usrCreation, dteCreation, version, …)
│   ├── User.java                     — userUuid, email, username, password, firstName, lastName, profilePicture,
│   │                                    provider (UserProvider), role (UserRole), providerId, emailVerified, status
│   │                                    (UserStatus, presence), enabled, seedId (String, nullable, DB SEED_ID,
│   │                                    DKP-0016 — sole idempotency key for UserSeeder, api); referenced by FK from
│   │                                    social-service's FriendRequest/Friendship/UserBlock entities (which live
│   │                                    there, not here — see social-service section below)
│   ├── ChatSession.java              — userId, title, lastActivityAt, summary (TEXT); parent of ChatMessage rows
│   ├── ChatMessage.java              — role, content, turnIndex; child of ChatSession
│   └── SysParam.java                 — @Entity for SYS_PARAM; fields: name (ParamKey), value (TEXT), computedAt
├── enums/
│   ├── ChatProvider.java             — OPENAI, ANTHROPIC; selects LangChain4j builder family per chat model profile
│   ├── ParamKey.java                 — typed keys for SYS_PARAM.NAME; renaming a constant requires a DB migration;
│   │                                   includes PROMPT_INJECTION_PROTOTYPE_EMBEDDINGS (fingerprinted vector-list cache
│   │                                   for PromptGuardStage — see repository/service below)
│   └── UserRole.java
├── repository/
│   ├── SysParamRepository.java       — JpaRepository<SysParam, Integer>; findByName(ParamKey); moved here from
│   │                                    api/repository so ai-service (which cannot depend on api) can reach it
│   │                                    via SysParamService below
│   └── UserRepository.java           — JpaRepository<User, Integer> + JpaSpecificationExecutor<User>; moved here
│                                        from api/repository so content-service/social-service (neither of which
│                                        can depend on api) can reach it directly — this also retired
│                                        social-service's own SocialUserRepository, a near-duplicate that existed
│                                        only because this repository used to live in api; findBySeedId(String)
│                                        added for UserSeeder (api) idempotency
├── service/
│   ├── SysParamService.java          — interface: getValue(ParamKey), upsert(ParamKey, String); string-in/string-out,
│   │                                   no opinion on value encoding — callers own their own serialization format
│   └── impl/
│       └── SysParamServiceImpl.java  — find-or-create-and-save upsert pattern, shared by CorpusStatisticsServiceImpl (api)
│                                        and PromptGuardStage (ai-service)
└── exception/
    ├── ApiException.java
    ├── BusinessException.java
    ├── ErrorCode.java                — interface (getCode/getMessage/getHttpStatus); one enum per module owning
    │                                    errors implements it (CommonErrorCode here, ContentErrorCode in
    │                                    content-service, SocialErrorCode in social-service, AiErrorCode in
    │                                    ai-service, ChatErrorCode in api) — lets ApiException/BusinessException/
    │                                    GlobalExceptionHandler stay module-agnostic without a compile-time
    │                                    dependency back onto every feature module from common
    ├── CommonErrorCode.java          — AUTH_*/OAUTH_*/USER_*/OTP_*/VALIDATION_*/SERVER_*/RESOURCE_*/REQUEST_*/
    │                                    RATE_* codes (everything not owned by a single feature module)
    └── ResourceNotFoundException.java
```

Category/Tag/ContentItem/ContentItemTag/QuestionAnswer/Article entities and their enums
(ContentStatus/ContentType/TagStatus/QuestionDifficulty) used to live here; they moved to
`content-service` — see that module's section below and `CHANGELOG.md`.

---

## infra

```
infra/src/main/java/com/ttg/devknowledgeplatform/infra/
├── context/
│   └── MdcKeys.java              — MDC key constants shared across modules (e.g. TRACE_ID = "traceId")
├── event/
│   ├── ApplicationEventHandler.java  — marker interface; Find Implementations = full event bus registry across modules
│   ├── EventHandler.java             — composed @EventListener + @Async("asyncEventExecutor"); enforces async on
│   │                                    every listener and pins dispatch to a dedicated pool (bulkhead vs sseStreamExecutor)
│   └── AsyncEventHandler.java        — abstract base class (Template Method); provides async dispatch via @EventHandler,
│                                        MDC TRACE_ID binding (opt-in via resolveTraceId()), timing, and exception safety;
│                                        subclasses implement doHandle(); subclasses that need DB writes declare @Transactional themselves
└── service/
    ├── SlugService.java              — toSlug(String), generateUniqueSlug(...) (two overloads: create vs
    │                                    update-excluding-self); lives here (not content-service) because it's a
    │                                    generic utility content-service's services need but api can't be the home
    │                                    for, since content-service cannot depend on api
    ├── impl/
    │   └── SlugServiceImpl.java      — diacritic-stripping + incrementing-counter uniqueness resolution
    └── seed/
        └── CsvSeeder.java            — Template Method for flat, single-file CSV sources (read/iterate/
                                         skip-or-insert loop; subclasses supply alreadyExists()/buildEntity()/
                                         persist()); moved here from content-service once social-service's
                                         UserBlockSeeder needed it too — content-service and social-service are
                                         independent siblings that can't depend on each other, so the shared
                                         template moved to infra, which both already depend on (same reasoning
                                         as SlugService above). Used by content-service's CategorySeeder/
                                         TagSeeder, api's UserSeeder, and social-service's UserBlockSeeder.
```

---

## content-service

```
content-service/src/main/java/com/ttg/devknowledgeplatform/content/
├── entity/
│   ├── Category.java              — hierarchical; parent/children self-join; seedId (nullable) for CategorySeeder idempotency
│   ├── Tag.java                   — status (TagStatus); seedId (nullable) for TagSeeder idempotency
│   ├── ContentItem.java           — base content record (type, status, title, slug, category, viewCount,
│   │                                 publishedAt, qualityScore); seedId (nullable) for QuestionAnswerSeeder idempotency
│   ├── ContentItemTag.java        — join entity for content ↔ tag
│   ├── QuestionAnswer.java        — general dev-knowledge Q&A, not only interview prep;
│   │                                 difficulty/isCommon are nullable interview-specific metadata
│   └── Article.java               — body text; backs both ContentType.ARTICLE and .BLOG_POST
├── enums/
│   ├── ContentStatus.java         — DRAFT, PUBLISHED, …
│   ├── ContentType.java           — QUESTION_ANSWER, ARTICLE, BLOG_POST
│   ├── TagStatus.java             — ACTIVE, INACTIVE
│   └── QuestionDifficulty.java    — BEGINNER, INTERMEDIATE, ADVANCED
├── event/
│   └── ContentPublishedEvent.java — carries a ContentItem; currently has no publisher wired up (scaffold for a
│                                     future auto-index-on-publish flow — today indexing is admin-triggered via
│                                     api's IngestionController); listened for by api's ContentPublishedEventListener
├── repository/
│   ├── CategoryRepository.java / TagRepository.java / ContentItemRepository.java / ContentItemTagRepository.java
│   │   / QuestionAnswerRepository.java / ArticleRepository.java
│   └── spec/
│       └── CategorySpecification.java / TagSpecification.java / QuestionAnswerSpecification.java / ArticleSpecification.java
├── service/
│   ├── CategoryService.java / TagService.java / QuestionAnswerService.java / ArticleService.java — return
│   │   entities, not REST DTOs — api's Category/Tag/QuestionAnswer/ArticleMapper do entity→response mapping
│   │   (same split as social-service's FriendService → api's FriendMapper, and ai-service's RagQueryService →
│   │   api's ChatResponse)
│   ├── CategoryTreeNode.java      — record (Category + resolved children) returned by CategoryService.listTree();
│   │                                 api's CategoryMapper.toTreeNodeResponse() flattens it into CategoryTreeNodeResponse
│   ├── QuestionAnswerCommands.java / ArticleCommands.java — Create/Update input records mirroring api's
│   │   Create*Request/Update*Request field-for-field, without REST/validation annotations — api translates its
│   │   request DTOs into these before calling the service, avoiding a content-service → api DTO dependency
│   ├── seed/                      — startup data seeding; format chosen per content shape (moved here from api
│   │   since seeders write directly via repositories, the same as production service impls)
│   │   ├── CategorySeeder.java        — data/csv/categories.csv; identity by seedId; extends infra's CsvSeeder
│   │   ├── TagSeeder.java             — data/csv/tags.csv; identity by seedId; extends infra's CsvSeeder
│   │   └── QuestionAnswerSeeder.java  — data/question-answers/*.md (YAML front matter + markdown body);
│   │                                     does not extend CsvSeeder (one-file-per-record, different iteration shape)
│   └── impl/
│       └── CategoryServiceImpl.java / TagServiceImpl.java / QuestionAnswerServiceImpl.java / ArticleServiceImpl.java
└── exception/
    └── ContentErrorCode.java      — CATEGORY_*/TAG_*/QA_*/ARTICLE_* codes, implements common's ErrorCode interface
```

REST controllers, DTOs (`dto/content/*`), mappers, and the indexing/RAG orchestration layer
(`ContentIndexingService`, `IndexingQualityService`, `EmbeddingIndexService`, `IngestionController`,
`PublicContentController`, `ContentPublishedEventListener`) all stay in `api` — see the `api` section below.
The orchestration layer genuinely needs both `content-service` and `ai-service`, and `content-service` cannot
depend on `ai-service` (that would create a cycle, since `ai-service` already depends on `content-service` for
`ContentItem`), so it lives one layer up in `api`, which depends on both.

`DataSeedingRunner` (`api`) still runs the seeders above in order (category → tag → questionAnswer); the
actual seed data files (`data/csv/*.csv`, `data/question-answers/*.md`) stay under `api/src/main/resources/`
unchanged — only the Java seeder classes moved, following the same precedent as Liquibase migrations (see
Database section below).

Why the service layer was redesigned rather than just relocated: `CategoryService`/`TagService`/
`QuestionAnswerService`/`ArticleService` used to accept and return `api`'s own REST DTOs directly
(`CreateCategoryRequest`, `CategoryResponse`, `PagedResponse<...>`). Moving them into `content-service` as-is
would have made `content-service` depend on `api`'s DTOs while `api` depends on `content-service` — circular.
Every method now takes plain params or a content-service-owned command record and returns an entity or
`Page<Entity>`, matching the `FriendService` precedent; `api`'s controllers build the command from the request
DTO and its mappers convert the returned entity back to a response DTO.

---

## social-service

```
social-service/src/main/java/com/ttg/devknowledgeplatform/social/
├── entity/
│   ├── FriendRequest.java         — requester/addressee (User, common), status (FriendRequestStatus)
│   ├── Friendship.java            — user1/user2 (User), canonically ordered (user1.id < user2.id) so each
│   │                                 pair has exactly one row regardless of who sent the original request
│   ├── UserBlock.java             — blocker/blocked (User); directional, independent of Friendship/FriendRequest
│   ├── Group.java                 — name only; maps to table MESSAGE_GROUP (GROUP is a reserved word in
│   │                                 PostgreSQL). No ownerId column — the owner is whichever GroupMember row
│   │                                 holds role = OWNER, a single source of truth instead of a duplicated ref
│   ├── GroupMember.java           — group/user (Group/User), role (GroupMemberRole); one row per (group, user) pair
│   ├── Channel.java                — group (Group), name; unique per group, not globally. Every group member can
│   │                                 see every channel in this MVP — no private/restricted channel concept yet
│   ├── DmThread.java               — user1/user2 (User), canonically ordered exactly like Friendship;
│   │                                 lastMessageAt (denormalized, same reasoning as ChatSession.lastActivityAt —
│   │                                 avoids a MAX(dteCreation) aggregate to render "my DMs, most recent first")
│   ├── DmMessage.java              — dmThread, sender (User), messageType (MessageType), content + 4 nullable
│   │                                 attachment columns (attachmentObjectKey is a MinIO object key, not a URL —
│   │                                 same pattern as avatar images). content and the attachment columns are
│   │                                 independently nullable, so a message can carry text, an attachment, or both.
│   │                                 Ordered by dteCreation (inherited), not an explicit turn-index like
│   │                                 ChatMessage.turnIndex — that counter exists there to guarantee strict
│   │                                 single-writer USER/ASSISTANT alternation, which doesn't apply here
│   └── ChannelMessage.java         — same field shape as DmMessage, FKs to Channel instead of DmThread. Kept as
│                                     a separate table rather than unifying with DmMessage under one generic
│                                     "conversation" concept — mirrors keeping Friendship/UserBlock separate
│                                     rather than one generic "relationship" table (see CHANGELOG for the fork)
├── enums/
│   ├── FriendRequestStatus.java   — PENDING, ACCEPTED, REJECTED, CANCELLED
│   ├── RelationshipStatus.java    — STRANGER, REQUEST_SENT, REQUEST_RECEIVED, FRIENDS, BLOCKED; computed
│   │                                 (not persisted) per profile/search-result view from the viewer's perspective
│   ├── GroupMemberRole.java       — OWNER, ADMIN, MEMBER; exactly one OWNER per group
│   └── MessageType.java           — TEXT, IMAGE, FILE; tags the primary content for rendering only — text and
│                                     an attachment may coexist on one row regardless of this value
├── repository/
│   ├── FriendRequestRepository.java  — findPendingBetween (status-scoped); existsBetween (any status, either
│   │                                    direction — FriendGraphSeeder's idempotency guard)
│   ├── FriendshipRepository.java  — findFriendUserIds() used by the service for mutual-friend-count set intersection
│   ├── UserBlockRepository.java   — existsEitherDirection (UserBlockSeeder's idempotency guard)
│   ├── GroupRepository.java       — findAllForUser (joins through GroupMember; ordered by group id — no "recent
│   │                                 activity" definition locked for groups yet, unlike DmThread's lastMessageAt)
│   ├── GroupMemberRepository.java — findByGroupAndUser/existsByGroupAndUser — the membership+role lookup behind
│   │                                 every group/channel permission check
│   ├── ChannelRepository.java     — findByGroup; existsByGroupAndName (pre-check before create)
│   ├── DmThreadRepository.java    — findByUser1AndUser2 (canonicalized pair, same convention as
│   │                                 FriendshipRepository); findAllForUser ordered by lastMessageAt DESC
│   ├── DmMessageRepository.java   — findByDmThreadOrderByDteCreationDesc, paginated
│   ├── ChannelMessageRepository.java — findByChannelOrderByDteCreationDesc, paginated
│   └── spec/
│       └── UserSpecification.java — fuzzy username/name match, exact email match, excludes any user blocked
│                                     in either direction relative to the viewer
├── event/
│   ├── FriendRequestSentEvent.java     — record; published right after a pending FriendRequest is created
│   └── FriendRequestAcceptedEvent.java — record; published when a Friendship is created (explicit accept
│                                          or mutual auto-accept)
├── service/
│   ├── FriendService.java           — sendRequest, accept/reject/cancelRequest, unfriend, block/unblock,
│   │                                   getRelationshipStatus, countMutualFriends, listFriends/Incoming/Outgoing/
│   │                                   BlockedUsers, searchUsers; returns entities, not REST DTOs — api's
│   │                                   FriendMapper does entity→response mapping (same split as ai-service's
│   │                                   RagQueryService → api's ChatResponse)
│   ├── DmService.java               — sendMessage (lazy DmThread creation, friend-gated via
│   │                                   FriendService.getRelationshipStatus — collapses "not friends" and
│   │                                   "blocked" into the same rejection, never revealing which), listMyThreads,
│   │                                   listMessages (same not-found error whether the thread doesn't exist or
│   │                                   the caller isn't a participant)
│   ├── GroupService.java            — createGroup, addMember (open add, idempotent), removeMember (owner
│   │                                   protected; only the owner can remove an admin), leaveGroup (owner
│   │                                   blocked — no ownership-transfer story yet), changeRole (owner-only;
│   │                                   ownership itself not reassignable), createChannel, postMessage, plus
│   │                                   listMyGroups/listChannels/listMessages/isChannelMember (pure boolean
│   │                                   membership check, used by api's ws/StompAuthChannelInterceptor to
│   │                                   authorize a channel-topic subscription before the broker admits it)
│   ├── MessageAttachmentInput.java  — record: objectKey/mimeType/fileName/fileSize; shared optional-attachment
│   │                                   input for both DmService.sendMessage and GroupService.postMessage
│   ├── impl/
│   │   ├── FriendServiceImpl.java   — mutual-request auto-accept; block cascades (removes friendship + pending
│   │   │                              request between the pair before recording the block); mutual invisibility
│   │   │                              (a lookup of a user who has blocked the viewer throws USER_NOT_FOUND, same
│   │   │                              as a nonexistent UUID, never a distinguishable "blocked" error)
│   │   ├── DmServiceImpl.java       — resolveOrCreateThread canonicalizes the pair then find-or-creates; updates
│   │   │                              DmThread.lastMessageAt via DateUtils.getCurrentDateTime() on every send
│   │   └── GroupServiceImpl.java    — requireManagementRole/resolveMembership are the shared permission-check
│   │                                   helpers behind every group/channel method; requireManagementRole is a
│   │                                   Java 21 exhaustive switch (no default) over GroupMemberRole, same
│   │                                   technique FriendServiceImpl.requirePending uses for FriendRequestStatus —
│   │                                   standing in for a full State-pattern class hierarchy at a scale that
│   │                                   doesn't justify one
│   └── seed/                        — sample social-graph data for the Friend Management GUI (see
│       │                              docs/SEED_DATA_AUTHORING_GUIDE.md); requires api's UserSeeder to run first
│       ├── FriendGraphSeeder.java   — data/csv/friend-requests.csv (requesterId, addresseeId, status); an
│       │                              ACCEPTED row also inserts the matching Friendship, canonically ordered,
│       │                              mirroring FriendServiceImpl.acceptRequest's production behavior. Does
│       │                              NOT extend infra's CsvSeeder — an ACCEPTED row persists two entities,
│       │                              which doesn't fit CsvSeeder's one-entity-per-row shape
│       └── UserBlockSeeder.java     — data/csv/user-blocks.csv (blockerId, blockedId); extends infra's CsvSeeder
└── exception/
    └── SocialErrorCode.java         — FRIEND_*/DM_*/GROUP_*/CHANNEL_* codes, implements common's ErrorCode
                                        interface (moved out of common's CommonErrorCode). Renamed from
                                        FriendErrorCode once this module grew beyond just the friend graph — one
                                        enum per module (not per sub-domain), same shape as content-service's
                                        ContentErrorCode holding CATEGORY_*/TAG_*/QA_*/ARTICLE_* together
```

Read access to `User` (search, relationship resolution) goes through `common`'s own `UserRepository`
directly — no module-local wrapper repository needed, since `UserRepository` already lives in
`common` and extends `JpaSpecificationExecutor<User>` (for `UserSpecification` above).

`GroupService` and `DmService` are deliberately two services, not one — they gate access differently
(open-add + role checks vs. friend-required) and share no entities, so combining them would mix two
unrelated authorization models in one class. `DmService` depends on `FriendService` as a collaborator
(reusing its relationship lookup) rather than querying `FriendshipRepository`/`UserBlockRepository`
directly, avoiding a second implementation of the canonicalization + mutual-invisibility logic.
REST layer: `api`'s `GroupApi`/`GroupController` and `DmApi`/`DmController`, DTOs in `dto/messaging/`,
`MessagingMapper` — see the `api` section below. No upload endpoint yet for message attachments;
`MessageAttachmentRequest.objectKey` assumes the client already has a MinIO object key from
somewhere else.

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
│   ├── RagQueryException.java
│   └── AiErrorCode.java              — AI_* codes, implements common's ErrorCode interface (moved out of
│                                        common's CommonErrorCode)
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
    │                                               throws BusinessException(AiErrorCode.AI_MODEL_UNSUPPORTED) for an unconfigured id
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
│   ├── GroupApi.java                 — /api/v1/groups/**: create/list groups, add/remove/leave members,
│   │                                    change role, create/list channels; plus
│   │                                    /api/v1/channels/{channelId}/messages (post/list) — kept un-nested
│   │                                    from groupId since GroupService resolves the group from the channel
│   │                                    itself, so a groupId path segment there would be unvalidated;
│   │                                    delegates to social-service's GroupService
│   ├── DmApi.java                    — /api/v1/dms/**: send message (lazy thread creation), list my
│   │                                    threads, list a thread's messages; delegates to social-service's
│   │                                    DmService
│   └── impl/
│       ├── ChatController.java            — POST /api/v1/chat, POST /api/v1/chat/stream;
│       │                                    builds RagFilter from ChatRequest and passes to RagQueryService
│       ├── EmbeddingIndexController.java  — delegates to EmbeddingIndexService; no mapping logic
│       ├── PipelineMetricsController.java — delegates to PipelineMetricsSummaryService; no mapping logic
│       ├── UserController.java            — see UserApi above
│       ├── FriendController.java          — see FriendApi above
│       ├── GroupController.java           — see GroupApi above
│       ├── DmController.java              — see DmApi above
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
├── dto/                               — REST request/response DTOs, colocated per feature (each subpackage
│   │                                    mirrors the feature module its DTOs front); `dto/admin/` is for
│   │                                    admin-tooling DTOs that don't belong to one extracted feature module
│   │                                    (currently just EmbeddingIndexItemResponse)
│   ├── chat/
│   │   ├── ChatRequest.java          — question, sessionId, sourceTypes, categoryId, tags, chatModel
│   │   │                                (chatModel: optional model id, e.g. "claude-sonnet-5"; null = server default)
│   │   ├── ChatResponse.java
│   │   ├── ChatSessionHistoryDto.java
│   │   └── ChatSessionSummaryDto.java
│   ├── content/                      — fronts content-service's Category/Tag/QuestionAnswer/Article services
│   │   ├── CategoryResponse.java / CreateCategoryRequest.java / UpdateCategoryRequest.java
│   │   ├── CategoryTreeNodeResponse.java  — recursive JSON tree shape; CategoryMapper.toTreeNodeResponse()
│   │   │                                    flattens content-service's CategoryTreeNode (Category + children) into this
│   │   ├── TagResponse.java / CreateTagRequest.java / UpdateTagRequest.java
│   │   ├── QuestionAnswerResponse.java / CreateQuestionAnswerRequest.java / UpdateQuestionAnswerRequest.java
│   │   └── ArticleResponse.java / CreateArticleRequest.java / UpdateArticleRequest.java
│   ├── friend/                       — Java records (immutable), not the older Lombok @Data/@Builder style
│   │   ├── UserSummaryResponse.java       — userUuid, username, firstName, lastName, profilePicture, status;
│   │   │                                    nested inside the three below rather than repeating the fields
│   │   ├── UserSearchResultResponse.java  — UserSummaryResponse + relationshipStatus + mutualFriendCount
│   │   ├── FriendRequestResponse.java     — id, requester, addressee, status, createdAt
│   │   └── FriendSummaryResponse.java     — UserSummaryResponse + friendsSince
│   ├── messaging/                    — fronts social-service's GroupService/DmService; deliberately not
│   │   │                                dto/chat/, which already means the unrelated AI-RAG chat feature
│   │   ├── GroupResponse.java / CreateGroupRequest.java
│   │   ├── GroupMemberResponse.java       — UserSummaryResponse (dto/friend) + role (String) + joinedAt
│   │   ├── ChannelResponse.java / CreateChannelRequest.java
│   │   ├── ChangeRoleRequest.java         — role: String, parsed to GroupMemberRole in GroupController
│   │   ├── ChannelMessageResponse.java / DmMessageResponse.java — same shape: sender (UserSummaryResponse),
│   │   │                                    messageType (String), content, attachment (nullable), createdAt
│   │   ├── DmThreadResponse.java          — otherUser (UserSummaryResponse, resolved relative to the
│   │   │                                    viewer), lastMessageAt
│   │   ├── SendMessageRequest.java        — content + attachment (MessageAttachmentRequest), both optional;
│   │   │                                    at least one required, enforced by the service not bean validation
│   │   ├── MessageAttachmentRequest.java  — objectKey/mimeType/fileName/fileSize; objectKey must already
│   │   │                                    exist in MinIO — no upload endpoint exists yet, this only
│   │   │                                    references an object already there
│   │   └── MessageAttachmentResponse.java — url (presigned, resolved from objectKey at read time),
│   │                                        mimeType, fileName, fileSize
│   └── admin/
│       └── EmbeddingIndexItemResponse.java — ContentItem + embedding stats, admin-only
├── exception/
│   └── ChatErrorCode.java            — CHAT_* codes, implements common's ErrorCode interface (moved out of
│                                        common's CommonErrorCode; owned here since ChatSessionServiceImpl,
│                                        below, is the sole owner of chat-session business logic)
├── mapper/                           — MapStruct mappers (DTO ↔ entity)
│   ├── CategoryMapper.java / TagMapper.java / QuestionAnswerMapper.java / ArticleMapper.java — map
│   │                                    content-service entities to dto/content/*
│   ├── FriendMapper.java             — abstract class (not interface) like UserMapper — needs an injected
│   │                                    StorageService for presigned avatar URLs, and MapStruct interfaces
│   │                                    can't hold instance fields; maps social-service entities to dto/friend/*
│   └── MessagingMapper.java           — abstract class; maps social-service's Group/Channel/DM entities to
│                                        dto/messaging/*. @Mapper(uses = FriendMapper.class) so User →
│                                        UserSummaryResponse (incl. avatar presigned URL) isn't duplicated;
│                                        also injects FriendMapper directly as a field to call
│                                        toUserSummary() from a hand-written expression (otherUser mapping
│                                        for DmThreadResponse) — MapStruct's implicit "uses" delegation only
│                                        covers same-named auto-mapped fields, not calls from your own
│                                        expression code, which is spliced as raw Java into the generated class
├── repository/
│   └── …                             — ChatSessionRepository, ChatMessageRepository (UserRepository moved
│                                        to common — see that module's section);
│                                        Category/Tag/ContentItem*/QuestionAnswer/Article repositories +
│                                        specifications moved to content-service (see that module's section)
├── security/                         — JwtProvider, OAuth2 handlers, UserUtils
│   └── jwt/
│       ├── TokenClaims.java          — sealed interface; typed JWT claim shape, permits
│       │                                AccessTokenClaims/RefreshTokenClaims; TokenClaims.parse(Claims)
│       ├── AccessTokenClaims.java    — record: userUuid, email, username, role
│       └── RefreshTokenClaims.java   — record: userUuid, username, role (type=refresh claim added
│                                        by toClaimsMap(); no email claim, unlike access tokens)
├── ws/                                — STOMP-over-WebSocket live push for group/DM chat (AI chat
│   │                                    keeps its own SSE stream; unrelated, additive infra here)
│   ├── WebSocketConfig.java          — @EnableWebSocketMessageBroker; registers /ws with NO SockJS
│   │                                    fallback (real handshake, not an emulated transport); simple
│   │                                    broker on /topic + /queue; /app client-send prefix; wires
│   │                                    StompAuthChannelInterceptor + CurrentUserIdMessageArgumentResolver
│   ├── StompAuthChannelInterceptor.java — CONNECT: authenticates the JWT passed as a STOMP
│   │                                       Authorization header (handshake itself is permitAll —
│   │                                       browsers can't set headers on the handshake request),
│   │                                       reusing JwtTokenProvider the same way JwtAuthenticationFilter
│   │                                       does for REST. SUBSCRIBE: authorizes /topic/channels/{id}
│   │                                       via GroupService.isChannelMember — the simple broker has no
│   │                                       per-destination ACL of its own. DMs need no equivalent
│   │                                       check (convertAndSendToUser's private queue has no public
│   │                                       topic string to subscribe to)
│   ├── WsCurrentUser.java            — package-private; resolves Integer userId from the STOMP
│   │                                    session's Principal (set at CONNECT), mirroring
│   │                                    CurrentUserIdArgumentResolver's REST-side lookup
│   ├── CurrentUserIdMessageArgumentResolver.java — Spring Messaging's HandlerMethodArgumentResolver
│   │                                    (different interface than Spring MVC's own of the same
│   │                                    name); lets @MessageMapping methods accept the same
│   │                                    @CurrentUserId Integer userId REST controllers use
│   ├── WsErrorResponse.java          — record: errorCode, message; sent to a client's private
│   │                                    /queue/errors by each controller's @MessageExceptionHandler
│   ├── GroupMessagingController.java — /app/channels/{channelId}/messages → GroupService.postMessage
│   │                                    → broadcast to /topic/channels/{channelId}. Safe to map
│   │                                    ChannelMessage.sender/.channel right after the service call
│   │                                    returns — both are the exact objects GroupServiceImpl already
│   │                                    fetched by ID in that same call, not lazy proxies
│   └── DmMessagingController.java    — /app/dms/{recipientUuid}/messages → DmService.sendMessage →
│                                        convertAndSendToUser to both participants' /queue/dms.
│                                        Deliberately does NOT read
│                                        message.getDmThread().getUser1()/getUser2() to find the
│                                        other participant — for an existing thread those are
│                                        genuine lazy proxies, and STOMP handling isn't covered by
│                                        Open-Session-In-View the way REST controllers are (OSIV is a
│                                        servlet-filter mechanism; a WebSocket message never goes
│                                        through it). Resolves both usernames directly instead
│                                        (sender's from the already-authenticated Principal,
│                                        recipient's via a fresh UserRepository lookup)
├── service/
│   ├── ChatSessionService.java       — getOrCreateSessionId, getConversationContext (primary),
│   │                                   getRecentTurns, addTurn (triggers rolling summary), listSessions, getHistory
│   ├── ContentIndexingService.java   — index / reindex / deleteIndex per contentItemId
│   ├── EmbeddingIndexService.java    — list(page,size,q,contentType,contentStatus,indexed) → PagedResponse<EmbeddingIndexItemResponse>
│   ├── IndexingQualityService.java   — assess(contentItemId, contentType) → QualityVerdict; centroid distance check at indexing time
│   ├── QualityVerdict.java           — record: boolean lowQuality, float score; factories pass/flag/skipped
│   ├── seed/
│   │   ├── DataSeedingRunner.java        — ApplicationRunner, @ConditionalOnProperty(app.seed.enabled);
│   │   │                                   runs seeders in order: category → tag → questionAnswer → user →
│   │   │                                   friend graph → blocks. CategorySeeder/TagSeeder/QuestionAnswerSeeder
│   │   │                                   live in content-service/service/seed/, FriendGraphSeeder/
│   │   │                                   UserBlockSeeder in social-service/service/seed/ (see those modules'
│   │   │                                   sections) — this runner just injects and calls all of them
│   │   └── UserSeeder.java               — data/csv/users.csv; identity by seedId; extends infra's CsvSeeder;
│   │                                        lives here (not common) since it needs PasswordEncoder to hash the
│   │                                        shared demo password every seeded account gets, matching where
│   │                                        UserService's own auth-flow logic already lives
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
│   ├── tags.csv                           — id, name, status
│   ├── users.csv                          — id, email, username, firstName, lastName (UserSeeder, api);
│   │                                         20 sample login-able accounts for the Friend Management GUI
│   ├── friend-requests.csv                — requesterId, addresseeId, status (FriendGraphSeeder,
│   │                                         social-service); references users.csv rows by id
│   └── user-blocks.csv                    — blockerId, blockedId (UserBlockSeeder, social-service);
│                                             references users.csv rows by id
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
- `CATEGORY` / `TAG` / `CONTENT_ITEM` / `CONTENT_ITEM_TAG` / `QUESTION_ANSWER` / `ARTICLE` — backing
  `content-service`'s entities of the same names (schema unchanged by the module extraction — see `CHANGELOG.md`)
- `FRIEND_REQUEST` / `FRIENDSHIP` / `USER_BLOCK` (DKP-0015) — friend graph, backing `social-service`'s
  entities of the same names. `FRIENDSHIP` stores each pair once with `USER_ID_1 < USER_ID_2` enforced by
  a check constraint. `FRIEND_REQUEST` has a partial unique index on the unordered pair
  `WHERE STATUS = 'PENDING'` — only pending rows are constrained, so a rejected/cancelled request doesn't
  block a later re-request. `USER_BLOCK` is directional (no implied reverse row). None of the three have
  their own `SEED_ID` (see `DKP-0016` below) — a pair's identity has no editable-field equivalent to
  `NAME`/`EMAIL` that could invalidate a pair-based idempotency check.
- `MESSAGE_GROUP` / `GROUP_MEMBER` / `CHANNEL` / `DM_THREAD` / `DM_MESSAGE` / `CHANNEL_MESSAGE` (DKP-0019)
  — chat MVP, backing `social-service`'s entities of the same names (`Group` maps to `MESSAGE_GROUP`,
  not `GROUP` — a reserved word in PostgreSQL). `DM_THREAD` reuses `FRIENDSHIP`'s canonical-pair-ordering
  convention (`USER_ID_1 < USER_ID_2` check constraint). `DM_MESSAGE`/`CHANNEL_MESSAGE` are deliberately
  separate tables rather than one unified "conversation" concept (same reasoning as keeping `FRIENDSHIP`/
  `USER_BLOCK` separate); both have independently-nullable `CONTENT`/`ATTACHMENT_*` columns so a message
  can carry text, an attachment, or both. `ON DELETE CASCADE` from messages up through channel/group and
  from members up through group.
- `USER.SEED_ID` (DKP-0016, nullable, unique index) — same pattern as `DKP-0013`'s `CATEGORY`/`TAG`/
  `CONTENT_ITEM`; sole idempotency key for `UserSeeder`'s (`api`) 20 sample login-able accounts.
- Migrations: `api/src/main/java/com/ttg/devknowledgeplatform/database/sql/` (Liquibase config lives in
  `api` regardless of which module owns the entities the migration backs — `social-service`'s tables are
  migrated from here too, same as `ai-service`'s)
  - Naming: `YYYY/VERSION/YYYYMMDDHHMI__VERSION__TICKET__description.sql`
