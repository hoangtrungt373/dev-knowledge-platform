# Project Structure

## Module layout

```
dev-knowledge-platform/
├── common/           — shared entities, enums, exceptions, base DTOs (PagedResponse, CustomOAuth2User),
│                        the @CurrentUserId annotation; depends on Spring Data JPA (for @Entity), validation,
│                        web, security (all as annotation/type support, not full autoconfiguration)
├── infra/            — shared Spring infrastructure: event base classes, composed annotations, MDC utilities,
│                        SlugService, StorageService (MinIO), Redis cache TTL config
├── content-service/  — categories, tags, and content items (Q&A, articles) — the knowledge corpus surfaced by
│                        the RAG pipeline; owns its own REST layer, mappers, and DTOs
├── ai-service/       — RAG pipeline (embedding, vector search, LLM generation via LangChain4j), the RAG-chat
│                        REST feature, and the content+AI indexing orchestration layer (own REST layer too)
├── identity-service/ — authentication (local + OAuth2/OIDC login), JWT issuance, OTP-gated registration,
│                        and pure user-profile mutation (update profile, avatar upload)
├── social-service/   — friend graph (search visibility, requests, friendships, blocking) plus chat:
│                        groups/channels (open-add, role-gated) and 1:1 DMs (friend-gated); own REST layer,
│                        including the relationship-enriched user-directory endpoints (search, public profile)
├── gateway/          — security/JWT-filter/STOMP transport wiring, Liquibase migrations, Spring Boot entry
│                        point. Holds **zero REST controllers of its own** (renamed from `api` once the last
│                        one moved out — see `docs/CHANGELOG.md`)
└── gui/              — React 18 + TypeScript + MUI frontend (Vite)
```

Dependency order: `common` ← `infra` ← `content-service` ← `ai-service`; `infra` ← `identity-service` ←
`social-service`. `content-service`/`identity-service` are parallel siblings depending only on
`common`+`infra`; `ai-service` and `social-service` are each allowed a single, real, one-directional
dependency on a sibling module (`ai-service` → `content-service`, `social-service` → `identity-service`) —
never the reverse. `gateway` depends on all four feature modules; it's the only module allowed to depend
on more than one, reserved for orchestration that needs two feature modules with **no** dependency
relationship possible between them in either direction — currently nothing qualifies, which is why
`gateway` has no REST layer of its own today. `gui` is independent of the whole Java reactor.

Each of `content-service`/`social-service`/`ai-service`/`identity-service` owns its own full vertical slice —
entities/services *and* REST controllers, DTOs, MapStruct mappers — rather than the earlier shape where
`api` (now `gateway`) centralized every controller/DTO/mapper regardless of which module owned the
underlying entity. That centralized shape kept these modules transport-agnostic; the vertical-slice shape
trades that away deliberately, in favor of each module being closer to an independently-deployable unit
ahead of an eventual microservices split (see `docs/CHANGELOG.md`'s `[Unreleased]` entries for the full
rationale and what moved).

Two real one-directional sibling dependencies exist, both following the same shape — a downstream module
reaching into an upstream one for a genuine data/logic need, never the reverse:
- `ai-service` → `content-service`: `ContentEmbedding` has a real `@ManyToOne` FK to `ContentItem`, and
  `ContentIngestionService.ingest(...)` takes a `ContentItem` parameter. This is also why the content+AI
  indexing orchestration layer (`IngestionApi`, `EmbeddingIndexApi`, `PublicContentApi`) lives in
  `ai-service` rather than `gateway`: `ai-service` is the one module (besides `gateway`) that can already
  see both `content-service` and itself.
