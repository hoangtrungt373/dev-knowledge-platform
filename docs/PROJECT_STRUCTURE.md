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
├── social-service/   — friend graph (search visibility, requests, friendships, blocking) plus chat:
│                        groups/channels (open-add, role-gated) and 1:1 DMs (friend-gated); own REST layer,
│                        including the relationship-enriched user-directory endpoints (search, public profile)
├── gateway/          — security/JWT-filter/STOMP transport wiring, Liquibase migrations, Spring Boot entry
│                        point. Holds **zero REST controllers of its own** (renamed from `api` once the last
│                        one moved out — see `docs/CHANGELOG.md`)
└── gui/              — React 18 + TypeScript + MUI frontend (Vite)
```

`ecommerce-service/`, `identity-service/`, and `task-service/` are deliberately **not** in the tree
above: all three are standalone Spring Boot applications, not part of this dependency graph at all
(own schema, own JWT verification, own port; `gateway` has no Maven dependency on any of them, each
extracted one at a time as a microservices-study exercise — see their own `## ecommerce-service`/
`## identity-service`/`## task-service` sections further down and root `CLAUDE.md`). All three still
compile against `common`+`infra` as ordinary library dependencies.

Dependency order: `common` ← `infra` ← `content-service` ← `ai-service`;
`common` ← `infra` ← `social-service`. `content-service`/`social-service` are parallel siblings
depending only on `common`+`infra`; `ai-service` is allowed a single, real, one-directional
dependency on a sibling module (`ai-service` → `content-service`) — never the reverse.
`social-service` used to have the same kind of dependency on `identity-service` (`UserApi`'s
`search`/`getPublicProfile`), and `task-service` used to be a third parallel sibling alongside
`content-service`/`social-service` before its own extraction — both removed once the target module
became a standalone service and could no longer be reached in-process (see below). `gateway` depends
on these three remaining feature modules (`content-service`, `ai-service`, `social-service`); it's
the only module allowed to depend on more than one,
reserved for orchestration that needs two feature modules with **no** dependency relationship
possible between them in either direction — currently nothing qualifies, which is why `gateway` has
no REST layer of its own today. `gui` is independent of the whole Java reactor.

Each of `content-service`/`social-service`/`ai-service` owns its own full vertical slice —
entities/services *and* REST controllers, DTOs, MapStruct mappers — rather than the earlier shape where
`api` (now `gateway`) centralized every controller/DTO/mapper regardless of which module owned the
underlying entity. That centralized shape kept these modules transport-agnostic; the vertical-slice shape
trades that away deliberately, in favor of each module being closer to an independently-deployable unit
ahead of an eventual microservices split (see `docs/CHANGELOG.md`'s `[Unreleased]` entries for the full
rationale and what moved). `identity-service` and `task-service` still own their own full vertical
slice too — they just do so as standalone apps now rather than embedded modules (see their own
sections further down).

One real one-directional sibling dependency remains inside the monolith's dependency graph — a downstream
module reaching into an upstream one for a genuine data/logic need, never the reverse:
- `ai-service` → `content-service`: `ContentEmbedding` has a real `@ManyToOne` FK to `ContentItem`, and
  `ContentIngestionService.ingest(...)` takes a `ContentItem` parameter. This is also why the content+AI
  indexing orchestration layer (`IngestionApi`, `EmbeddingIndexApi`, `PublicContentApi`) lives in
  `ai-service` rather than `gateway`: `ai-service` is the one module (besides `gateway`) that can already
  see both `content-service` and itself.

---

## common

```
common/src/main/java/com/ttg/devknowledgeplatform/common/
├── entity/
│   ├── AbstractEntity.java           — audit columns (usrCreation, dteCreation, version, …)
│   └── User.java                     — userUuid, email, username, password, firstName, lastName, profilePicture,
│                                        provider (UserProvider), role (UserRole), providerId, emailVerified, status
│                                        (UserStatus, presence), enabled, seedId (String, nullable, DB SEED_ID,
│                                        DKP-0016 — sole idempotency key for UserSeeder, now in `gateway`); referenced
│                                        by FK from social-service's FriendRequest/Friendship/UserBlock entities
│                                        (which live there, not here — see social-service section below). `@Table`
│                                        deliberately does NOT hardcode a schema — see this class's own Javadoc:
│                                        every standalone deployable (`gateway`, `ecommerce-service`,
│                                        `identity-service`) maps this same class into its own schema via its own
│                                        `hibernate.default_schema`, each with its own independently
│                                        JIT-provisioned copy of a given Keycloak identity
├── enums/
│   ├── UserProvider.java
│   ├── UserRole.java
│   └── UserStatus.java
├── repository/
│   └── UserRepository.java           — JpaRepository<User, Integer> + JpaSpecificationExecutor<User>; moved here
│                                        from gateway/repository (named api/repository at the time) so
│                                        content-service/social-service (neither of which can depend on
│                                        gateway) can reach it directly — this also retired social-service's
│                                        own SocialUserRepository, a near-duplicate that existed only because
│                                        this repository used to live in gateway; findBySeedId(String) added
│                                        for UserSeeder (now in `gateway`) idempotency
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
    ├── RateLimitExceededException.java — stays here (not ai-service, its only thrower today) because
    │                                    GlobalExceptionHandler#handleRateLimit needs a compile-time
    │                                    @ExceptionHandler(RateLimitExceededException.class) reference, and
    │                                    GlobalExceptionHandler itself must stay in common
    └── ResourceNotFoundException.java
```

Category/Tag/ContentItem/ContentItemTag/QuestionAnswer/Article entities and their enums
(ContentStatus/ContentType/TagStatus/QuestionDifficulty) used to live here; they moved to
`content-service` — see that module's section below and `CHANGELOG.md`. `ChatSession`/`ChatMessage`
(+ `ChatMessageRole`), `ChatProvider`, `SysParam`/`ParamKey`/`SysParamRepository`/`SysParamService`(`Impl`),
and `ConversationContext`/`ConversationTurn` used to live here too — an audit found zero real
consumers outside `ai-service` for any of them (some Javadoc cross-references had implied otherwise),
so they moved there — see that module's section below and `CHANGELOG.md`.

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
│   │                                    binding, read by `gateway`'s `RedisCacheConfig`. Originally
│   │                                    moved here because `identity-service`'s (now-deleted)
│   │                                    `StateTokenServiceImpl` needed it too — see the Keycloak
│   │                                    migration entry in `docs/CHANGELOG.md`
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
                                         TagSeeder, gateway's UserSeeder, and social-service's UserBlockSeeder.
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
│       │                              docs/SEED_DATA_AUTHORING_GUIDE.md); requires gateway's UserSeeder to run first
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
│   │                                    there instead, on a separate standalone app now). Resolves the base
│   │                                    profile lookup directly via `common`'s `UserRepository` and this
│   │                                    module's own `FriendMapper.toUserInfo` (a local `UserInfoResponse`
│   │                                    DTO, duplicated from `identity-service`'s equivalent) before applying
│   │                                    `FriendService` relationship enrichment — no longer reaches into
│   │                                    `identity-service`, which is a standalone service now and can't be
│   │                                    called in-process
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
`gateway` — edge/transport infra, not a `social-service` concern. `gateway`'s own
`KeycloakJwtAuthenticationConverter` now also owns the JIT-provisioning business logic itself
(`findOrCreateUser`, inlined via `UserRepository` directly) rather than delegating to
`identity-service`'s `UserService` — see the `## identity-service` section below for why.

`GroupService` and `DmService` are deliberately two services, not one — they gate access differently
(open-add + role checks vs. friend-required) and share no entities, so combining them would mix two
unrelated authorization models in one class. `DmService` depends on `FriendService` as a collaborator
(reusing its relationship lookup) rather than querying `FriendshipRepository`/`UserBlockRepository`
directly, avoiding a second implementation of the canonicalization + mutual-invisibility logic.
REST layer: this module's own `GroupApi`/`GroupController` and `DmApi`/`DmController`, DTOs in
`dto/messaging/`, `MessagingMapper` — see `api/` above. No upload endpoint yet for message attachments;
`MessageAttachmentRequest.objectKey` assumes the client already has a MinIO object key from
somewhere else. `UserApi`/`UserController` (also `api/` above) used to be this module's one
dependency on `identity-service`; that dependency was removed once `identity-service` became a
standalone service (see the `## identity-service` section below) — this module's `pom.xml` no
longer declares it.

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
│   ├── ConversationContext.java           — rolling summary + recent verbatim turns; primary RAG context type;
│   │                                        moved in from common — audit found zero real consumers outside this module
│   ├── ConversationTurn.java              — role (ChatMessageRole) + content record for a single message; moved in
│   │                                        from common alongside ConversationContext, same reason
│   ├── admin/
│   │   └── EmbeddingIndexItemResponse.java — @Builder DTO: contentItemId, title, contentType, contentStatus,
│   │                                          qualityScore, chunkCount, totalTokens, modelName, lastIndexedAt, indexed
│   └── chat/
│       ├── ChatRequest.java              — record: question, sessionId, sourceTypes, categoryId, tags, chatModel
│       ├── ChatResponse.java             — record: answer, List<RagSource>, sessionId; from(RagAnswer, sessionId)
│       ├── ChatSessionHistoryDto.java    — record: sessionId, List<MessageDto>; nested MessageDto(role, content, turnIndex)
│       └── ChatSessionSummaryDto.java    — record: sessionId, title, lastActivityAt, messageCount
├── enums/
│   ├── ChatMessageRole.java   — USER, ASSISTANT; moved in from common alongside ChatMessage/ConversationTurn
│   ├── ChatProvider.java      — OPENAI, ANTHROPIC; selects LangChain4j builder family per chat model profile;
│   │                            moved in from common — only AiServiceConfig/ChatModelsConfig ever used it
│   └── ParamKey.java          — typed keys for SYS_PARAM.NAME; renaming a constant requires a DB migration;
│                                includes PROMPT_INJECTION_PROTOTYPE_EMBEDDINGS (fingerprinted vector-list cache
│                                for PromptGuardStage below); moved in from common alongside SysParam/SysParamService
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
│   ├── PipelineMetrics.java          — append-only analytics entity (no AbstractEntity); columns: traceId, createdAt,
│   │                                    abortedAt, candidateCount, afterScoringCount, selectedCount,
│   │                                    evidenceMeanScore, effectiveSimThreshold, answerContextSim, answerQuerySim, answerDrifted;
│   │                                    latency: contextualizationMs, embeddingMs, retrievalMs, llmGenerationMs, totalPipelineMs;
│   │                                    tokens: contextualizationInputTokens, contextualizationOutputTokens, embeddingTokens,
│   │                                    qualityEmbeddingTokens, generationInputTokens, generationOutputTokens, estimatedCostUsd;
│   │                                    attribution: userId (no FK — analytics rows must survive user deletion),
│   │                                    chatModel (id of the resolved chat model profile; NULL pre-DKP-0012 rows)
│   ├── ChatSession.java              — userId, title, lastActivityAt, summary (TEXT); parent of ChatMessage rows;
│   │                                    moved in from common — its repository (below) already lived here
│   ├── ChatMessage.java              — role (ChatMessageRole), content, turnIndex; child of ChatSession; moved in
│   │                                    from common alongside ChatSession
│   └── SysParam.java                 — @Entity for SYS_PARAM; fields: name (ParamKey), value (TEXT), computedAt;
│                                        moved in from common — audit found zero real consumers outside this module
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
│   ├── ChatMessageRepository.java        — findByChatSession_IdOrderByTurnIndexAsc/Desc, findMaxTurnIndexBySessionId
│   └── SysParamRepository.java           — JpaRepository<SysParam, Integer>; findByName(ParamKey); moved in
│                                            from common — audit found zero real consumers outside this module
└── service/
    ├── ContentIngestionService.java             — chunks text + stores embeddings
    ├── SysParamService.java                     — interface: getValue(ParamKey), upsert(ParamKey, String);
    │                                               string-in/string-out, no opinion on value encoding; moved in
    │                                               from common — its only two callers (CorpusStatisticsServiceImpl,
    │                                               PromptGuardStage below) were always in this module
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
        ├── CorpusStatisticsServiceImpl.java            — moved in from `gateway` (was left behind when the rest of the
        │                                                  indexing/RAG orchestration layer moved here — pure oversight, it
        │                                                  had zero gateway-specific dependencies even before this move);
        │                                                  @PostConstruct loads centroids from SYS_PARAM; @Scheduled refresh
        │                                                  recomputes via SQL avg(embedding); volatile float[] cache;
        │                                                  persistence delegated to this module's own SysParamService
        └── SysParamServiceImpl.java                    — find-or-create-and-save upsert pattern; moved in from common
                                                           alongside SysParamService/SysParamRepository/SysParam