- `social-service` → `identity-service`: `UserApi`'s `search`/`getPublicProfile` endpoints (in
  `social-service`) need `identity-service`'s `UserService`/`UserMapper` for the base profile lookup before
  applying `social-service`'s own `FriendService` relationship enrichment. `identity-service` stays a pure
  `common`+`infra` leaf specifically so this dependency is safe in this one direction — the reverse
  (`identity-service` depending on `social-service`) would invert the usual auth-is-foundational hierarchy
  and mix a social-graph view into an auth module.

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
│   │                                    DKP-0016 — sole idempotency key for UserSeeder, identity-service); referenced by FK from
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
│   │                                    gateway/repository (named api/repository at the time) so ai-service
│   │                                    (which cannot depend on gateway) can reach it via SysParamService below
│   └── UserRepository.java           — JpaRepository<User, Integer> + JpaSpecificationExecutor<User>; moved here
│                                        from gateway/repository (named api/repository at the time) so
│                                        content-service/social-service (neither of which can depend on
│                                        gateway) can reach it directly — this also retired social-service's
│                                        own SocialUserRepository, a near-duplicate that existed only because
│                                        this repository used to live in gateway; findBySeedId(String) added
│                                        for UserSeeder (identity-service) idempotency
├── service/
│   ├── SysParamService.java          — interface: getValue(ParamKey), upsert(ParamKey, String); string-in/string-out,
│   │                                   no opinion on value encoding — callers own their own serialization format
│   └── impl/
│       └── SysParamServiceImpl.java  — find-or-create-and-save upsert pattern, shared by CorpusStatisticsServiceImpl (ai-service)
│                                        and PromptGuardStage (ai-service)
└── exception/
    ├── ApiException.java
    ├── BusinessException.java
    ├── ErrorCode.java                — interface (getCode/getMessage/getHttpStatus); one enum per module owning
    │                                    errors implements it (CommonErrorCode here, ContentErrorCode in
    │                                    content-service, SocialErrorCode in social-service, AiErrorCode and
    │                                    ChatErrorCode in ai-service) — lets ApiException/BusinessException/
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
├── config/
│   ├── storage/{StorageConfig,StorageProperties}.java — MinioClient bean + app.storage.* properties;
│   │                                    moved here from gateway (named api at the time) alongside
│   │                                    StorageService below
│   ├── cache/{CacheNames,CacheTtlProperties}.java — Redis cache-name constants + app.cache.* TTL
│   │                                    binding; shared by `identity-service`'s `StateTokenServiceImpl`
│   │                                    and `gateway`'s `RedisCacheConfig`, two modules that can't
│   │                                    depend on each other
│   └── thread/{AsyncEventThreadPoolConfig,AsyncEventThreadPoolProperties}.java — the
│                                        asyncEventExecutor bean (app.threads.async-event.*); moved
│                                        here from gateway since this module's own event/ framework
│                                        (below) is what actually owns this pool's purpose. gateway's
│                                        sseStreamExecutor (a separate bulkhead) stays there.
└── service/
    ├── SlugService.java              — toSlug(String), generateUniqueSlug(...) (two overloads: create vs
    │                                    update-excluding-self); lives here (not content-service) because it's a
    │                                    generic utility content-service's services need but gateway can't be the
    │                                    home for, since content-service cannot depend on gateway
    ├── StorageService.java (+ impl/) — MinIO upload/presigned-URL/delete; moved here from gateway
    │                                    because both `social-service` (`FriendMapper`/`MessagingMapper`)
    │                                    and `identity-service` (`UserMapper`, avatar upload) need it and
    │                                    neither can depend on the other
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
                                         TagSeeder, identity-service's UserSeeder, and social-service's UserBlockSeeder.
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
│                                     ai-service's IngestionController); listened for by ai-service's ContentPublishedEventListener
├── repository/
│   ├── CategoryRepository.java / TagRepository.java / ContentItemRepository.java / ContentItemTagRepository.java
│   │   / QuestionAnswerRepository.java / ArticleRepository.java
│   └── spec/
│       └── CategorySpecification.java / TagSpecification.java / QuestionAnswerSpecification.java / ArticleSpecification.java
├── service/
│   ├── CategoryService.java / TagService.java / QuestionAnswerService.java / ArticleService.java — return
│   │   entities, not REST DTOs — this module's own Category/Tag/QuestionAnswer/ArticleMapper (below) do
│   │   entity→response mapping (same split as social-service's FriendService → its own FriendMapper, and
│   │   ai-service's RagQueryService → its own ChatResponse)
│   ├── CategoryTreeNode.java      — record (Category + resolved children) returned by CategoryService.listTree();
│   │                                 this module's own CategoryMapper.toTreeNodeResponse() flattens it into CategoryTreeNodeResponse
│   ├── QuestionAnswerCommands.java / ArticleCommands.java — Create/Update input records mirroring this
│   │   module's own Create*Request/Update*Request field-for-field, without REST/validation annotations —
│   │   the controllers below translate request DTOs into these before calling the service, keeping the
│   │   service layer decoupled from the REST/JSON contract even though both now live in the same module
│   ├── seed/                      — startup data seeding; format chosen per content shape (moved here from
│   │   `gateway`, back when it was named `api`, since seeders write directly via repositories, the same
│   │   as production service impls)
│   │   ├── CategorySeeder.java        — data/csv/categories.csv; identity by seedId; extends infra's CsvSeeder
│   │   ├── TagSeeder.java             — data/csv/tags.csv; identity by seedId; extends infra's CsvSeeder
│   │   └── QuestionAnswerSeeder.java  — data/question-answers/*.md (YAML front matter + markdown body);
│   │                                     does not extend CsvSeeder (one-file-per-record, different iteration shape)
│   └── impl/
│       └── CategoryServiceImpl.java / TagServiceImpl.java / QuestionAnswerServiceImpl.java / ArticleServiceImpl.java
├── exception/
│   └── ContentErrorCode.java      — CATEGORY_*/TAG_*/QA_*/ARTICLE_* codes, implements common's ErrorCode interface
├── api/                           — admin CRUD REST layer (moved in from `gateway`, named `api` at
│                                     the time — see CHANGELOG)
│   ├── CategoryApi.java / TagApi.java / ArticleApi.java / QuestionAnswerApi.java
│   └── impl/                      — CategoryController / TagController / ArticleController / QuestionAnswerController
├── mapper/                        — MapStruct: CategoryMapper / TagMapper / ArticleMapper / QuestionAnswerMapper
│                                     (entity ↔ dto/*); `ArticleMapper`/`QuestionAnswerMapper` are also used
│                                     directly by `ai-service`'s `PublicContentController` (already-allowed
│                                     dependency direction — `ai-service` → `content-service`)
└── dto/                           — flat (not nested under dto/content/): CategoryResponse/CreateCategoryRequest/
                                      UpdateCategoryRequest, CategoryTreeNodeResponse, TagResponse/CreateTagRequest/
                                      UpdateTagRequest, ArticleResponse/CreateArticleRequest/UpdateArticleRequest,
                                      QuestionAnswerResponse/CreateQuestionAnswerRequest/UpdateQuestionAnswerRequest
```

The indexing/RAG orchestration layer (`ContentIndexingService`, `IndexingQualityService`,
`EmbeddingIndexService`, `IngestionApi`/`Controller`, `PublicContentApi`/`Controller`) and the read-only
public content-browsing endpoints now live in `ai-service` — see that module's section. It genuinely needs
both `content-service` and `ai-service`, and since `ai-service` already depends on `content-service` for
`ContentItem`, it lives there rather than needing `gateway`. `ContentPublishedEventListener` moved to
`ai-service` too (co-located with its own `PipelineCompletedEvent`/`Listener`), since it just calls that
module's own `ContentIndexingService` — no `gateway`-specific dependency ever justified keeping it there.

`ArticleController`/`QuestionAnswerController` resolve the authenticated principal's author id via `common`'s
`UserRepository.findByEmail(...)` directly, not `identity-service`'s `UserService` — `content-service` must
never depend on `identity-service`, and `UserRepository` living in `common` exists specifically so any module
can resolve a `User` by identifier without depending on the module that owns auth-flow business logic.

`DataSeedingRunner` (`gateway`) still runs the seeders above in order (category → tag → questionAnswer);
the actual seed data files (`data/csv/*.csv`, `data/question-answers/*.md`) stay under
`gateway/src/main/resources/` unchanged — only the Java seeder classes moved, following the same precedent
as Liquibase migrations (see Database section below).

Why the service layer was redesigned rather than just relocated: `CategoryService`/`TagService`/
`QuestionAnswerService`/`ArticleService` used to accept and return `gateway`'s own REST DTOs directly
(`CreateCategoryRequest`, `CategoryResponse`, `PagedResponse<...>`) back when this module's REST layer still
lived there. Moving them into `content-service` as-is would have made `content-service` depend on
`gateway`'s DTOs while `gateway` depends on `content-service` — circular. Every method now takes plain
params or a content-service-owned command record and returns an entity or `Page<Entity>`, matching the
`FriendService` precedent; this module's own controllers build the command from the request DTO and its
mappers convert the returned entity back to a response DTO.

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
│   ├── FriendRequestAcceptedEvent.java — record; published when a Friendship is created (explicit accept
│   │                                      or mutual auto-accept)
│   ├── FriendRequestSentEventListener.java     — moved in from gateway; currently just logs (seam for a
│   │                                      future in-app/email notification)
│   └── FriendRequestAcceptedEventListener.java — moved in from gateway; currently just logs
├── service/
│   ├── FriendService.java           — sendRequest, accept/reject/cancelRequest, unfriend, block/unblock,
│   │                                   getRelationshipStatus, countMutualFriends, listFriends/Incoming/Outgoing/
│   │                                   BlockedUsers, searchUsers; returns entities, not REST DTOs — this
│   │                                   module's own FriendMapper does entity→response mapping
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
│   │                                   membership check, used by gateway's StompAuthChannelInterceptor to
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
│       │                              docs/SEED_DATA_AUTHORING_GUIDE.md); requires identity-service's UserSeeder to run first
│       ├── FriendGraphSeeder.java   — data/csv/friend-requests.csv (requesterId, addresseeId, status); an
│       │                              ACCEPTED row also inserts the matching Friendship, canonically ordered,
│       │                              mirroring FriendServiceImpl.acceptRequest's production behavior. Does
│       │                              NOT extend infra's CsvSeeder — an ACCEPTED row persists two entities,
│       │                              which doesn't fit CsvSeeder's one-entity-per-row shape
│       └── UserBlockSeeder.java     — data/csv/user-blocks.csv (blockerId, blockedId); extends infra's CsvSeeder
├── exception/
│   └── SocialErrorCode.java         — FRIEND_*/DM_*/GROUP_*/CHANNEL_* codes, implements common's ErrorCode
│                                       interface (moved out of common's CommonErrorCode). Renamed from
│                                       FriendErrorCode once this module grew beyond just the friend graph — one
│                                       enum per module (not per sub-domain), same shape as content-service's
│                                       ContentErrorCode holding CATEGORY_*/TAG_*/QA_*/ARTICLE_* together
├── api/                              — REST + STOMP layer (moved in from `gateway`, named `api` at
│   │                                    the time — see CHANGELOG)
│   ├── FriendApi.java / GroupApi.java / DmApi.java — REST
│   ├── GroupMessagingApi.java / DmMessagingApi.java — STOMP counterparts (send-path only; reads stay REST)
│   ├── UserApi.java                  — GET /public/{userUuid}, GET /search — a **second** `UserApi`,
│   │                                    distinct from `identity-service`'s own (same `/api/v1/users` base
│   │                                    mapping, different two methods: `updateProfile`/`uploadAvatar` live
│   │                                    there instead). The one place in the whole reactor where a
│   │                                    controller reaches across into a sibling module
│   │                                    (`identity-service`'s `UserService`/`UserMapper`, for the base
│   │                                    profile lookup) before applying this module's own `FriendService`
│   │                                    enrichment
│   └── impl/                         — FriendController / GroupController / DmController /
│                                        GroupMessagingController / DmMessagingController / UserController
├── mapper/                           — MapStruct: FriendMapper, MessagingMapper (both abstract classes —
│                                        need an injected `infra`-owned `StorageService` for presigned avatar
│                                        URLs, and MapStruct interfaces can't hold instance fields);
│                                        `MessagingMapper` uses `FriendMapper` for User → UserSummaryResponse
└── dto/
    ├── friend/                       — Java records: UserSummaryResponse, UserSearchResultResponse,
    │                                    FriendRequestResponse, FriendSummaryResponse
    └── messaging/                    — GroupResponse/CreateGroupRequest, GroupMemberResponse, ChannelResponse/
                                         CreateChannelRequest, ChangeRoleRequest, ChannelMessageResponse,
                                         DmMessageResponse, DmThreadResponse, SendMessageRequest,
                                         MessageAttachmentRequest/Response, WsErrorResponse (STOMP error payload)
```

Read access to `User` (search, relationship resolution) goes through `common`'s own `UserRepository`
directly — no module-local wrapper repository needed, since `UserRepository` already lives in
`common` and extends `JpaSpecificationExecutor<User>` (for `UserSpecification` above). STOMP transport
wiring (`WebSocketConfig`, `StompAuthChannelInterceptor`, `CurrentUserIdMessageArgumentResolver`) stays in
`gateway` — edge/transport infra, not a `social-service` concern, same split as `SecurityConfig`/
`JwtAuthenticationFilter` staying in `gateway` while `identity-service` owns the actual auth business logic.

`GroupService` and `DmService` are deliberately two services, not one — they gate access differently
(open-add + role checks vs. friend-required) and share no entities, so combining them would mix two
unrelated authorization models in one class. `DmService` depends on `FriendService` as a collaborator
(reusing its relationship lookup) rather than querying `FriendshipRepository`/`UserBlockRepository`
directly, avoiding a second implementation of the canonicalization + mutual-invisibility logic.
REST layer: this module's own `GroupApi`/`GroupController` and `DmApi`/`DmController`, DTOs in
`dto/messaging/`, `MessagingMapper` — see `api/` above. No upload endpoint yet for message attachments;
`MessageAttachmentRequest.objectKey` assumes the client already has a MinIO object key from
somewhere else. `UserApi`/`UserController` (also `api/` above) is this module's one dependency on
`identity-service` — see the Module layout section's rationale for why that direction is safe.

---

## ai-service

```
ai-service/src/main/java/com/ttg/devknowledgeplatform/ai/
├── api/                       — REST layer, moved in from `gateway` (named `api` at the time —
│   │                             content+AI orchestration and the self-contained chat feature are
│   │                             both owned here now — see ai-service/CLAUDE.md for why the old
│   │                             "stays in gateway" rule no longer applies to this specific module pairing)
│   ├── ChatApi.java           — /api/v1/chat: chat(), chatStream() (SSE), listSessions(), getSessionHistory()
│   ├── IngestionApi.java      — /api/v1/admin/indexing: index(), indexAll(), deleteIndex(), refreshCorpus(); class-level @PreAuthorize("hasRole('ADMIN')")
│   ├── EmbeddingIndexApi.java — /api/v1/admin/embeddings: list() — paged, filterable content+embedding-stats view
│   ├── PipelineMetricsApi.java — /api/v1/admin/pipeline-metrics: getSummary(MetricsPeriod)
│   ├── PublicContentApi.java  — /api/v1/public: listQuestionAnswers/getQuestionAnswerBySlug/listArticles/getArticleBySlug
│   │                             (read-only, unauthenticated; fronts content-service's ArticleService/QuestionAnswerService
│   │                             via content-service's own ArticleMapper/QuestionAnswerMapper + content.dto.* DTOs)
│   └── impl/
│       ├── ChatController.java            — orchestrates RagQueryService + ChatSessionService + SseStreamTemplate
│       ├── IngestionController.java       — delegates to ContentIndexingService + CorpusStatisticsService
│       ├── EmbeddingIndexController.java  — delegates to EmbeddingIndexService
│       ├── PipelineMetricsController.java — delegates to PipelineMetricsSummaryService
│       └── PublicContentController.java   — delegates to ArticleService/QuestionAnswerService; increments view count
├── config/
│   ├── sse/
│   │   ├── SseStreamTemplate.java  — reusable SSE-endpoint helper; owns SSE_TIMEOUT_MS (60_000L) —
│   │   │                             gateway's WebMvcConfig.configureAsyncSupport reads this constant
│   │   │                             (not the other way round: ai-service must never depend on gateway)
│   │   └── SseEmitterWriter.java   — guards every SSE write: disconnect check, IOException handling, double-complete guard
│   ├── chat/
│   │   ├── ChatSessionProperties.java — @ConfigurationProperties at app.chat.session.*; ttlHours,
│   │   │                                 summaryThresholdPairs, summaryTriggerIntervalPairs, summaryRecentWindowPairs
│   │   ├── ChatRateLimiter.java        — per-user Bucket4j token bucket (Redis-backed via
│   │   │                                 LettuceBasedProxyManager), moved in from gateway alongside
│   │   │                                 ChatController — co-locates rate limiting with the endpoint
│   │   │                                 it protects
│   │   └── RateLimitProperties.java    — @ConfigurationProperties at app.ai.rate-limit; requestsPerMinute
│   │                                     (10), requestsPerHour (100), bucketExpiration (PT2H)
│   ├── web/
│   │   ├── ChatRateLimitInterceptor.java — HandlerInterceptor; consumes one ChatRateLimiter token
│   │   │                                    per POST /api/v1/chat/** request
│   │   └── ChatMvcConfig.java             — this module's own WebMvcConfigurer bean, registers
│   │                                        ChatRateLimitInterceptor — Spring composes every
│   │                                        WebMvcConfigurer in the context automatically, so this
│   │                                        module doesn't need gateway's WebMvcConfig to register
│   │                                        interceptors on its behalf
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
│   ├── StageSpan.java                     — record: stage name, durationMs, aborted flag; one per pipeline stage per request
│   ├── admin/
│   │   └── EmbeddingIndexItemResponse.java — @Builder DTO: contentItemId, title, contentType, contentStatus,
│   │                                          qualityScore, chunkCount, totalTokens, modelName, lastIndexedAt, indexed
│   └── chat/
│       ├── ChatRequest.java              — record: question, sessionId, sourceTypes, categoryId, tags, chatModel
│       ├── ChatResponse.java             — record: answer, List<RagSource>, sessionId; from(RagAnswer, sessionId)
│       ├── ChatSessionHistoryDto.java    — record: sessionId, List<MessageDto>; nested MessageDto(role, content, turnIndex)
│       └── ChatSessionSummaryDto.java    — record: sessionId, title, lastActivityAt, messageCount
├── event/
│   ├── PipelineCompletedEvent.java         — record event published by RagQueryServiceImpl after each pipeline execution;
│   │                                        carries RagPipelineContext + AnswerQualityVerdict
│   ├── PipelineCompletedEventListener.java — extends AsyncEventHandler<PipelineCompletedEvent>; @Transactional;
│   │                                        maps event → PipelineMetrics entity; resolveTraceId() binds MDC for logging
│   └── ContentPublishedEventListener.java — moved in from gateway; listens for content-service's
│                                            ContentPublishedEvent, calls this module's own ContentIndexingService.index(...)
│                                            (this event's definition stays in content-service, since it's published from there)
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
│   ├── AiErrorCode.java              — AI_* codes, implements common's ErrorCode interface (moved out of
│   │                                    common's CommonErrorCode)
│   └── ChatErrorCode.java            — CHAT_* codes (CHAT_SESSION_NOT_FOUND), owned by ChatSessionServiceImpl;
│                                        implements common's ErrorCode interface, same pattern as AiErrorCode
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
│   ├── PipelineMetricsRepository.java    — JpaRepository<PipelineMetrics, Integer>; append-only analytics writes;
│   │                                        fetchSummary(Instant) — native query using percentile_cont WITHIN GROUP
│   ├── ChatSessionRepository.java        — findByIdAndUserId (ownership check), findSessionSummariesByUserId
│   │                                        (JPQL "new" projection into ChatSessionSummaryDto, COUNT(m) join)
│   └── ChatMessageRepository.java        — findByChatSession_IdOrderByTurnIndexAsc/Desc, findMaxTurnIndexBySessionId
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
    ├── ChatSessionService.java                  — interface: getOrCreateSessionId, getRecentTurns, getConversationContext,
    │                                               addTurn, listSessions, getHistory (session lifecycle + rolling summarisation)
    ├── ContentIndexingService.java               — interface: index/indexAll/reindex/deleteIndex a ContentItem into the RAG store
    ├── IndexingQualityService.java               — interface: assess(contentItemId, contentType) → QualityVerdict
    │                                                (centroid-distance quality check at indexing time)
    ├── QualityVerdict.java                       — record: lowQuality, score; pass()/flag()/skipped() factories
    ├── EmbeddingIndexService.java                — interface: list() — paged content items + embedding stats for admin UI
    └── impl/
        ├── AnswerQualityServiceImpl.java             — embeds answer; computes normalised context centroid from selectedChunks;
        │                                               evaluates contextSimilarity + querySimilarity; logs WARN on drift
        ├── ChatModelResolverImpl.java                 — looks up Map<String,ChatLanguageModel> / Map<String,StreamingChatLanguageModel>
        │                                                (built by AiServiceConfig) by resolved model id
        ├── ConversationSummarisationServiceImpl.java — ChatLanguageModel-backed summarisation
        ├── ConversationTopicGuardServiceImpl.java    — embedBatch(question + historyFingerprint); strips recentTurns on shift
        ├── PipelineMetricsSummaryServiceImpl.java    — @Transactional(readOnly=true); calls fetchSummary(); maps projection to record
        ├── RagQueryServiceImpl.java                  — thin orchestrator: resolve chat model (before any pipeline work) →
        │                                                topicGuard → pipeline → recordPipelineMetrics() (6 Micrometer instruments)
        │                                                → LLM call + timing + token capture → assessAnswerQuality()
        │                                                → publishEvent(PipelineCompletedEvent)
        ├── ChatSessionServiceImpl.java                — lazy session-expiry enforcement (24h TTL); addTurn() safe from
        │                                                background threads; rolling summarisation via ChatSessionProperties triggers
        ├── ContentIndexingServiceImpl.java            — resolves QuestionAnswer/Article text → ContentIngestionService.ingest();
        │                                                also runs IndexingQualityService and persists ContentItem.qualityScore
        ├── IndexingQualityServiceImpl.java             — mean cosine similarity of chunk embeddings vs corpus centroid
        │                                                (CorpusStatisticsService), compared against IndexingConfig threshold
        ├── EmbeddingIndexServiceImpl.java              — two-query pattern: paged Specification query + batch stats query;
        │                                                 `indexed` filter uses a Criteria EXISTS subquery on ContentEmbedding
        └── CorpusStatisticsServiceImpl.java            — moved in from `gateway` (was left behind when the rest of the
                                                           indexing/RAG orchestration layer moved here — pure oversight, it
                                                           had zero gateway-specific dependencies even before this move);
                                                           @PostConstruct loads centroids from SYS_PARAM; @Scheduled refresh
                                                           recomputes via SQL avg(embedding); volatile float[] cache;
                                                           persistence delegated to common's SysParamService
```

---

## identity-service

```
identity-service/src/main/java/com/ttg/devknowledgeplatform/identity/
├── api/
│   ├── OAuth2Api.java                 — /api/v1/auth/**: OAuth2 redirect, local login/register/OTP flows,
│   │                                     state-token exchange, refresh, logout, current-user retrieval
│   ├── UserApi.java                   — PUT /me, POST /me/avatar ONLY — pure profile mutation. GET
│   │   │                                 /public/{userUuid} and GET /search moved to `social-service`'s
│   │   │                                 own `UserApi` instead (see that section) since they need
│   │   │                                 `FriendService` for relationship enrichment, and this module
│   │   │                                 must stay a pure `common`+`infra` leaf
│   │   └── impl/                      — OAuth2Controller / UserController
├── mapper/
│   └── UserMapper.java                — entity → dto/UserInfoResponse
├── dto/
│   ├── RegisterRequest.java / UserInfoResponse.java
│   ├── {Google,Facebook}OAuth2UserInfo.java / OAuth2UserInfo.java / OAuth2UserInfoFactory.java —
│   │   per-provider OAuth2 attribute-map parsing
│   ├── auth/                          — ExchangeStateRequest, LoginRequest/Response, LogoutRequest,
│   │                                    RefreshTokenRequest, RegisterResponse, ResendOtpRequest,
│   │                                    TokenResponse, VerifyOtpRequest
│   └── user/UpdateProfileRequest.java
├── service/seed/UserSeeder.java       — data/csv/users.csv (file itself stays under `gateway`'s
│                                        resources); identity by seedId; extends infra's CsvSeeder;
│                                        moved in from `gateway` alongside `PasswordEncoder` below,
│                                        which it uses to hash the shared demo password every
│                                        seeded account gets. Writes directly via common's
│                                        UserRepository, not UserService, same reasoning as every
│                                        other seeder in the reactor. `gateway`'s DataSeedingRunner
│                                        imports it across the module boundary to run it in order
└── security/
    ├── JwtTokenProvider.java          — HMAC sign/verify/refresh; issues access + refresh tokens
    ├── PasswordEncoderConfig.java     — @Bean PasswordEncoder (BCrypt); the ONE place in the whole
    │                                    reactor this bean is defined — UserSeeder above uses it
    │                                    directly now (same module); don't add a second one anywhere else
    ├── jwt/                           — TokenClaims (sealed interface) + AccessTokenClaims/RefreshTokenClaims
    ├── handler/OAuth2LoginSuccessHandler.java — issues JWTs + stores them in Redis via StateTokenService
    │                                    on successful OAuth2 login; wired into `gateway`'s SecurityConfig
    │                                    across the module boundary
    └── service/                       — UserService/Impl (registration, password hashing, OTP-gated
        │                                activation, provider linking), CustomOAuth2UserService (non-OIDC
        │                                providers e.g. Facebook), CustomOidcUserService (OIDC e.g. Google),
        │                                RefreshTokenBlacklistService/Impl (Redis), StateTokenService/Impl
        │                                (Redis; OAuth2 state-token → JWT handoff)
        └── (EmailService/OtpService also live at identity/service/ — OTP delivery)
```

`gateway`'s `SecurityConfig`/`JwtAuthenticationFilter`/`WebSocketConfig`/`StompAuthChannelInterceptor`
inject `JwtTokenProvider` and the `security.jwt.*` claim types from here across the module boundary —
those stay in `gateway` because they're transport-edge wiring (the security filter chain, STOMP CONNECT
authentication), not auth business logic. `CacheNames`/`CacheTtlProperties` (Redis TTL config, needed by
`StateTokenServiceImpl` here and `gateway`'s `RedisCacheConfig`) live in `infra`, not either module —
same "two siblings, shared utility" reasoning as `StorageService`.

---

## gateway

Renamed from `api` once its last REST controller (`UserApi.search`/`getPublicProfile`) moved to
`social-service` (see `docs/CHANGELOG.md`) — this module holds **zero REST controllers of its own**
today. Still the Spring Boot entry point and the one module allowed to depend on every feature
module, reserved for orchestration that needs two feature modules with no dependency relationship
possible between them in either direction (currently nothing qualifies).

```
gateway/src/main/java/com/ttg/devknowledgeplatform/
├── config/                           — chat-specific rate limiting (ChatRateLimiter/RateLimitProperties/
│   │                                    ChatRateLimitInterceptor) and the asyncEventExecutor bean have
│   │                                    both since moved out — to ai-service and infra respectively,
│   │                                    each the module that actually owns that concern's purpose
│   ├── JacksonConfig.java             — shared ObjectMapper customization
│   ├── cache/RedisCacheConfig.java    — @EnableCaching; base RedisCacheConfiguration + per-cache TTL
│   │                                    RedisCacheManager (reads infra's CacheTtlProperties); dedicated
│   │                                    Bucket4j Redis connection (also used by ai-service's
│   │                                    ChatRateLimiter, injected there by type — no import needed)
│   ├── thread/
│   │   ├── ThreadPoolProperties.java — @ConfigurationProperties at app.threads.*; nested SseExecutor
│   │   │                               only now: corePoolSize (10), maxPoolSize (50), queueCapacity (100),
│   │   │                               awaitTerminationSeconds (30); env-var overrides. The
│   │   │                               AsyncEventExecutor nested class moved to infra's own
│   │   │                               AsyncEventThreadPoolProperties (app.threads.async-event.*)
│   │   └── ThreadPoolConfig.java     — Factory Method: creates only sseStreamExecutor (SSE/MVC async
│   │                                   dispatch) now; registered with ExecutorServiceMetrics (Micrometer
│   │                                   Decorator); sizing from ThreadPoolProperties. asyncEventExecutor
│   │                                   moved to infra's own AsyncEventThreadPoolConfig
│   └── web/
│       ├── WebMvcConfig.java         — @EnableAsync; wires sseStreamExecutor into configureAsyncSupport
│       │                               (timeout read from ai-service's SseStreamTemplate.SSE_TIMEOUT_MS —
│       │                               not duplicated here, see that class) only — @Async dispatch uses
│       │                               asyncEventExecutor via an explicit qualifier on @EventHandler.
│       │                               Registers no interceptors of its own anymore — ai-service's own
│       │                               ChatMvcConfig registers the chat rate-limit interceptor via its
│       │                               own composed WebMvcConfigurer bean instead (Spring merges every
│       │                               WebMvcConfigurer bean in the context automatically)
│       ├── CurrentUserIdArgumentResolver.java — Spring MVC HandlerMethodArgumentResolver for
│       │                               @CurrentUserId (common.annotation), reads common.dto.CustomOAuth2User
│       │                               from the SecurityContext
│       └── CurrentUserIdMessageArgumentResolver.java — same, Spring Messaging's resolver interface, for
│                                       STOMP @MessageMapping methods
├── database/
│   └── sql/                          — Liquibase changelogs (master: dev-knowledge-platform.xml)
├── (no event/ package — every listener moved into the module that owns the event it reacts to:
│    ContentPublishedEventListener → ai-service, FriendRequestSentEventListener/
│    FriendRequestAcceptedEventListener → social-service; none ever had a gateway-specific dependency)
├── security/                         — JWT verification + OAuth2/STOMP transport wiring (edge concerns);
│   │                                    the actual JWT issuance/OAuth2 business logic lives in
│   │                                    identity-service — see that section
│   ├── SecurityConfig.java           — JWT + OAuth2 filter chain; injects identity-service's
│   │                                    CustomOAuth2UserService/CustomOidcUserService/OAuth2LoginSuccessHandler
│   ├── CorsConfig.java / JsonAuthenticationEntryPoint.java / CurrentUserResolver.java
│   ├── JwtAuthenticationFilter.java   — verifies bearer tokens via identity-service's JwtTokenProvider,
│   │                                    populates common.dto.CustomOAuth2User on the SecurityContext
│   ├── WebSocketConfig.java           — @EnableWebSocketMessageBroker; registers /ws with NO SockJS
│   │                                    fallback (real handshake, not an emulated transport); simple
│   │                                    broker on /topic + /queue; /app client-send prefix; wires
│   │                                    StompAuthChannelInterceptor + CurrentUserIdMessageArgumentResolver;
│   │                                    imports GroupMessagingController/DmMessagingController from
│   │                                    social-service's social.api.impl package
│   └── StompAuthChannelInterceptor.java — CONNECT: authenticates the JWT passed as a STOMP
│                                          Authorization header (handshake itself is permitAll —
│                                          browsers can't set headers on the handshake request),
│                                          reusing identity-service's JwtTokenProvider the same way
│                                          JwtAuthenticationFilter does for REST. SUBSCRIBE: authorizes
│                                          /topic/channels/{id} via social-service's GroupService.isChannelMember
│                                          — the simple broker has no per-destination ACL of its own. DMs
│                                          need no equivalent check (convertAndSendToUser's private queue
│                                          has no public topic string to subscribe to)
└── service/
    └── seed/
        └── DataSeedingRunner.java        — ApplicationRunner, @ConditionalOnProperty(app.seed.enabled);
                                             runs seeders in order: category → tag → questionAnswer → user →
                                             friend graph → blocks. CategorySeeder/TagSeeder/QuestionAnswerSeeder
                                             live in content-service/service/seed/, UserSeeder in
                                             identity-service/service/seed/, FriendGraphSeeder/
                                             UserBlockSeeder in social-service/service/seed/ (see those modules'
                                             sections) — this runner just injects and calls all of them
```

Everything that used to live flat here — every feature's REST controllers, DTOs, and MapStruct
mappers, including the one composed `UserApi.search`/`getPublicProfile` endpoint (moved to
`social-service`, which reaches into `identity-service` for the base lookup) — moved into the owning
feature module (`content-service`, `social-service`, `ai-service`, `identity-service`); see those
modules' sections and `docs/CHANGELOG.md`'s `[Unreleased]` entries for the full move and its
rationale. Chat-specific rate limiting (`ChatRateLimiter`/`RateLimitProperties`/
`ChatRateLimitInterceptor`) and the `asyncEventExecutor` thread pool moved out too, to `ai-service`
and `infra` respectively — see those modules' sections. What's left here is transport/security edge
infra (`SecurityConfig`, JWT filter, STOMP wiring, the `sseStreamExecutor` pool), Liquibase
migrations for every module's tables, and the cross-domain seeding orchestrator
(`DataSeedingRunner`).

`gateway/src/main/resources/data/` (separate resources tree, not nested under the Java sources above):

```
data/
├── csv/                              — DataSeedingRunner input (see service/seed above); no
│   │                                    slug column — CategorySeeder/TagSeeder always generate
│   │                                    it via SlugService; identity AND cross-references are by
│   │                                    id (→ seedId), never name/slug
│   ├── categories.csv                    — id, name, parentId (parentId references another row's id)
│   ├── tags.csv                           — id, name, status
│   ├── users.csv                          — id, email, username, firstName, lastName (UserSeeder, identity-service;
│   │                                         file itself stays under gateway's resources);
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
  `CONTENT_ITEM`; sole idempotency key for `UserSeeder`'s (`identity-service`) 20 sample login-able accounts.
- Migrations: `gateway/src/main/java/com/ttg/devknowledgeplatform/database/sql/` (Liquibase config
  lives in `gateway` regardless of which module owns the entities the migration backs —
  `social-service`'s tables are migrated from here too, same as `ai-service`'s)
  - Naming: `YYYY/VERSION/YYYYMMDDHHMI__VERSION__TICKET__description.sql`