```

---

## task-service

Personal task/project management (MVP, built in phases — see `docs/CHANGELOG.md`). Single-user for
now: every `Project`/`Task` has an `ownerUuid`, no shared membership yet. **Now a standalone Spring
Boot application, not part of the monolith** — the third module pulled out, following the
`ecommerce-service`/`identity-service` precedent (see the `project-microservices-extraction-plan`
memory for the full history).

**Extraction (done):** own `TaskServiceApplication` entry point, own `task` Postgres schema
(separate from the monolith's `product` schema), own JWT verification (`security/` — verifies
tokens issued by Keycloak, never issues its own), own port (`8083`), own Liquibase changelog +
`task-service-liquibase.yml` docker-compose file. `gateway` no longer has a Maven dependency on
this module — it never called into task-service's Java classes in-process to begin with
(`ProjectApi`/`TaskApi` were already this module's own REST layer, just riding on `gateway`'s
Spring context via the Maven dependency), so this was a pure `pom.xml` removal, no rewritten call
sites. **Not yet built:** the `gateway`-side HTTP proxy to this service — until that exists, it's
only reachable directly on its own port, same limitation `ecommerce-service`/`identity-service`
have.

**No local `User` copy, unlike `gateway`/`identity-service`** (see below) — mirroring
`ecommerce-service`'s "Option C" shape, just for a different concrete reason: `Project.ownerUuid`/
`Task.ownerUuid` are plain columns compared directly against the caller's verified JWT `sub` claim,
never a `@ManyToOne User` foreign key. Every ownership check this module does only ever answers "is
this row's owner the caller," never "show me someone else's profile" — so there's no cross-service
`User` duplication to justify, no `@EntityScan`/`@EnableJpaRepositories` widening onto
`common.entity`/`common.repository`, and no database access at all in the current-user resolution
path. (An earlier revision of this module briefly gave it its own JIT-provisioned `task.USER` table
plus both annotations, on the assumption that a real relational owner reference meant it needed a
real local `User` row — reverted once that distinction became clear; see
`task-service/CLAUDE.md`'s "No local `User` copy" rule.)

```
task-service/src/main/java/com/ttg/devknowledgeplatform/task/
├── TaskServiceApplication.java        — @SpringBootApplication entry point; sitting at this package
│                                        (not the shared root gateway's main class uses) keeps
│                                        content-service/social-service/ai-service/identity-service
│                                        out of this app's component scan (no Maven dependency on any
│                                        of them anyway). No @EntityScan/@EnableJpaRepositories —
│                                        this module doesn't touch common.entity.User/
│                                        common.repository.UserRepository at all; default scanning
│                                        already covers this module's own entity/repository packages
├── security/
│   ├── SecurityConfig.java            — this app's own filter chain (everything requires auth
│   │                                     except `/actuator/**` — no public/admin surface, single-
│   │                                     user personal task tracker); pure OAuth2 resource server,
│   │                                     verifies bearer tokens against Keycloak's JWKS
│   ├── KeycloakRealmRoleConverter.java — maps `realm_access.roles` to `ROLE_*` authorities;
│   │                                     duplicated from gateway's/identity-service's/ecommerce-
│   │                                     service's converter of the same name rather than shared
│   ├── KeycloakJwtAuthenticationConverter.java — builds the CustomOAuth2User principal directly
│   │                                     from the verified JWT's claims (sub stands in for
│   │                                     userUuid) — persists nothing, mirroring ecommerce-
│   │                                     service's converter of the same name, not gateway's/
│   │                                     identity-service's
│   └── CurrentUserResolver.java        — reads the authenticated CustomOAuth2User principal's UUID
│                                         straight off the principal, no database lookup
├── config/web/
│   ├── WebMvcConfig.java               — registers CurrentUserIdArgumentResolver
│   └── CurrentUserIdArgumentResolver.java — resolves common.annotation.CurrentUserId String-
│                                         annotated controller parameters via CurrentUserResolver;
│                                         duplicated from gateway's class of the same name (no STOMP
│                                         transport here, so no message-argument-resolver
│                                         counterpart needed)
├── entity/
│   ├── Project.java                  — name, description, ownerUuid (String, plain column — see
│   │                                    above), status (ProjectStatus)
│   └── Task.java                     — project (Project, @ManyToOne, nullable — standalone tasks allowed),
│                                        ownerUuid (String, plain column), title, description, status
│                                        (TaskStatus, default TODO), priority (TaskPriority, default
│                                        MEDIUM), dueDate (Instant, nullable), parentTask (Task,
│                                        @ManyToOne, nullable — self-FK, capped at one level deep) +
│                                        subtasks (List<Task>, @OneToMany, cascade ALL + orphanRemoval)
├── enums/
│   ├── ProjectStatus.java             — ACTIVE, ARCHIVED
│   ├── TaskPriority.java              — LOW, MEDIUM, HIGH, URGENT
│   └── TaskStatus.java                — TODO, IN_PROGRESS, DONE; canTransitionTo(target) guards only the
│                                         no-op case (target == this) — deliberately permissive otherwise
├── repository/
│   ├── ProjectRepository.java         — JpaRepository<Project, Integer> + findByOwnerUuid(String, Pageable)
│   ├── TaskRepository.java            — JpaRepository<Task, Integer> + JpaSpecificationExecutor<Task>
│   └── spec/
│       └── TaskSpecification.java     — withFilters(ownerUuid, projectId, status, priority, dueBefore, dueAfter);
│                                         ownerUuid and "parentTask IS NULL" (top-level only) are always applied,
│                                         the rest are optional equality/range predicates
├── service/
│   ├── ProjectService.java (+ impl/)  — CRUD; every method ownership-checked via a private
│   │                                     resolveOwnedProject(ownerUuid, projectId) helper
│   ├── TaskService.java (+ impl/)     — CRUD + changeStatus(ownerUuid, taskId, newStatus) (uses
│   │                                     TaskStatus.canTransitionTo, throws TASK_INVALID_STATUS_TRANSITION
│   │                                     on a no-op) + listSubtasks(ownerUuid, parentTaskId) (unpaginated —
│   │                                     nesting capped at one level)
│   ├── ProjectCommands.java           — Create/Update records (name, description)
│   ├── TaskCommands.java              — Create/Update records (title, description, projectId, priority,
│   │                                     dueDate, parentTaskId); Update fully replaces these fields
│   └── TaskFilter.java                — optional query-filter record for TaskService.listTasks
├── exception/
│   └── TaskErrorCode.java             — PROJECT_NOT_FOUND, TASK_NOT_FOUND,
│                                         TASK_INVALID_STATUS_TRANSITION, TASK_INVALID_PARENT (self-parent,
│                                         parent-is-itself-a-subtask, or task-already-has-subtasks); a
│                                         project/task owned by a different user reuses the same *_NOT_FOUND
│                                         as a missing id (mutual-invisibility-style, no separate 403)
├── dto/
│   ├── ProjectResponse.java           — record: id, name, description, status, createdAt
│   ├── CreateProjectRequest.java / UpdateProjectRequest.java — @Data, name (@NotBlank), description
│   ├── TaskResponse.java              — record: id, projectId (flat Integer, not nested), title,
│   │                                     description, status, priority, dueDate, parentTaskId
│   │                                     (flat Integer), createdAt
│   ├── CreateTaskRequest.java / UpdateTaskRequest.java — @Data, title (@NotBlank), description,
│   │                                     projectId, priority (default MEDIUM), dueDate, parentTaskId
│   └── ChangeTaskStatusRequest.java   — @Data, status (@NotNull)
├── mapper/
│   ├── ProjectMapper.java             — plain MapStruct interface (no injected fields needed)
│   └── TaskMapper.java                — projectId/parentTaskId mapped via null-safe expressions
│                                         (task.getProject()/getParentTask() != null ? ... : null)
└── api/ (+ api/impl/)
    ├── ProjectApi.java (+ ProjectController.java) — /api/v1/projects: create, getById, list,
    │                                     update, POST /{id}/archive; every method takes
    │                                     @CurrentUserId String ownerUuid
    └── TaskApi.java (+ TaskController.java)       — /api/v1/tasks: create, getById, list (+
                                          projectId/status/priority/dueBefore/dueAfter filters, always
                                          top-level only), update, POST /{id}/status,
                                          GET /{id}/subtasks, delete (cascades to subtasks)
```

Depends on `common` + `infra` only — no dependency on `content-service`; `Task` used to carry an
optional `@ManyToOne` FK to `content-service`'s `ContentItem`, removed as unused (see
`docs/CHANGELOG.md`'s `[Unreleased]` entry). **Historical:** while still embedded in the monolith,
this module's tables migrated from `gateway`'s changelog tree — `DKP-0020` (`PROJECT`/`TASK`, both
`product` schema, in its *original* shape including `CONTENT_ITEM_ID`, `OWNER_ID` as a real FK to
`product.USER` — it had already executed against a real DB by the time both follow-on changes came
up, so it stayed frozen as-is) plus `DKP-0021` (added `TASK.PARENT_TASK_ID`/`FK_TASK_PARENT`/
`IDX_TASK_PARENT`) and `DKP-0022` (dropped `TASK.CONTENT_ITEM_ID`/`FK_TASK_CONTENT_ITEM`/
`IDX_TASK_CONTENT_ITEM`). Those `gateway`-tree migrations are untouched (already-run history, per
this repo's frozen-changeset convention) but now describe an orphaned `product.PROJECT`/
`product.TASK` pair `gateway`'s own Spring context no longer maps any entity to.

**Now (post-extraction):** this module migrates its own `task` schema from its own changelog tree
(`task-service/.../database/sql/task-service.xml` + `2026/0.0.1/*.sql`), applied via the standalone
`task-service-liquibase.yml` docker-compose file at the repo root — the same pattern
`ecommerce-service`/`identity-service` established. `DKP-0028` is the *only* migration in this
tree — it adds `task.PROJECT`/`task.TASK` as a fresh snapshot of the *final* shape those tables
reached in `gateway`'s tree (post-`DKP-0022` — no `CONTENT_ITEM_ID` column at all, `PARENT_TASK_ID`
present from the start), not a replay of `DKP-0020`→`DKP-0021`→`DKP-0022`'s incremental history,
with one deliberate deviation: `OWNER_UUID` (`VARCHAR(36)`, indexed, no FK) replaces `OWNER_ID`
entirely — there is no `task.USER` table (see the "no local `User` copy" note above; an earlier
revision of this migration briefly added one as `DKP-0028`, with the `PROJECT`/`TASK` tables as a
second changeset `DKP-0029` — both were collapsed back into this single migration once the `USER`
table was found to be unnecessary). Any further `PROJECT`/`TASK` schema change gets its own new
changeset in *this* module's tree now, per the same frozen-changeset convention — see
`task-service/CLAUDE.md`'s Liquibase rule.

`Task.parentTask`/`subtasks` mirror `content-service`'s `Category` self-referential parent/child
tree, capped at one level deep instead of Category's arbitrary depth (see `task-service/CLAUDE.md`
for the full reasoning and the delete/status/listing behavior that intentionally diverges from
Category's).

**Compiles cleanly** (full reactor including the extraction changes; needs `JAVA_HOME` pointed at a
JDK 21 install) but hasn't been run against a real Postgres yet — same unverified-at-runtime caveat
as `ecommerce-service`/`identity-service`.

---

## identity-service

Keycloak now owns login/registration/password/OTP/OAuth2-brokering entirely (see
`docs/CHANGELOG.md`'s Keycloak migration entries) — this module narrowed to JIT-syncing a local
`User` row from a verified Keycloak identity, plus the authenticated user's own profile. **Now a
standalone Spring Boot application, not part of the monolith** — see the
`project-microservices-extraction-plan` memory for the full extraction history.

**Extraction (done):** own `IdentityServiceApplication` entry point (`@EntityScan`/
`@EnableJpaRepositories` pointed at `common.entity`/`common.repository`, since this module owns no
entities/repositories of its own and default Spring Boot scanning wouldn't otherwise reach
`common`'s), own `identity` Postgres schema (separate from the monolith's `product` schema), own
JWT verification (`security/` — verifies tokens issued by Keycloak, never issues its own), own port
(`8082`), own Liquibase changelog + `identity-service-liquibase.yml` docker-compose file. `gateway`
no longer has a Maven dependency on this module, and vice versa was never true. **Not yet built:**
the `gateway`-side HTTP proxy to this service — until that exists, it's only reachable directly on
its own port, same limitation `ecommerce-service` has.

```
identity-service/src/main/java/com/ttg/devknowledgeplatform/identity/
├── IdentityServiceApplication.java    — @SpringBootApplication entry point; sitting at this package
│                                        (not the shared root gateway's main class uses) keeps
│                                        content-service/social-service/ai-service/task-service out
│                                        of this app's component scan (no Maven dependency on any of
│                                        them anyway); @EntityScan/@EnableJpaRepositories widen JPA
│                                        scanning to also cover common.entity/common.repository,
│                                        which default scanning wouldn't reach on its own
├── api/
│   ├── AuthApi.java                   — GET /api/v1/auth/user ONLY (renamed from OAuth2Api once
│   │                                     every other endpoint on it was deleted — Keycloak's own
│   │                                     /userinfo doesn't cover this app's avatar/username shape)
│   ├── UserApi.java                   — PUT /me, POST /me/avatar ONLY — pure profile mutation. GET
│   │   │                                 /public/{userUuid} and GET /search live in `social-service`'s
│   │   │                                 own `UserApi` instead (see that section) since they need
│   │   │                                 `FriendService` for relationship enrichment — that module
│   │   │                                 now resolves the base lookup itself rather than reaching
│   │   │                                 into this now-standalone service
│   │   └── impl/                      — AuthController / UserController
├── mapper/
│   └── UserMapper.java                — entity → dto/UserInfoResponse
├── dto/
│   ├── UserInfoResponse.java
│   └── user/UpdateProfileRequest.java
└── security/
    ├── SecurityConfig.java            — this app's own filter chain (everything requires auth
    │                                    except `/actuator/**` — no public/admin surface here, unlike
    │                                    content-service/ecommerce-service); pure OAuth2 resource
    │                                    server, verifies bearer tokens against Keycloak's JWKS
    ├── KeycloakRealmRoleConverter.java — maps `realm_access.roles` to `ROLE_*` authorities;
    │                                    duplicated from gateway's/ecommerce-service's converter of
    │                                    the same name rather than shared (no Maven dependency)
    ├── KeycloakJwtAuthenticationConverter.java — the one converter in the reactor that still
    │                                    *delegates* rather than inlining: calls this module's own
    │                                    in-process `service/UserService.findOrCreateFromKeycloak`
    │                                    directly, since both live in this same standalone app
    └── service/                       — UserService/Impl, narrowed to findOrCreateFromKeycloak
                                          (KeycloakUserInfo carrier record, same package),
                                          resolveCurrentUser, findByEmail/
                                          findByUserUuid(Optional)/findById, updateStatus,
                                          updateProfile, updateAvatar
```

**Deleted outright** (all superseded by Keycloak): `security/JwtTokenProvider`,
`security/jwt/{TokenClaims,AccessTokenClaims,RefreshTokenClaims}`, `security/PasswordEncoderConfig`,
`security/service/{CustomOAuth2UserService,CustomOidcUserService}`,
`security/handler/OAuth2LoginSuccessHandler`, `security/service/StateTokenService`(`Impl`),
`security/service/RefreshTokenBlacklistService`(`Impl`), `service/{OtpService,EmailService}`(`Impl`),
every `dto/auth/*` type, `dto/RegisterRequest`,
`dto/{OAuth2UserInfo,GoogleOAuth2UserInfo,FacebookOAuth2UserInfo,OAuth2UserInfoFactory}`, and (as
part of this extraction) `service/seed/UserSeeder` — relocated to `gateway`, not deleted: it only
ever wrote via `common`'s `UserRepository` directly, no other dependency on this module, and
`gateway` still needs to seed its own `product.USER` for the modules still embedded there. This
module needs no seed data of its own — a seeded demo account has no matching Keycloak identity, so
this module's own `identity.USER` table only ever fills via JIT-provisioning on a real login. The
Keycloak-migration pom cleanup (JJWT, Redis-for-blacklist, mail-for-OTP — all left over from before
those classes were deleted) happened alongside this extraction too.

`gateway`'s own `KeycloakJwtAuthenticationConverter` no longer calls into this module's
`UserService.findOrCreateFromKeycloak` across a module boundary — that stopped being possible once
this module became a standalone service with no Maven dependency from either. It now inlines the
same find-or-create logic directly via `common`'s `UserRepository`, JIT-provisioning its own local
`User` copy into `product.USER`. `ecommerce-service`'s converter takes a different path entirely —
it persists nothing at all, building its principal straight from the JWT's claims (see its own
section below for why). There is no single canonical `User` row shared across deployables; see root
`CLAUDE.md`'s Security section.

**Compiles cleanly** (full reactor; needs `JAVA_HOME` pointed at a JDK 21 install) but hasn't been
run against a real Postgres yet — same unverified-at-runtime caveat as `ecommerce-service`.

---

## ecommerce-service

Study-project e-commerce vertical slice, **and now a standalone Spring Boot application, not part
of the monolith**. Full scope/rationale for all five epics lives in `docs/user-stories/`
(`README.md` + `01-catalog-search.md` through `05-reviews-recommendations.md`) — only
**Epic 1 (Catalog & Search)** has code so far, but all 7 of its user stories are now built: admin
CRUD for `ProductCategory`/`Product` including independent variant/image add-remove-reorder, the
outbox relay, and a public browse/search/detail surface with attribute-value filtering.

**Extraction (done):** own `EcommerceServiceApplication` entry point, own `ecommerce` Postgres
schema (same `dev-premier` database as the monolith, not a separate database instance —
per-service-per-schema, see root `CLAUDE.md`'s Database Conventions), own JWT verification
(`security/` — verifies tokens issued elsewhere, never issues its own), own port (`8081`), own
Liquibase changelog + `ecommerce-service-liquibase.yml` docker-compose file. `gateway` no longer
has a Maven dependency on this module. **Not yet built:** the `gateway`-side HTTP proxy to this
service — until that exists, it's only reachable directly on its own port.

**Compiles cleanly** (full reactor including the extraction changes; needs `JAVA_HOME` pointed at
a JDK 21 install) but hasn't been run against a real Postgres yet — the Liquibase migration
against the `ecommerce` schema, the native SQL in `ProductSearchViewRepository.search`, and the JWT
verification path are all still unverified at runtime.

**Does not persist a `User` row at all** — none of this module's own entities have a foreign key
onto a user, so `KeycloakJwtAuthenticationConverter` only ever needs "who is the caller, and are
they an admin," both fully answerable from the verified JWT's claims. An earlier revision briefly
added `@EntityScan`/`@EnableJpaRepositories` plus an `ecommerce.USER` table (found alongside a real
bug: neither annotation existed, so `common.repository.UserRepository` would never have become a
bean and this app would have failed to start) — reverted once it became clear this module never
actually needed a persisted `User` copy in the first place. See the `## identity-service` section
above and the `project-microservices-extraction-plan` memory for the full "Option C" reasoning.

```
ecommerce-service/src/main/java/com/ttg/devknowledgeplatform/ecommerce/
├── EcommerceServiceApplication.java — @SpringBootApplication + @EnableScheduling entry point;
│                                       sitting at this package (not the shared root gateway's main
│                                       class uses) keeps content-service/social-service/ai-service/
│                                       task-service/identity-service out of this app's component
│                                       scan. No @EntityScan/@EnableJpaRepositories — this module
│                                       never touches common.entity.User/common.repository.
│                                       UserRepository (see above), and default scanning already
│                                       covers this module's own entity/repository packages
├── entity/
│   ├── ProductCategory.java    — flat taxonomy (table PRODUCT_CATEGORY, not CATEGORY — avoids
│   │                              colliding with content-service's Category in the shared schema)
│   ├── Product.java            — name/description/slug/active; ManyToOne ProductCategory; always
│   │                              has ≥1 ProductVariant
│   ├── ProductImage.java       — ordered gallery; storageKey references a MinIO object (infra's
│   │                              StorageService); sortOrder unique per product
│   ├── ProductVariant.java     — sku/price/stockQuantity/reservedQuantity; attributes is a
│   │                              Map<String,String> stored as JSONB (@JdbcTypeCode(SqlTypes.JSON),
│   │                              same approach as ai-service's ContentEmbedding.metadata); DB CHECK
│   │                              enforces 0 <= reservedQuantity <= stockQuantity
│   ├── ProductSearchView.java  — CQRS read model for browse/search/filter; one denormalized row per
│   │                              Product; SEARCH_VECTOR (tsvector) is DB-generated from SEARCH_TEXT
│   │                              and deliberately not mapped as a Java field; written only by
│   │                              ProductChangedOutboxEventHandler (service/impl/, below)
│   └── OutboxEvent.java        — shared transactional-outbox table every future epic will reuse;
│                                  status (OutboxEventStatus: PENDING/PROCESSING/PROCESSED/FAILED)
│                                  is the relay's claim/dispatch signal, attemptCount/lastError
│                                  make a poison message diagnosable; aggregateType is an enum
│                                  (OutboxAggregateType, DB CHECK-backed — small, slow-growing set);
│                                  eventType stays a plain string — one Java field can only be one
│                                  enum type, and every future epic keeps adding its own event
│                                  types to this same shared table
├── enums/
│   ├── OutboxEventStatus.java     — PENDING, PROCESSING, PROCESSED, FAILED
│   └── OutboxAggregateType.java   — PRODUCT (widen only when a later epic adds an aggregate root)
├── exception/
│   └── EcommerceErrorCode.java  — PRODUCT_CATEGORY_*/PRODUCT_*/PRODUCT_VARIANT_*/PRODUCT_IMAGE_*
│                                    codes, implements common's ErrorCode interface
├── repository/
│   ├── ProductCategoryRepository.java / ProductRepository.java / ProductVariantRepository.java
│   │   / ProductImageRepository.java / OutboxEventRepository.java (findIdsByStatus + an atomic
│   │   claim(id, from, to) conditional UPDATE) / ProductSearchViewRepository.java
│   │   (findByProductId/deleteByProductId + a native search() query — tsvector+trgm keyword
│   │   match, category/price-range/inStock filters, all via the "(:param IS NULL OR ...)" idiom
│   │   for one static query covering every optional-filter combination)
│   └── spec/
│       └── ProductCategorySpecification.java / ProductSpecification.java
├── outbox/                      — generic outbox mechanism, reusable by every future epic
│   ├── OutboxEventHandler.java     — Strategy interface: eventType() + handle(OutboxEvent)
│   ├── OutboxEventDispatcher.java  — Map<String, OutboxEventHandler> built from every handler bean
│   ├── OutboxEventProcessor.java   — claims + dispatches one event, @Transactional; kept as its
│   │                                  own bean (not a 2nd method on OutboxRelay) to avoid Spring's
│   │                                  @Transactional self-invocation proxy pitfall
│   └── OutboxRelay.java            — @Scheduled poller (app.ecommerce.outbox.relay.poll-interval,
│                                        default PT5S); @EnableScheduling lives on this module's
│                                        own EcommerceServiceApplication now
├── security/                    — this app's own filter chain, independent of gateway's; pure
│   │                                OAuth2 resource server (Keycloak-backed, same as every other
│   │                                deployable — the old JJWT-based JwtVerifier/
│   │                                JwtAuthenticationFilter shown in earlier revisions of this doc
│   │                                are gone, see docs/CHANGELOG.md's Keycloak migration entries)
│   ├── SecurityConfig.java          — /api/v1/public/** permitAll, /api/v1/admin/** hasRole(ADMIN),
│   │                                    stateless, .oauth2ResourceServer(...) verifying bearer
│   │                                    tokens against Keycloak's JWKS — never issues tokens itself
│   ├── KeycloakRealmRoleConverter.java — maps realm_access.roles to ROLE_* authorities; duplicated
│   │                                    from gateway's/identity-service's converter of the same name
│   └── KeycloakJwtAuthenticationConverter.java — builds the CustomOAuth2User principal directly
│                                        from the verified JWT's claims (sub stands in for
│                                        userUuid) — persists nothing, unlike gateway's/
│                                        identity-service's converters of the same name. This
│                                        module's own entities have no foreign key onto a user, so
│                                        the only real need was resolving the caller's identity/role,
│                                        fully answerable from the token alone (see this module's
│                                        own CLAUDE.md for the "Option C" reasoning)
├── service/
│   ├── ProductCategoryService.java / ProductService.java / ProductSearchService.java — return
│   │   entities, not REST DTOs; this module's own mapper/ does entity→response mapping (same
│   │   split as content-service)
│   ├── ProductCommands.java     — Create/Update input records (incl. nested VariantInput/
│   │   ImageInput) mirroring content-service's QuestionAnswerCommands
│   └── impl/
│       ├── ProductCategoryServiceImpl.java / ProductServiceImpl.java — the latter enforces
│       │   at-least-one-variant, no duplicate SKU/sortOrder within a request, no SKU conflict
│       │   against existing variants, and consistent attribute keys across a product's variants;
│       │   publishes a PRODUCT_CHANGED OutboxEvent after every create/update/deactivate
│       ├── ProductSearchServiceImpl.java — thin: trigram-threshold constant, blank-q handling,
│       │   calls the repository with an unsorted PageRequest (the native query bakes in its own
│       │   ORDER BY)
│       └── ProductChangedOutboxEventHandler.java — the PRODUCT_CHANGED handler; re-derives
│           ProductSearchView from current Product/ProductVariant state; deletes the row (rather
│           than updating it) when the product is deactivated or missing, since ProductSearchView
│           has no active column of its own
├── mapper/                      — MapStruct: ProductCategoryMapper / ProductMapper (also maps
│                                    ProductVariant→ProductVariantResponse and
│                                    ProductImage→ProductImageResponse for ProductResponse's
│                                    nested lists) / ProductSearchViewMapper
├── api/                         — REST layer
│   ├── ProductCategoryApi.java / ProductApi.java — admin CRUD (/api/v1/admin/**), incl.
│   │                                 POST/DELETE .../variants/{id} and
│   │                                 POST/DELETE/PATCH .../images/{id} for independent
│   │                                 variant/image mutation (US-1.6)
│   ├── ProductSearchApi.java    — public browse/search + GET /{slug} detail
│   │                                 (/api/v1/public/products, US-1.1/1.2/1.3/1.4)
│   └── impl/                    — ProductCategoryController / ProductController (admin-gated
│                                    automatically via this module's own security/SecurityConfig
│                                    /api/v1/admin/** rule) / ProductSearchController (public via
│                                    that same config's /api/v1/public/** rule)
└── dto/                         — ProductCategoryResponse/CreateProductCategoryRequest/
                                     UpdateProductCategoryRequest, ProductResponse/
                                     CreateProductRequest/UpdateProductRequest,
                                     ProductVariantRequest/ProductVariantResponse,
                                     ProductImageRequest/ProductImageResponse,
                                     UpdateProductImageSortOrderRequest,
                                     ProductSearchResponse
```

Liquibase migration: `ecommerce-service/.../database/sql/2026/0.0.1/202608040001__0.0.1__DKP-0023__add_ecommerce_catalog_tables.sql`
under this module's **own** changelog tree now (not `gateway`'s) — `PRODUCT_CATEGORY`, `PRODUCT`,
`PRODUCT_IMAGE`, `PRODUCT_VARIANT`, `PRODUCT_SEARCH_VIEW`, `OUTBOX_EVENT` (with its
`STATUS`/`ATTEMPT_COUNT`/`LAST_ERROR` columns), plus `CREATE SCHEMA ecommerce`, the `pg_trgm`
extension, and GIN indexes for `tsvector`/trigram/JSONB containment search on
`PRODUCT_SEARCH_VIEW`. Applied via `ecommerce-service-liquibase.yml` at the repo root.

Not yet built: the `gateway`-side HTTP proxy, variant/image add-remove-reorder endpoints,
`ProductCategory` delete, and Epics 2–5.

Compiles against `common` + `infra` as ordinary library dependencies; has **no** Maven dependency
on any other feature module, and none of them (including `gateway`) depend on it either — see
root `CLAUDE.md`'s dependency-order section for why Epic 5's originally-planned dependency on
`ai-service` needs rethinking now that this module is standalone. See `ecommerce-service/CLAUDE.md`
for the rules this module follows.

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
├── security/                         — OAuth2-resource-server verification + STOMP transport wiring
│   │                                    (edge concerns); Keycloak is the identity provider — this
│   │                                    app never issues tokens, only verifies them; JIT-provisioning
│   │                                    business logic (finding/creating this app's own local User
│   │                                    row) lives right here now — see below
│   ├── SecurityConfig.java           — .oauth2ResourceServer(jwt -> ...) verifying bearer tokens
│   │                                    against Keycloak's JWKS (spring.security.oauth2.
│   │                                    resourceserver.jwt.issuer-uri); no .oauth2Login() anymore
│   ├── KeycloakRealmRoleConverter.java — Converter<Jwt, Collection<GrantedAuthority>>; maps the
│   │                                    token's realm_access.roles claim to ROLE_* authorities —
│   │                                    Spring's default converter only reads a flat scope claim
│   ├── KeycloakJwtAuthenticationConverter.java — Converter<Jwt, AbstractAuthenticationToken>; JIT-
│   │                                    provisions/refreshes this app's own product.USER row
│   │                                    directly via common's UserRepository (find by
│   │                                    keycloakSubjectId, fallback email, write only if changed) —
│   │                                    inlined here rather than delegating to identity-service's
│   │                                    UserService, which is a standalone service now and can't be
│   │                                    called in-process (deliberately duplicated — note
│   │                                    ecommerce-service's own converter of this name does NOT
│   │                                    persist anything at all; this app's `Task`/`Friendship`/etc.
│   │                                    FKs genuinely need a real local User row, ecommerce-service's
│   │                                    entities don't). Builds the same CustomOAuth2User principal
│   │                                    shape every call site expects.
│   │                                    Rejects a refresh token presented as a bearer token via its
│   │                                    typ claim. Shared by both the REST filter chain (above) and
│   │                                    STOMP CONNECT (below) — one JIT-provisioning path, not two
│   ├── CorsConfig.java / JsonAuthenticationEntryPoint.java / CurrentUserResolver.java
│   ├── WebSocketConfig.java           — @EnableWebSocketMessageBroker; registers /ws with NO SockJS
│   │                                    fallback (real handshake, not an emulated transport); simple
│   │                                    broker on /topic + /queue; /app client-send prefix; wires
│   │                                    StompAuthChannelInterceptor + CurrentUserIdMessageArgumentResolver;
│   │                                    imports GroupMessagingController/DmMessagingController from
│   │                                    social-service's social.api.impl package
│   └── StompAuthChannelInterceptor.java — CONNECT: decodes the JWT passed as a STOMP Authorization
│                                          header (handshake itself is permitAll — browsers can't set
│                                          headers on the handshake request) via an injected
│                                          JwtDecoder (Spring's resource-server auto-config, no manual
│                                          key handling), then reuses
│                                          KeycloakJwtAuthenticationConverter.convert(jwt) — the same
│                                          JIT-provisioning path REST uses. SUBSCRIBE: authorizes
│                                          /topic/channels/{id} via social-service's GroupService.isChannelMember
│                                          — the simple broker has no per-destination ACL of its own. DMs
│                                          need no equivalent check (convertAndSendToUser's private queue
│                                          has no public topic string to subscribe to)
└── service/
    └── seed/
        ├── UserSeeder.java               — same package as DataSeedingRunner now (relocated from
        │                                    identity-service once that module became a standalone
        │                                    service — see its own section); writes directly via
        │                                    common's UserRepository, no logic change from the move
        └── DataSeedingRunner.java        — ApplicationRunner, @ConditionalOnProperty(app.seed.enabled);
                                             runs seeders in order: category → tag → questionAnswer → user →
                                             friend graph → blocks. CategorySeeder/TagSeeder/QuestionAnswerSeeder
                                             live in content-service/service/seed/, UserSeeder right here now,
                                             FriendGraphSeeder/UserBlockSeeder in social-service/service/seed/
                                             (see those modules' sections) — this runner just injects and calls
                                             all of them
```

Everything that used to live flat here — every feature's REST controllers, DTOs, and MapStruct
mappers, including the one composed `UserApi.search`/`getPublicProfile` endpoint (moved to
`social-service`, which used to reach into `identity-service` for the base lookup before that
module became standalone — it resolves the base lookup itself now) — moved into the owning feature
module (`content-service`, `social-service`, `ai-service`, `identity-service` before its later
extraction); see those modules' sections and `docs/CHANGELOG.md`'s `[Unreleased]` entries for the
full move and its rationale. Chat-specific rate limiting (`ChatRateLimiter`/`RateLimitProperties`/
`ChatRateLimitInterceptor`) and the `asyncEventExecutor` thread pool moved out too, to `ai-service`
and `infra` respectively — see those modules' sections. What's left here is transport/security edge
infra (`SecurityConfig`, JWT filter, STOMP wiring, the `sseStreamExecutor` pool), Liquibase
migrations for every module's tables, and the cross-domain seeding orchestrator
(`DataSeedingRunner`).

`gateway/src/test/` — the reactor's first test source (every other module's `src/test` is still
empty): `ws/AbstractStompIntegrationTest.java` (Testcontainers Postgres/Redis/MinIO +
`@DynamicPropertySource`, boots the full context — the only place `WebSocketConfig`/
`StompAuthChannelInterceptor` and `social-service`'s `DmMessagingController` actually get wired
together) and `ws/DmMessagingStompIntegrationTest.java` (the real STOMP flow: dual delivery,
thread reuse, CONNECT rejection cases, `WsErrorResponse` business-rule cases — see
`docs/CHANGELOG.md`'s `[Unreleased]` entry for the full scenario list). `application-test.yml`
under `src/test/resources` activates profile `test` (placeholder OAuth2 client-id/secret so
context startup doesn't fail; `spring.liquibase.enabled: true`; real datasource/redis/minio
coordinates come from the Testcontainers instances at runtime, not this file).

`gateway/src/main/resources/data/` (separate resources tree, not nested under the Java sources above):

```
data/
├── csv/                              — DataSeedingRunner input (see service/seed above); no
│   │                                    slug column — CategorySeeder/TagSeeder always generate
│   │                                    it via SlugService; identity AND cross-references are by
│   │                                    id (→ seedId), never name/slug
│   ├── categories.csv                    — id, name, parentId (parentId references another row's id)
│   ├── tags.csv                           — id, name, status
│   ├── users.csv                          — id, email, username, firstName, lastName (UserSeeder, gateway —
│   │                                         relocated here from identity-service once that module became a
│   │                                         standalone service); 20 sample login-able accounts for the
│   │                                         Friend Management GUI
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
  `CONTENT_ITEM`; sole idempotency key for `UserSeeder`'s (`gateway`) 20 sample login-able accounts.
- Migrations: `gateway/src/main/java/com/ttg/devknowledgeplatform/database/sql/` (Liquibase config
  lives in `gateway` regardless of which module owns the entities the migration backs —
  `social-service`'s tables are migrated from here too, same as `ai-service`'s)
  - Naming: `YYYY/VERSION/YYYYMMDDHHMI__VERSION__TICKET__description.sql`

---

## Deployment

Four independently-runnable Spring Boot processes exist today — `gateway` (the monolith),
`ecommerce-service`, `identity-service`, and `task-service` (the latter three standalone
microservices-study extractions) — each with its own `Dockerfile` (multi-stage:
`maven:3.9.9-eclipse-temurin-21` build stage running `mvn -pl <module> -am package` against the full
reactor, `eclipse-temurin:21-jre-jammy` runtime stage). All four Dockerfiles use the **repo root**
as their build context, since the Maven reactor build needs sibling-module sources
(`docker build -f gateway/Dockerfile .`, not `docker build gateway/`). `gateway`'s `Dockerfile` only
`COPY`s the sources of modules it actually depends on
(`common`/`infra`/`content-service`/`ai-service`/`social-service`) plus every module's `pom.xml`
(needed for Maven to parse the reactor's full `<modules>` list even for modules it won't build) — it
does not copy `identity-service`, `ecommerce-service`, or `task-service` sources.

`dev-knowledge-platform-apps-docker-compose.yml` (repo root) brings up all four app containers plus
one-shot Liquibase migration runners (`dkp-liquibase`, `ecommerce-liquibase`, `identity-liquibase`,
`task-liquibase`) ahead of them via `depends_on: condition: service_completed_successfully`. It has
no infra containers of its own — it must be run combined with
`dev-knowledge-platform-docker-compose.yml` in one command, since that's what puts every container
in a single Compose project/network so service-name DNS (`postgres`/`redis`/`minio`/`keycloak`)
resolves:

```bash
docker compose -f dev-knowledge-platform-docker-compose.yml \
                -f dev-knowledge-platform-apps-docker-compose.yml \
                up -d --build
```

`ecommerce-service`, `identity-service`, and `task-service` share the same `dev-premier` Postgres
database as `gateway`, each in its own schema (`ecommerce`/`identity`/`task` vs. `gateway`'s
`product`) — per-service-per-schema, not per-service-per-database (see root `CLAUDE.md`'s Database
Conventions and the `project-microservices-extraction-plan` memory for why). Each service also has
its own standalone `*-liquibase.yml` compose file at the repo root for migrating its own schema
outside the combined apps-compose flow (`ecommerce-service-liquibase.yml`,
`identity-service-liquibase.yml`, `task-service-liquibase.yml`).

`common`, `infra`, and the root `pom.xml` needed no changes for any of this — they already function
as this repo's shared-foundation modules (proven by `ecommerce-service` already depending on
`common`+`infra` as ordinary library jars, with zero Maven dependency on `gateway`, before
`identity-service` and `task-service` followed the same pattern). Kafka/RabbitMQ messaging and a
`kubernetes/` directory are not part of this yet — see `docs/CHANGELOG.md`'s `[Unreleased]` entry
for current scope.
