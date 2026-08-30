# Project Structure

## Module layout

```
dev-knowledge-platform/
├── common/           — shared entities, enums, exceptions, base DTOs (PagedResponse, CustomOAuth2User),
│                        the @CurrentUserId annotation; depends on Spring Data JPA (for @Entity), validation,
│                        web, security (all as annotation/type support, not full autoconfiguration)
├── infra/            — shared Spring infrastructure: event base classes, composed annotations, MDC utilities,
│                        SlugService, StorageService (MinIO)
├── gateway/          — security/JWT-filter wiring, Spring Boot entry point. Holds **zero REST
│                        controllers, zero embedded feature modules, and zero Liquibase migrations
│                        of its own** (renamed from `api` once the last controller moved out — see
│                        `docs/CHANGELOG.md`)
└── gui/              — React 18 + TypeScript + MUI frontend (Vite)
```

`content-service/`, `ecommerce-service/`, `identity-service/`, `task-service/`, `social-service/`,
and `ai-service/` are deliberately **not** in the tree above: all six are standalone Spring Boot
applications, not part of this dependency graph at all (own schema, own JWT verification, own
port; `gateway` has no Maven dependency on any of them, each extracted one at a time as a
microservices-study exercise — see their own `## content-service`/`## ecommerce-service`/
`## identity-service`/`## task-service`/`## social-service`/`## ai-service` sections further down
and root `CLAUDE.md`). All six still compile against `common`+`infra` as ordinary library
dependencies. `ai-service` was the sixth and final extraction — `gateway` now has **zero** embedded
feature modules left, closing out the microservices-extraction-plan project (see root `CLAUDE.md`'s
Long-term direction section). `infra`'s Redis cache TTL config (`CacheTtlProperties`/`CacheNames`)
was deleted outright during `ai-service`'s extraction, not moved — see that module's own section
further down for the dead-code finding.

`ai-service` depends only on `common`+`infra` now — **not** `common` ← `infra` ← `content-service`
← `ai-service` as this file used to describe, nor `gateway` → `ai-service` as it described more
recently. It used to carry a single, real, one-directional Maven dependency on `content-service`
(`ContentEmbedding`'s `@ManyToOne` FK to `ContentItem`, `ContentIngestionService.ingest(...)` taking
a live `ContentItem` parameter, and `PublicContentApi`/`PublicContentController` fronting
`content-service`'s own services) — all three were removed as part of `content-service`'s own
standalone-service extraction, well before `ai-service`'s own extraction (see that module's own
section further down): `ContentEmbedding` now carries a plain `contentItemId` column instead of
a JPA association, `ai-service`'s indexing pipeline calls a `ContentServiceClient` (HTTP, against
`content-service`'s own `/internal/content-items/**` API) instead, and
`PublicContentApi`/`PublicContentController` moved back into `content-service` outright.
`social-service` used to be a parallel sibling here too, depending only on `common`+`infra`, with
its own one-directional dependency on `identity-service` (`UserApi`'s `search`/`getPublicProfile`),
and `task-service` used to be a fourth parallel sibling with its own one-directional dependency on
`content-service` — all removed once the target module became a standalone service and could no
longer be reached in-process, before each was itself extracted in turn (see below). `gateway`
depends only on `common`+`infra` now — **zero embedded feature modules remain**, `ai-service` having
been the last one. The "only module allowed to depend on more than one feature module, reserved for
orchestration with no dependency relationship possible between the two" rule is now doubly moot in
practice — there are zero embedded feature modules left for `gateway` to depend on more than one of
— which is why `gateway` has no REST layer of its own today. `gui` is independent of the whole Java
reactor.

`ai-service` owns its own full vertical slice — entities/services *and* REST controllers, DTOs,
MapStruct mappers — rather than the earlier shape where `api` (now `gateway`) centralized every
controller/DTO/mapper regardless of which module owned the underlying entity. That centralized
shape kept these modules transport-agnostic; the vertical-slice shape trades that away
deliberately, in favor of each module being closer to an independently-deployable unit ahead of an
eventual microservices split (see `docs/CHANGELOG.md`'s `[Unreleased]` entries for the full
rationale and what moved). `content-service`, `identity-service`, `task-service`, `social-service`,
and now `ai-service` itself all own their own full vertical slice too — they just do so as
standalone apps now rather than embedded modules (see their own sections further down).

---

## common

```
common/src/main/java/com/ttg/devknowledgeplatform/common/
├── entity/
│   └── AbstractEntity.java           — audit columns (usrCreation, dteCreation, version, …); the sole entity left
│                                        here — User (+ UserRepository + UserProvider/UserRole/UserStatus) moved
│                                        out to `identity-service` once `gateway` dropped its own local User copy
│                                        and `identity-service` became the sole remaining consumer (see that
│                                        module's section below and `docs/CHANGELOG.md`)
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
    ├── ResourceNotFoundException.java
    └── Validator.java                — static guard-clause utility (isTrue/isFalse/notNull/isNull/notFound);
                                           collapses the repeated if(!condition){throw ...} shape reactor-wide;
                                           see docs/CHANGELOG.md's [Unreleased] entry
```

Category/Tag/ContentItem/ContentItemTag/QuestionAnswer/Article entities and their enums
(ContentStatus/ContentType/TagStatus/QuestionDifficulty) used to live here; they moved to
`content-service` — see that module's section below and `CHANGELOG.md`. `ChatSession`/`ChatMessage`
(+ `ChatMessageRole`), `ChatProvider`, `SysParam`/`ParamKey`/`SysParamRepository`/`SysParamService`(`Impl`),
and `ConversationContext`/`ConversationTurn` used to live here too — an audit found zero real
consumers outside `ai-service` for any of them (some Javadoc cross-references had implied otherwise),
so they moved there — see that module's section below and `CHANGELOG.md`. `User`/`UserRepository`/
`UserProvider`/`UserRole`/`UserStatus` used to live here too, mapped by both `gateway` (into its own
`product.USER`) and `identity-service` (into `identity.USER`) as a shared-kernel class — moved to
`identity-service` outright once `gateway` retired its own local copy entirely (no REST controllers
ever read it back — see `gateway`'s section below) and `identity-service` became the sole consumer.
`common` no longer maps any `User`-shaped entity to anything.

---

## infra

```
infra/src/main/java/com/ttg/devknowledgeplatform/infra/
├── context/
│   └── MdcKeys.java              — MDC key constants shared across modules: TRACE_ID = "traceId",
│                                    SPAN_ID = "spanId". Only visible in log output where a module's
│                                    own logging.pattern.console renders %X{traceId}/%X{spanId} —
│                                    see tracing/ below.
├── tracing/
│   ├── TraceContext.java         — record(traceId, spanId, sampled) implementing the W3C Trace
│   │                                Context traceparent header shape (version-traceid-spanid-flags);
│   │                                parse(String)/fresh()/withNewSpan()/toHeaderValue(); no Spring
│   │                                dependency, pure parsing/formatting logic
│   └── TraceContextFilter.java   — OncePerRequestFilter, @Component; binds a TraceContext to
│                                    MDC for every inbound request, rewrites the request's own
│                                    traceparent header to carry this app's own span (so Gateway
│                                    Server MVC's default header-forwarding proxy behavior carries
│                                    it downstream automatically — no GatewayRoutesConfig change
│                                    needed), and logs one structured access-log line
│                                    (method/path/status/latency) per request. Auto-registered in
│                                    all seven of this reactor's Spring Boot apps via component
│                                    scan — gateway ROADMAP.md item #1. Does not propagate across
│                                    an @Async boundary (MDC is thread-local) — see ai-service's own
│                                    TraceparentClientHttpRequestInterceptor and its CLAUDE.md for
│                                    the one place this matters today.
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
│   (no cache/ package anymore — CacheNames/CacheTtlProperties were deleted outright, not moved,
│    during ai-service's standalone extraction: they backed gateway's old RedisCacheConfig, and a
│    reactor-wide grep for @Cacheable/@CacheEvict/@CachePut found zero real usages anywhere in the
│    codebase — see ai-service/CLAUDE.md's Rules section for the full dead-code finding)
│   ├── thread/{AsyncEventThreadPoolConfig,AsyncEventThreadPoolProperties}.java — the
│   │                                    asyncEventExecutor bean (app.threads.async-event.*); moved
│   │                                    here from gateway since this module's own event/ framework
│   │                                    (below) is what actually owns this pool's purpose. The
│   │                                    sseStreamExecutor pool (a separate bulkhead) no longer lives
│   │                                    in gateway either — it moved to ai-service's own
│   │                                    config/thread/ once that module went standalone.
│   └── json/JacksonConfig.java       — shared ObjectMapper customization (JavaTimeModule,
│                                        tolerant deserialization, ISO-8601 dates); moved here from
│                                        gateway once every standalone service's own
│                                        @SpringBootApplication was widened with an explicit
│                                        @ComponentScan reaching this sibling package — before that,
│                                        this bean only ever reached gateway's own (nonexistent)
│                                        REST layer, while every one of the six standalone services
│                                        silently fell back to Spring Boot's un-customized default
│                                        ObjectMapper. See the gateway section's config/ note below
│                                        for the full component-scan fix this move depended on.
├── security/
│   ├── KeycloakRealmRoleConverter.java — Converter<Jwt, Collection<GrantedAuthority>>; maps a JWT's
│   │                                    realm_access.roles claim to ROLE_*-prefixed authorities.
│   │                                    Moved here from seven near-identical per-service copies —
│   │                                    zero module-specific logic, used by all seven services now.
│   ├── KeycloakJwtAuthenticationConverter.java — Converter<Jwt, AbstractAuthenticationToken>; builds
│   │                                    a CustomOAuth2User principal straight from the verified
│   │                                    JWT's claims (sub → userUuid), zero database access. Moved
│   │                                    here from five near-identical per-service copies —
│   │                                    gateway/ecommerce-service/task-service/content-service/
│   │                                    ai-service all use this shared bean now. identity-service
│   │                                    and social-service keep their own local converter of the
│   │                                    same class name in their own security package instead (both
│   │                                    JIT-provision a real local row — identity.USER/
│   │                                    social.PROFILE — which is genuine divergent logic, not
│   │                                    duplication) — see those modules' own sections. Both of
│   │                                    those two still delegate to this shared
│   │                                    KeycloakRealmRoleConverter for the role-mapping half.
│   │                                    Requires spring-boot-starter-oauth2-resource-server on this
│   │                                    module's own pom.xml — adds nothing new to any consumer's
│   │                                    classpath, since every service already declares it itself.
│   ├── CurrentUserResolver.java      — resolveUserUuid(Principal): reads CustomOAuth2User.getUserUuid()
│   │                                    straight off the principal, zero database access. Moved
│   │                                    here from task-service/content-service/ai-service's own
│   │                                    identical copies (which differed only in method name —
│   │                                    resolveOwnerUuid/resolveAuthorUuid/resolveUserUuid — matching
│   │                                    each module's own column vocabulary); CurrentUserIdArgumentResolver
│   │                                    (below) calls this shared method. Not used by
│   │                                    social-service (resolves a real local SocialProfile numeric
│   │                                    PK via a repository lookup) or identity-service (resolves via
│   │                                    @AuthenticationPrincipal directly in the controller instead).
│   │                                    ecommerce-service had no @CurrentUserId consumer in Epic 1
│   │                                    but gained one for Epic 2's cart.
│   ├── CurrentUserIdArgumentResolver.java — resolves @CurrentUserId String-annotated controller
│   │                                    parameters via CurrentUserResolver.resolveUserUuid. Moved
│   │                                    here from four near-identical per-service copies
│   │                                    (content-service/task-service/ai-service/ecommerce-service —
│   │                                    byte-identical logic, differing only in Javadoc wording).
│   │                                    Each consuming module's own WebMvcConfig/ChatMvcConfig still
│   │                                    registers it locally via addArgumentResolvers — only the
│   │                                    resolver class itself moved. Not used by social-service (own
│   │                                    Integer-PK copy, genuinely divergent) or identity-service/
│   │                                    gateway (never had one).
│   └── JsonAuthenticationEntryPoint.java — returns a JSON 401 body instead of Spring Security's
│                                        default redirect/HTML response. Moved here from
│                                        gateway's/ai-service's byte-identical copies (the only two
│                                        services with an explicit exceptionHandling() entry point
│                                        wired up); no other change needed. The other five services
│                                        still fall back to Spring Security's own default 401
│                                        behavior for a resource server.
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
                                         TagSeeder and social-service's UserBlockSeeder — not by any
                                         UserSeeder: gateway's own UserSeeder was deleted outright once
                                         product.USER was dropped, and identity-service never needed one
                                         (a seeded demo account has no matching Keycloak identity, so
                                         identity.USER only ever fills via real JIT-provisioning).
```

---

## content-service

**Standalone Spring Boot application** (own entry point, own `content` schema, own port `8085`) —
see root `CLAUDE.md`'s Long-term direction section and the `project-microservices-extraction-plan`
memory for the full 8-step extraction history (the hardest of the five microservices-study
extractions so far, since `ai-service`'s coupling to it was real, deep, and bidirectional rather
than a removable leftover).

```
content-service/src/main/java/com/ttg/devknowledgeplatform/content/
├── ContentServiceApplication.java — @SpringBootApplication + @ConfigurationPropertiesScan entry
│                                     point; no @EntityScan/@EnableJpaRepositories — this module
│                                     never touches common.entity.User/common.repository.UserRepository
├── security/
│   ├── SecurityConfig.java        — this module's own filter chain: /api/v1/public/** and
│   │                                 /internal/** permitAll, /actuator/** permitAll,
│   │                                 /api/v1/admin/** hasRole(ADMIN), everything else authenticated
│   │                                 (mirrors gateway's old three-way rule set for these same paths)
│   │   (no local KeycloakRealmRoleConverter.java/KeycloakJwtAuthenticationConverter.java
│   │    anymore — both moved to infra.security as shared beans, see infra section above;
│   │    picked up via this module's existing @ComponentScan reaching infra)
│   (no local CurrentUserResolver.java anymore — uses the shared
│    infra.security.CurrentUserResolver.resolveUserUuid(...) instead, see infra section above)
├── config/web/
│   └── WebMvcConfig.java          — registers infra's shared CurrentUserIdArgumentResolver
│                                     (assigns the resolveUserUuid result to this module's own
│                                     authorUuid vocabulary) — no local resolver class of this
│                                     module's own anymore, see infra section above
├── entity/
│   ├── Category.java              — hierarchical; parent/children self-join; seedId (nullable) for CategorySeeder idempotency
│   ├── Tag.java                   — status (TagStatus); seedId (nullable) for TagSeeder idempotency
│   ├── ContentItem.java           — base content record (type, status, title, slug, category, viewCount,
│   │                                 publishedAt, qualityScore, authorUuid); seedId (nullable) for
│   │                                 QuestionAnswerSeeder idempotency. authorUuid is a plain String
│   │                                 column (the Keycloak JWT's sub claim), never a @ManyToOne User FK
│   │                                 — see the "No local User copy" rule below
│   ├── ContentItemTag.java        — join entity for content ↔ tag
│   ├── QuestionAnswer.java        — general dev-knowledge Q&A, not only interview prep;
│   │                                 difficulty/isCommon are nullable interview-specific metadata
│   └── Article.java               — body text; backs both ContentType.ARTICLE and .BLOG_POST
│   (none of these six entities hardcode @Table(schema="product") anymore — they resolve via this
│   app's own hibernate.default_schema: content)
├── enums/
│   └── TagStatus.java             — ACTIVE, INACTIVE (only enum still local to this module — no
│                                     consumer outside it; ContentStatus/ContentType/QuestionDifficulty
│                                     moved to common's enums/ package — see that module's CLAUDE.md)
├── event/
│   └── ContentPublishedEvent.java — carries a ContentItem; currently has no publisher wired up (scaffold for a
│                                     future auto-index-on-publish flow — today indexing is admin-triggered via
│                                     ai-service's IngestionController). ai-service used to carry a listener for
│                                     this event (ContentPublishedEventListener) but it was deleted as dead code
│                                     during this module's extraction — never fired (no publisher exists),
│                                     and an in-process Spring event can't cross a service boundary anyway
├── repository/
│   ├── CategoryRepository.java / TagRepository.java / ContentItemRepository.java / ContentItemTagRepository.java
│   │   / QuestionAnswerRepository.java / ArticleRepository.java
│   └── spec/
│       └── CategorySpecification.java / TagSpecification.java / QuestionAnswerSpecification.java /
│           ArticleSpecification.java / ContentItemSpecification.java (filters directly on ContentItem
│           itself — distinct from the other three, which join through it; backs InternalContentService)
├── service/
│   ├── CategoryService.java / TagService.java / QuestionAnswerService.java / ArticleService.java — return
│   │   entities, not REST DTOs — this module's own Category/Tag/QuestionAnswer/ArticleMapper (below) do
│   │   entity→response mapping (same split as social-service's FriendService → its own FriendMapper, and
│   │   ai-service's RagQueryService → its own ChatResponse). Article/QuestionAnswerService.create()
│   │   take a String authorUuid, not an Integer authorId
│   ├── CategoryTreeNode.java      — record (Category + resolved children) returned by CategoryService.listTree();
│   │                                 this module's own CategoryMapper.toTreeNodeResponse() flattens it into CategoryTreeNodeResponse
│   ├── QuestionAnswerCommands.java / ArticleCommands.java — Create/Update input records mirroring this
│   │   module's own Create*Request/Update*Request field-for-field, without REST/validation annotations —
│   │   the controllers below translate request DTOs into these before calling the service, keeping the
│   │   service layer decoupled from the REST/JSON contract even though both now live in the same module
│   ├── seed/                      — startup data seeding; format chosen per content shape
│   │   ├── CategorySeeder.java        — data/csv/categories.csv; identity by seedId; extends infra's CsvSeeder
│   │   ├── TagSeeder.java             — data/csv/tags.csv; identity by seedId; extends infra's CsvSeeder
│   │   ├── QuestionAnswerSeeder.java  — data/question-answers/*.md (YAML front matter + markdown body);
│   │   │                                 does not extend CsvSeeder (one-file-per-record, different iteration shape)
│   │   └── DataSeedingRunner.java     — this module's own; orchestrates the three seeders above in
│   │                                     dependency order, moved here from gateway's once that app
│   │                                     could no longer import these classes across the module boundary
│   └── impl/
│       └── CategoryServiceImpl.java / TagServiceImpl.java / QuestionAnswerServiceImpl.java / ArticleServiceImpl.java
├── exception/
│   └── ContentErrorCode.java      — CATEGORY_*/TAG_*/QA_*/ARTICLE_*/CONTENT_ITEM_* codes, implements
│                                     common's ErrorCode interface
├── api/                           — admin CRUD REST layer (moved in from `gateway`, named `api` at
│                                     the time — see CHANGELOG)
│   ├── CategoryApi.java / TagApi.java / ArticleApi.java / QuestionAnswerApi.java — Article/
│   │   QuestionAnswerApi.create() take @CurrentUserId String authorUuid, not
│   │   @AuthenticationPrincipal CustomOAuth2User
│   ├── PublicContentApi.java      — /api/v1/public/**, read-only published-content browsing;
│   │                                 moved back here from ai-service — it only ever fronted this
│   │                                 module's own services, never anything ai-service-specific
│   ├── InternalContentApi.java    — /internal/content-items/**, server-to-server only (see below)
│   └── impl/                      — CategoryController / TagController / ArticleController /
│                                     QuestionAnswerController / PublicContentController /
│                                     InternalContentController
├── mapper/                        — MapStruct: CategoryMapper / TagMapper / ArticleMapper / QuestionAnswerMapper
│                                     (entity ↔ dto/*); ArticleMapper/QuestionAnswerMapper are used by this
│                                     module's own PublicContentController above
│                                     InternalContentItemMapper — ContentItem + QuestionAnswer/Article → dto/internal
├── dto/
│   ├── (flat, not nested under dto/content/): CategoryResponse/CreateCategoryRequest/
│   │   UpdateCategoryRequest, CategoryTreeNodeResponse, TagResponse/CreateTagRequest/
│   │   UpdateTagRequest, ArticleResponse/CreateArticleRequest/UpdateArticleRequest,
│   │   QuestionAnswerResponse/CreateQuestionAnswerRequest/UpdateQuestionAnswerRequest
│   └── internal/                  — InternalContentItemResponse (flattened ContentItem +
│       QuestionAnswer/Article, incl. categoryName/tagNames) / UpdateQualityScoreRequest
└── config/
    ├── InternalApiProperties.java — app.internal-api.key (shared secret for /internal/**)
    └── security/InternalApiKeyFilter.java — rejects any /internal/** request missing/mismatching
        the X-Internal-Api-Key header; this module's own SecurityConfig (above) marks /internal/**
        permitAll so this filter (not Spring Security) is what actually enforces it

content-service/src/main/java/com/ttg/devknowledgeplatform/content/database/
└── sql/
    ├── content-service.xml            — this module's own master changelog; includeAll over
    │                                     2026/0.0.2/, same shape as task-service's/social-service's
    │                                     own changelog trees. Applied via the consolidated
    │                                     services-liquibase job in docker-compose.apps.yml — this
    │                                     module has no standalone content-service-liquibase.yml
    │                                     file of its own, unlike task-service/social-service (see
    │                                     root CLAUDE.md's Migrations — Liquibase section);
    │                                     spring.liquibase.enabled stays false on app boot, same
    │                                     convention as every other standalone service
    └── 2026/0.0.2/
        └── DKP-0031__add_content_service_tables.sql — fresh snapshot of CATEGORY/TAG/CONTENT_ITEM/
            CONTENT_ITEM_TAG/QUESTION_ANSWER/ARTICLE into a new `content` schema (not a replay of
            gateway's incremental DKP-0001..0018 history); AUTHOR_UUID (VARCHAR(36)) replaces
            gateway's plain AUTHOR_ID INTEGER outright — edited in place before this changeset ever
            ran against a real database, mirroring task-service's own ownerId→ownerUuid correction

content-service/
├── Dockerfile                     — multi-stage build, port 8085, mirrors task-service's exactly
└── src/main/resources/
    ├── application.yml            — server.port 8085, hibernate.default_schema: content,
    │                                 app.internal-api.key, app.seed.enabled
    └── data/
        ├── csv/categories.csv, csv/tags.csv — moved here from gateway's own resources
        └── question-answers/*.md  — 100 files, moved here from gateway's own resources
```

The indexing/RAG orchestration layer (`ContentIndexingService`, `IndexingQualityService`,
`EmbeddingIndexService`, `IngestionApi`/`Controller`) lives in `ai-service` — see that module's section.
It used to also host `PublicContentApi`/`Controller` (the read-only public content-browsing endpoints)
and `ContentPublishedEventListener`, back when `ai-service` depended on `content-service` for `ContentItem`
and it was the one module (besides `gateway`) that could already see both. Both moved/were deleted as part
of `content-service`'s own standalone-service extraction: `PublicContentApi`/`Controller` moved
back here (see `api/` above), and `ContentPublishedEventListener` was deleted outright as dead code (its
event never had a publisher). The indexing orchestration itself stays in `ai-service` — it's that module's
own pipeline, now reaching this module over HTTP (`ContentServiceClient` → `InternalContentApi`) instead of
a live JPA entity.

**No local `User` copy** — `ArticleApi`/`QuestionAnswerApi`'s `create()` endpoints take
`@CurrentUserId String authorUuid` directly, resolved by `infra`'s shared
`security.CurrentUserIdArgumentResolver`/`CurrentUserResolver` with zero database access.
This replaced an earlier design where `ArticleController`/`QuestionAnswerController` resolved the
author via `common`'s `UserRepository.findByEmail(...).map(User::getId)` — reverted once this
module was extracted, since that lookup only ever needed "who is the caller," and `authorId`/
`authorUuid` is write-once at creation, never read back or joined through anywhere in this module
(see the `project-microservices-extraction-plan` memory's "Option C" discussion).

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

Friend graph (search visibility, requests, friendships, blocking) plus chat (groups/channels, 1:1
DMs). **Now a standalone Spring Boot application, not part of the monolith** — the fourth module
pulled out, following the `ecommerce-service`/`identity-service`/`task-service` precedent (see the
`project-microservices-extraction-plan` memory for the full history).

**Extraction (done):** own `SocialServiceApplication` entry point, own `social` Postgres schema,
own JWT verification *and* its own full WebSocket/STOMP transport (`security/WebSocketConfig`/
`StompAuthChannelInterceptor` — relocated here from `gateway` in full; this is the only one of the
four extractions so far that had to bring real-time transport with it, not just REST), own port
(`8084`), own Liquibase changelog + `social-service-liquibase.yml` docker-compose file, own test
suite (relocated from `gateway`, see below). `gateway` no longer has a Maven dependency on this
module — it never called into this module's Java classes in-process to begin with (`FriendApi`/
`GroupApi`/`DmApi`/`UserApi` were already this module's own REST layer), but `gateway`'s
`WebSocketConfig`/`StompAuthChannelInterceptor`/`CurrentUserIdMessageArgumentResolver` *did* wire
this module's `GroupMessagingController`/`DmMessagingController` into a running broker — all three
were deleted from `gateway` outright once relocated here, since chat was `gateway`'s only STOMP use
case. **Not yet built:** the `gateway`-side HTTP proxy to this service — until that exists, it's
only reachable directly on its own port, same limitation `ecommerce-service`/`identity-service`/
`task-service` have.

**No coupling to `common.entity.User`, unlike `gateway`/`identity-service`** — a deliberate design
decision, not the default extraction pattern. This module genuinely needs a real local identity row
(unlike `ecommerce-service`/`task-service`'s claims-based "Option C" shape), since it searches/
lists/joins across *other* users' profile data — but instead of reusing the shared
`common.entity.User` class, it persists its own **lean, module-local** `entity.SocialProfile`
(table `social.PROFILE`) carrying only the columns this module's own code actually reads/writes
(verified by grepping real usages): `profileUuid`/`keycloakSubjectId`/`email` for JIT-provisioning
lookup, `username`/`firstName`/`lastName`/`profilePicture`/`status` for search/display, `seedId`
for the demo-data seeders — no `password`, OAuth `provider`, `role`, `emailVerified`, or `enabled`.
`role`/`provider`/`emailVerified` were also trimmed out of the public `UserInfoResponse` API shape
(previously riding along via MapStruct's auto-mapping) after confirming via `gui` grep that nothing
reads them off a friend's public profile — only off the *logged-in user's own* account dashboard,
an entirely different endpoint on `identity-service`. See `social-service/CLAUDE.md`'s "No coupling
to `common.entity.User`" rule for the full reasoning.

```
social-service/src/main/java/com/ttg/devknowledgeplatform/social/
├── SocialServiceApplication.java  — @SpringBootApplication entry point; sitting at this package
│                                     (not the shared root gateway's main class uses) keeps
│                                     content-service/ai-service/identity-service/task-service/
│                                     ecommerce-service out of this app's component scan (no Maven
│                                     dependency on any of them anyway). No @EntityScan/
│                                     @EnableJpaRepositories — this module doesn't touch
│                                     common.entity.User/common.repository.UserRepository at all;
│                                     default scanning already covers this module's own entity/
│                                     repository packages
├── security/
│   ├── SecurityConfig.java            — this app's own filter chain: everything requires auth
│   │                                     except /api/v1/users/public/** (public profile lookup),
│   │                                     the /ws/** handshake, and /actuator/**
│   ├── (no local KeycloakRealmRoleConverter.java anymore — uses the shared
│   │    infra.security.KeycloakRealmRoleConverter bean instead, see infra section above)
│   ├── KeycloakJwtAuthenticationConverter.java — kept local (one of only two in the reactor doing
│   │                                     real divergent work, identity-service's is the other):
│   │                                     JIT-provisions/refreshes this app's own local
│   │                                     SocialProfile row directly via SocialProfileRepository
│   │                                     (find by keycloakSubjectId, fallback email, write only if
│   │                                     changed) — inlined here (like the shared converter's own
│   │                                     shape used to be), not delegated (unlike identity-service's).
│   │                                     Delegates role-mapping to infra's shared
│   │                                     KeycloakRealmRoleConverter rather than duplicating it.
│   ├── CurrentUserResolver.java        — resolves the authenticated CustomOAuth2User principal's
│   │                                     UUID to this app's own local SocialProfile numeric PK
│   ├── WebSocketConfig.java            — relocated from gateway in full: registers /ws (no SockJS
│   │                                     fallback), simple broker on /topic + /queue, /app prefix,
│   │                                     wires StompAuthChannelInterceptor +
│   │                                     CurrentUserIdMessageArgumentResolver
│   └── StompAuthChannelInterceptor.java — relocated from gateway: CONNECT authenticates via this
│                                          module's own KeycloakJwtAuthenticationConverter;
│                                          SUBSCRIBE to /topic/channels/{id} authorizes via this
│                                          module's own GroupService.isChannelMember
├── config/web/
│   ├── WebMvcConfig.java               — registers CurrentUserIdArgumentResolver
│   ├── CurrentUserIdArgumentResolver.java — REST resolver, duplicated from gateway's, resolves
│   │                                     against this module's own SocialProfileRepository
│   └── CurrentUserIdMessageArgumentResolver.java — STOMP resolver, duplicated from gateway's,
│                                        same resolution target
├── entity/
│   ├── SocialProfile.java         — this module's own lean projection of a Keycloak identity —
│   │                                 see the module-level note above for the full column list and
│   │                                 why it isn't a copy of common.entity.User
│   ├── FriendRequest.java         — requester/addressee (SocialProfile), status (FriendRequestStatus)
│   ├── Friendship.java            — user1/user2 (SocialProfile), canonically ordered (user1.id < user2.id)
│   │                                 so each pair has exactly one row regardless of who sent the original request
│   ├── UserBlock.java             — blocker/blocked (SocialProfile); directional, independent of Friendship/FriendRequest
│   ├── Group.java                 — name only; maps to table MESSAGE_GROUP (GROUP is a reserved word in
│   │                                 PostgreSQL). No ownerId column — the owner is whichever GroupMember row
│   │                                 holds role = OWNER, a single source of truth instead of a duplicated ref
│   ├── GroupMember.java           — group/user (Group/SocialProfile), role (GroupMemberRole); one row per (group, user) pair
│   ├── Channel.java                — group (Group), name; unique per group, not globally. Every group member can
│   │                                 see every channel in this MVP — no private/restricted channel concept yet
│   ├── DmThread.java               — user1/user2 (SocialProfile), canonically ordered exactly like Friendship;
│   │                                 lastMessageAt (denormalized, same reasoning as ChatSession.lastActivityAt —
│   │                                 avoids a MAX(dteCreation) aggregate to render "my DMs, most recent first")
│   ├── DmMessage.java              — dmThread, sender (SocialProfile), messageType (MessageType), content + 4 nullable
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
│   ├── ProfileStatus.java         — ONLINE, OFFLINE, AWAY, BUSY; SocialProfile's presence enum —
│   │                                 deliberately not a reuse of common.enums.UserStatus, since
│   │                                 this module no longer maps common.entity.User at all
│   ├── FriendRequestStatus.java   — PENDING, ACCEPTED, REJECTED, CANCELLED
│   ├── RelationshipStatus.java    — STRANGER, REQUEST_SENT, REQUEST_RECEIVED, FRIENDS, BLOCKED; computed
│   │                                 (not persisted) per profile/search-result view from the viewer's perspective
│   ├── GroupMemberRole.java       — OWNER, ADMIN, MEMBER; exactly one OWNER per group
│   └── MessageType.java           — TEXT, IMAGE, FILE; tags the primary content for rendering only — text and
│                                     an attachment may coexist on one row regardless of this value
├── repository/
│   ├── SocialProfileRepository.java  — this module's own repository over SocialProfile; the
│   │                                    replacement for common's shared UserRepository, which this
│   │                                    module no longer uses at all
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
│       └── UserSpecification.java — now specified over SocialProfile, not common.entity.User; fuzzy
│                                     username/name match, exact email match, excludes any user
│                                     blocked in either direction relative to the viewer
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
│   │                                   membership check, used by this module's own StompAuthChannelInterceptor
│   │                                   to authorize a channel-topic subscription before the broker admits it)
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
│       │                              docs/SEED_DATA_AUTHORING_GUIDE.md)
│       ├── SocialProfileSeeder.java — this module's own copy of the demo-user seeder, reading this
│       │                              module's own data/csv/users.csv (duplicated from gateway's
│       │                              file, same id/seed-key values) — necessary because seed
│       │                              accounts have no real Keycloak identity to JIT-provision
│       │                              from, and this module has no access to gateway's
│       │                              resources/classpath at runtime anymore
│       ├── FriendGraphSeeder.java   — data/csv/friend-requests.csv (requesterId, addresseeId, status); an
│       │                              ACCEPTED row also inserts the matching Friendship, canonically ordered,
│       │                              mirroring FriendServiceImpl.acceptRequest's production behavior. Does
│       │                              NOT extend infra's CsvSeeder — an ACCEPTED row persists two entities,
│       │                              which doesn't fit CsvSeeder's one-entity-per-row shape
│       ├── UserBlockSeeder.java     — data/csv/user-blocks.csv (blockerId, blockedId); extends infra's CsvSeeder
│       ├── DmThreadSeeder.java      — one random lorem-ipsum DM conversation per existing Friendship row
│       └── DataSeedingRunner.java   — this module's own seeding orchestrator (ApplicationRunner,
│                                      app.seed.enabled-gated), running SocialProfileSeeder →
│                                      FriendGraphSeeder → UserBlockSeeder → DmThreadSeeder — not a
│                                      continuation of gateway's runner, which no longer references
│                                      any of these three seeders after this module's extraction
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
│   │                                    profile lookup directly via this module's own
│   │                                    `SocialProfileRepository` and this module's own
│   │                                    `FriendMapper.toUserInfo` (a local `UserInfoResponse`
│   │                                    DTO, duplicated from `identity-service`'s equivalent, minus
│   │                                    the `role`/`provider`/`emailVerified` fields) before applying
│   │                                    `FriendService` relationship enrichment
│   └── impl/                         — FriendController / GroupController / DmController /
│                                        GroupMessagingController / DmMessagingController / UserController
├── mapper/                           — MapStruct: FriendMapper, MessagingMapper (both abstract classes —
│                                        need an injected `infra`-owned `StorageService` for presigned avatar
│                                        URLs, and MapStruct interfaces can't hold instance fields);
│                                        `MessagingMapper` uses `FriendMapper` for SocialProfile → UserSummaryResponse
└── dto/
    ├── friend/                       — Java records: UserSummaryResponse, UserSearchResultResponse,
    │                                    FriendRequestResponse, FriendSummaryResponse, UserInfoResponse
    │                                    (the last trimmed of role/provider/emailVerified — see above)
    └── messaging/                    — GroupResponse/CreateGroupRequest, GroupMemberResponse, ChannelResponse/
                                         CreateChannelRequest, ChangeRoleRequest, ChannelMessageResponse,
                                         DmMessageResponse, DmThreadResponse, SendMessageRequest,
                                         MessageAttachmentRequest/Response, WsErrorResponse (STOMP error payload)
```

`GroupService` and `DmService` are deliberately two services, not one — they gate access differently
(open-add + role checks vs. friend-required) and share no entities, so combining them would mix two
unrelated authorization models in one class. `DmService` depends on `FriendService` as a collaborator
(reusing its relationship lookup) rather than querying `FriendshipRepository`/`UserBlockRepository`
directly, avoiding a second implementation of the canonicalization + mutual-invisibility logic.
REST layer: this module's own `GroupApi`/`GroupController` and `DmApi`/`DmController`, DTOs in
`dto/messaging/`, `MessagingMapper` — see `api/` above. No upload endpoint yet for message attachments;
`MessageAttachmentRequest.objectKey` assumes the client already has a MinIO object key from
somewhere else.

**Liquibase:** own changelog tree (`database/sql/social-service.xml` + `2026/0.0.2/*.sql`), applied
via the standalone `social-service-liquibase.yml` docker-compose file. `DKP-0029` adds
`social.PROFILE`; `DKP-0030` adds a fresh snapshot of the friend-graph + chat tables — the final
shape `gateway`'s old `DKP-0015`/`DKP-0019` reached — with every FK repointed at `social.PROFILE`
instead of `product.USER`. Those `gateway`-tree changesets are untouched (frozen, already-run
history) but now describe an orphaned table set `gateway`'s own Spring context no longer maps any
entity to.

**Test suite:** `src/test/java/.../ws/` — `AbstractStompIntegrationTest` (Testcontainers Postgres/
MinIO/Keycloak — no Redis, unlike `gateway`'s original version of this suite, since this module has
no Redis-backed bean of its own) + `DmMessagingStompIntegrationTest`, relocated from `gateway` now
that this module has its own `@SpringBootApplication` to boot them against.

**Compiles cleanly** (full reactor including the extraction changes; needs `JAVA_HOME` pointed at a
JDK 21 install) but hasn't been run against a real Postgres yet — same unverified-at-runtime caveat
as `ecommerce-service`/`identity-service`/`task-service`.

---

## ai-service

RAG pipeline (embedding, vector search, LLM generation via LangChain4j), the RAG-chat REST feature,
and the content-indexing orchestration layer. **Now a standalone Spring Boot application, not part
of the monolith** — the sixth and final module pulled out, following the
`ecommerce-service`/`identity-service`/`task-service`/`social-service`/`content-service` precedent
(see the `project-microservices-extraction-plan` memory for the full history of all six). `gateway`
has **zero** embedded feature modules remaining after this extraction. Concretely: its own
`AiServiceApplication` entry point (`@SpringBootApplication` + `@ConfigurationPropertiesScan` +
`@EnableAsync`, no `@EntityScan`/`@EnableJpaRepositories` — this module never touches
`common.entity.User`), its own `ai` Postgres schema (same `dev-premier` database, its own
`hibernate.default_schema: ai`), its own port (`8086`), and its own Liquibase changelog
(`database/sql/ai-service.xml` + `DKP-0032`, a fresh snapshot of `CONTENT_EMBEDDING`/`CHAT_SESSION`/
`CHAT_MESSAGE`/`SYS_PARAM`/`PIPELINE_METRICS`, not a replay of `gateway`'s incremental history — same
convention every other standalone service's changelog already follows). Unlike `content-service`'s
extraction, this one needed no HTTP rewrite of its own — `ai-service` already had zero Maven
dependency on `content-service` going into this extraction (severed during *that* module's own
extraction, not this one); the work here was giving `ai-service` its own app shell/security/schema
and severing `gateway`'s Maven dependency on `ai-service`, plus relocating the runtime
infrastructure `ai-service`'s own controllers/services actually use (`sseStreamExecutor`, the
Bucket4j Redis connection) out of `gateway`. Its own `Dockerfile` and
`docker-compose.apps.yml`/`ai-service-liquibase.yml` wiring are in place now
(port `8086`); `gateway`-side HTTP proxying for end-user traffic to this service is not built yet,
same as every other standalone service in this reactor.

```
ai-service/src/main/java/com/ttg/devknowledgeplatform/ai/
├── AiServiceApplication.java  — @SpringBootApplication + @ConfigurationPropertiesScan (required —
│                                 this module no longer rides on gateway's scan) + @EnableAsync;
│                                 no @EntityScan/@EnableJpaRepositories (no common.entity.User usage)
├── security/                  — this app's own filter chain (mirrors content-service's/
│   │                             task-service's exactly): SecurityConfig (@EnableWebSecurity +
│   │                             @EnableMethodSecurity — needed here since IngestionApi is the only
│   │                             @PreAuthorize("hasRole('ADMIN')") method left in the whole reactor;
│   │                             rules: /actuator/** permitAll, /api/v1/admin/** hasRole(ADMIN),
│   │                             everything else authenticated). No local KeycloakRealmRoleConverter/
│   │                             KeycloakJwtAuthenticationConverter classes anymore — both are
│   │                             shared infra.security beans now (builds CustomOAuth2User straight
│   │                             from JWT claims — sub → userUuid, no DB read/write at all — see
│   │                             infra section above). No local CurrentUserResolver either — uses
│   │                             infra.security.CurrentUserResolver.resolveUserUuid(...). No local
│   │                             JsonAuthenticationEntryPoint either — also a shared infra.security
│   │                             bean now (byte-identical to gateway's own former copy). No CorsConfig here anymore (used to
│   │                             be narrowed to just /api/v1/chat/stream, then deleted outright
│   │                             once gateway's own ChatStreamProxyController landed) — every
│   │                             request this module receives now comes from another server, not
│   │                             a browser, so SecurityConfig dropped its .cors(...) wiring too
├── api/                       — REST layer, moved in from `gateway` (named `api` at the time —
│   │                             content+AI orchestration and the self-contained chat feature are
│   │                             both owned here now — see ai-service/CLAUDE.md for why the old
│   │                             "stays in gateway" rule no longer applies to this specific module pairing)
│   ├── ChatApi.java           — /api/v1/chat: chat(), chatStream() (SSE), listSessions(), getSessionHistory()
│   ├── IngestionApi.java      — /api/v1/admin/indexing: index(), indexAll(), deleteIndex(), refreshCorpus(); class-level @PreAuthorize("hasRole('ADMIN')")
│   ├── EmbeddingIndexApi.java — /api/v1/admin/embeddings: list() — paged, filterable content+embedding-stats view
│   ├── PipelineMetricsApi.java — /api/v1/admin/pipeline-metrics: getSummary(MetricsPeriod)
│   └── impl/
│       ├── ChatController.java            — orchestrates RagQueryService + ChatSessionService + SseStreamTemplate
│       ├── IngestionController.java       — delegates to ContentIndexingService + CorpusStatisticsService
│       ├── EmbeddingIndexController.java  — delegates to EmbeddingIndexService
│       └── PipelineMetricsController.java — delegates to PipelineMetricsSummaryService
│   (PublicContentApi/PublicContentController used to live here — moved back to content-service in
│   that module's extraction step 5; see content-service's own api/ section above)
├── config/
│   ├── sse/
│   │   ├── SseStreamTemplate.java  — reusable SSE-endpoint helper; owns SSE_TIMEOUT_MS (60_000L) —
│   │   │                             this module's own config/web/ChatMvcConfig.configureAsyncSupport
│   │   │                             reads this constant locally now (gateway's WebMvcConfig, the old
│   │   │                             reader, was deleted outright once this module went standalone)
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
│   │   ├── ChatRateLimitInterceptor.java     — HandlerInterceptor; consumes one ChatRateLimiter token
│   │   │                                        per POST /api/v1/chat/** request
│   │   └── ChatMvcConfig.java                 — this module's own WebMvcConfigurer bean, registers
│   │                                            ChatRateLimitInterceptor (addInterceptors) AND
│   │                                            infra's shared CurrentUserIdArgumentResolver
│   │                                            (addArgumentResolvers — a local copy of this class
│   │                                            existed briefly after this module's standalone
│   │                                            extraction, then moved to infra.security once
│   │                                            byte-identical to content-service's/task-service's
│   │                                            own copies) — Spring composes every WebMvcConfigurer
│   │                                            in the context automatically, so this module doesn't
│   │                                            need any gateway-hosted WebMvcConfig to register
│   │                                            either on its behalf (gateway has none left at all)
│   ├── thread/
│   │   ├── ThreadPoolProperties.java — @ConfigurationProperties at app.threads.*; sseExecutor:
│   │   │                                corePoolSize (10), maxPoolSize (50), queueCapacity (100),
│   │   │                                awaitTerminationSeconds (30); moved here **verbatim** from
│   │   │                                gateway's now-deleted config/thread/ThreadPoolProperties
│   │   └── ThreadPoolConfig.java      — Factory Method: creates sseStreamExecutor (SSE/MVC async
│   │                                     dispatch), registered with ExecutorServiceMetrics
│   │                                     (Micrometer Decorator); moved here **verbatim** from
│   │                                     gateway's now-deleted config/thread/ThreadPoolConfig — no
│   │                                     behavioral change, ChatController/SseStreamTemplate already
│   │                                     lived here
│   ├── RedisConfig.java       — this module's own Redis wiring, moved in from gateway's
│   │                             RedisCacheConfig. Only the bucket4jRedisConnection bean
│   │                             (StatefulRedisConnection<String,byte[]> for ChatRateLimiter's
│   │                             binary bucket state) made the move — RedisCacheConfig's other two
│   │                             beans (cacheManager, baseRedisCacheConfiguration — the
│   │                             @EnableCaching machinery) were found to have zero
│   │                             @Cacheable/@CacheEvict/@CachePut consumers anywhere in the reactor
│   │                             during this extraction, so they were deleted outright rather than
│   │                             moved (see the dead-code bullet in ai-service/CLAUDE.md's Rules
│   │                             section) — infra's CacheTtlProperties/CacheNames, which backed
│   │                             those two beans, were deleted in the same pass, now fully orphaned
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
│   ├── PromptsLoader.java     — @Configuration that reads prompts/*.txt and produces LoadedPrompts bean
│   └── ContentServiceClientProperties.java — @ConfigurationProperties at app.content-service.*;
│                                 fields: baseUrl, internalApiKey (must match content-service's own
│                                 app.internal-api.key) — consumed by ContentServiceClientImpl
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
│   ├── client/
│   │   └── ContentItemDto.java             — this module's own deserialized copy of content-service's
│   │                                          InternalContentItemResponse JSON shape (duplicated, not shared —
│   │                                          same "duplicate a cross-service DTO rather than share it"
│   │                                          convention identity-service's/social-service's own local
│   │                                          KeycloakJwtAuthenticationConverter still follows, even
│   │                                          though the claims-only variant is a shared infra bean now);
│   │                                          returned by ContentServiceClient
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
├── event/                                     — ContentPublishedEventListener used to live here (moved in
│                                                 from gateway) but was deleted as dead code in content-service's
│                                                 extraction step 5 — its event never had a publisher wired up
│   ├── PipelineCompletedEvent.java         — record event published by RagQueryServiceImpl after each pipeline execution;
│   │                                        carries RagPipelineContext + AnswerQualityVerdict
│   └── PipelineCompletedEventListener.java — extends AsyncEventHandler<PipelineCompletedEvent>; @Transactional;
│                                            maps event → PipelineMetrics entity; resolveTraceId() binds MDC for logging
├── entity/
│   ├── ContentEmbedding.java         — embedding vector (1536-dim), chunkText, sourceType,
│   │                                    chunkIndex, modelName, tokenCount, contentItemId (plain
│   │                                    column, not a @ManyToOne FK — ContentItem lives in
│   │                                    content-service's own database, a genuinely separate
│   │                                    process/schema now; see root CLAUDE.md's Long-term direction),
│   │                                    metadata (JSONB: categoryId, categoryName, tagIds, tagNames)
│   ├── PipelineMetrics.java          — append-only analytics entity (no AbstractEntity); columns: traceId, createdAt,
│   │                                    abortedAt, candidateCount, afterScoringCount, selectedCount,
│   │                                    evidenceMeanScore, effectiveSimThreshold, answerContextSim, answerQuerySim, answerDrifted;
│   │                                    latency: contextualizationMs, embeddingMs, retrievalMs, llmGenerationMs, totalPipelineMs;
│   │                                    tokens: contextualizationInputTokens, contextualizationOutputTokens, embeddingTokens,
│   │                                    qualityEmbeddingTokens, generationInputTokens, generationOutputTokens, estimatedCostUsd;
│   │                                    attribution: userUuid (plain String column, no FK — claims-based, mirroring
│   │                                    task-service's ownerUuid/content-service's authorUuid; renamed from userId:
│   │                                    Integer during this module's standalone extraction, USER_ID → USER_UUID in
│   │                                    DKP-0032 — analytics rows must survive user deletion regardless),
│   │                                    chatModel (id of the resolved chat model profile; NULL pre-DKP-0012 rows)
│   ├── ChatSession.java              — userUuid (plain String column, renamed from userId: Integer during this
│   │                                    module's standalone extraction, same claims-based reasoning as PipelineMetrics
│   │                                    above), title, lastActivityAt, summary (TEXT); parent of ChatMessage rows;
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
│   │                                    attribution: userUuid (nullable String, renamed from userId: Integer)
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
│   ├── ContentEmbeddingRepository.java   — findTopSimilarIds (pgvector <=>), findAllById (plain
│   │                                       JpaRepository method now — no eager join needed since
│   │                                       contentItemId is a plain column), findDistinctContentItemIds
│   │                                       (backs EmbeddingIndexServiceImpl's indexed filter, see below),
│   │                                       findStatsByContentItemIds(List<Integer>) → List<EmbeddingStatsProjection>
│   │                                       (JPQL: COUNT/SUM/MAX grouped by content item ID),
│   │                                       computeGlobalCentroid(), computeCentroidBySourceType(String) —
│   │                                       its 3 native queries' hardcoded product.content_embedding
│   │                                       table prefix was fixed to ai.content_embedding during this
│   │                                       module's standalone extraction (a plain @Table(schema=...)
│   │                                       removal doesn't touch a hand-written native query's own
│   │                                       schema-qualified table name)
│   ├── PipelineMetricsRepository.java    — JpaRepository<PipelineMetrics, Integer>; append-only analytics writes;
│   │                                        fetchSummary(Instant) — native query using percentile_cont WITHIN GROUP;
│   │                                        its hardcoded product.PIPELINE_METRICS table prefix was fixed to
│   │                                        ai.PIPELINE_METRICS during this module's standalone extraction, same
│   │                                        reasoning as ContentEmbeddingRepository above
│   ├── ChatSessionRepository.java        — findByIdAndUserUuid (ownership check, renamed from
│   │                                        findByIdAndUserId during this module's standalone
│   │                                        extraction), findSessionSummariesByUserUuid (renamed from
│   │                                        findSessionSummariesByUserId; JPQL "new" projection into
│   │                                        ChatSessionSummaryDto, COUNT(m) join)
│   ├── ChatMessageRepository.java        — findByChatSession_IdOrderByTurnIndexAsc/Desc, findMaxTurnIndexBySessionId
│   └── SysParamRepository.java           — JpaRepository<SysParam, Integer>; findByName(ParamKey); moved in
│                                            from common — audit found zero real consumers outside this module
└── service/
    ├── ContentIngestionService.java             — chunks text + stores embeddings; ingest() takes
    │                                               Integer contentItemId, ContentType sourceType
    │                                               (not a live ContentItem — see entity/ above)
    ├── ContentServiceClient.java                 — interface: getById/listByStatus/list/updateQualityScore
    │                                               against content-service's /internal/content-items/**
    │                                               API; replaces the direct ContentItemRepository/
    │                                               QuestionAnswerRepository/ArticleRepository injections
    │                                               ContentIndexingServiceImpl/EmbeddingIndexServiceImpl
    │                                               used before the content-service extraction's step 4
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
    │                                               primary overloads accept ConversationContext + RagFilter + userUuid + chatModel
    │                                               (renamed from userId: Integer during this module's standalone extraction)
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
        ├── ContentServiceClientImpl.java               — RestClient-backed; base-url + shared
        │                                                 X-Internal-Api-Key from ContentServiceClientProperties,
        │                                                 plus TraceparentClientHttpRequestInterceptor
        │                                                 registered via .requestInterceptor(...)
        ├── TraceparentClientHttpRequestInterceptor.java — stamps traceparent per-call from the
        │                                                 current thread's MDC (infra's TraceContext);
        │                                                 falls back to a fresh, disconnected trace on
        │                                                 the async indexing pipeline's own worker
        │                                                 thread, where MDC doesn't propagate — known,
        │                                                 documented gap, not solved here
        ├── ContentIndexingServiceImpl.java            — resolves question/article text from ContentServiceClient's
        │                                                flattened ContentItemDto → ContentIngestionService.ingest();
        │                                                also runs IndexingQualityService and persists the quality
        │                                                score back via ContentServiceClient.updateQualityScore
        ├── IndexingQualityServiceImpl.java             — mean cosine similarity of chunk embeddings vs corpus centroid
        │                                                (CorpusStatisticsService), compared against IndexingConfig threshold
        ├── EmbeddingIndexServiceImpl.java              — two-query pattern: ContentServiceClient.list() (HTTP) +
        │                                                 batch stats query; `indexed` filter reads this module's own
        │                                                 embedded-id set first (findDistinctContentItemIds), then
        │                                                 passes it as ids=/excludeIds= to content-service's query
        │                                                 (a cross-service EXISTS join is no longer possible)
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
│   │   (no local KeycloakRealmRoleConverter.java/KeycloakJwtAuthenticationConverter.java
│   │    anymore — both moved to infra.security as shared beans, see infra section above;
│   │    picked up via this module's existing @ComponentScan reaching infra)
│   (no local CurrentUserResolver.java anymore — uses the shared
│    infra.security.CurrentUserResolver.resolveUserUuid(...) instead, see infra section above)
├── config/web/
│   └── WebMvcConfig.java               — registers infra's shared CurrentUserIdArgumentResolver,
│                                         assigning the resolveUserUuid result to this module's own
│                                         ownerUuid vocabulary — no local resolver class of this
│                                         module's own anymore, see infra section above (no STOMP
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
(`task-service/.../database/sql/task-service.xml` + `2026/0.0.2/*.sql`), applied via the standalone
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

Keycloak now owns login/password/OTP/OAuth2-brokering entirely (see `docs/CHANGELOG.md`'s Keycloak
migration entries) — this module narrowed to JIT-syncing a local `User` row from a verified
Keycloak identity, the authenticated user's own profile, and — reintroduced in a later
`docs/CHANGELOG.md` `[Unreleased]` entry — **registration**, now implemented as a server-side call
to Keycloak's own Admin REST API (`KeycloakAdminService`) rather than local password hashing, since
Keycloak's token endpoint can only authenticate an existing user, never create one. **Now a
standalone Spring Boot application, not part of the monolith** — see the
`project-microservices-extraction-plan` memory for the full extraction history.

**Extraction (done):** own `IdentityServiceApplication` entry point, own `identity` Postgres schema
(separate from the monolith's `product` schema), own JWT verification (`security/` — verifies
tokens issued by Keycloak, never issues its own), own port (`8082`), own Liquibase changelog
applied via the consolidated `services-liquibase` job in `docker-compose.apps.yml` (no standalone
`identity-service-liquibase.yml` file — see root `CLAUDE.md`'s Migrations section). `gateway` no
longer has a Maven dependency on this module, and vice versa was never true. **Not yet built:** the
`gateway`-side HTTP proxy to this service — until that exists, it's only reachable directly on its
own port, same limitation `ecommerce-service` has.

**Now also the sole owner of `User`/`UserRepository`/`UserProvider`/`UserRole`/`UserStatus`** — all
five moved here outright from `common` once `gateway` retired its own local `User` copy entirely
(see `gateway`'s section below and `docs/CHANGELOG.md`). `IdentityServiceApplication` dropped its
`@EntityScan`/`@EnableJpaRepositories` overrides as a direct result — default Spring Boot component
scanning now covers this module's own `entity`/`repository` packages without help, since they're no
longer off in `common`.

```
identity-service/src/main/java/com/ttg/devknowledgeplatform/identity/
├── IdentityServiceApplication.java    — @SpringBootApplication entry point; sitting at this package
│                                        (not the shared root gateway's main class uses) keeps
│                                        content-service/social-service/ai-service/task-service out
│                                        of this app's component scan (no Maven dependency on any of
│                                        them anyway). No @EntityScan/@EnableJpaRepositories anymore
│                                        — used to widen JPA scanning to reach common.entity/
│                                        common.repository when User/UserRepository lived there;
│                                        default scanning already covers this app's own entity/
│                                        repository packages now that both moved here
├── entity/
│   └── User.java                      — userUuid, email, username, password, firstName, lastName,
│                                        profilePicture, provider (UserProvider), role (UserRole),
│                                        providerId, emailVerified, status (UserStatus, presence),
│                                        enabled, seedId (unused today — this module needs no seed
│                                        data of its own), keycloakSubjectId. The sole system-of-
│                                        record for user identity in this reactor now — moved here
│                                        from `common` outright, not re-exported. `@Table`
│                                        deliberately still carries no hardcoded schema — this app's
│                                        own `hibernate.default_schema: identity` resolves it to
│                                        `identity.USER` at runtime, same convention every entity in
│                                        this reactor follows
├── enums/
│   ├── UserProvider.java
│   ├── UserRole.java
│   └── UserStatus.java
├── repository/
│   └── UserRepository.java            — JpaRepository<User, Integer> + JpaSpecificationExecutor<User>;
│                                        moved here from `common` alongside `User`. The
│                                        JpaSpecificationExecutor capability has no real consumer
│                                        today (it used to back social-service's dynamic user search
│                                        before that module moved to its own SocialProfile/
│                                        SocialProfileRepository) — kept rather than silently dropped
├── api/
│   ├── AuthApi.java                   — GET /api/v1/auth/user (renamed from OAuth2Api once every
│   │                                     other pre-Keycloak endpoint was deleted — Keycloak's own
│   │                                     /userinfo doesn't cover this app's avatar/username shape)
│   │                                     + POST /api/v1/auth/register (added back later — creates
│   │                                     the Keycloak account via KeycloakAdminService; the one
│   │                                     endpoint on this module that's `permitAll`, no token yet)
│   ├── UserApi.java                   — PUT /me, POST /me/avatar ONLY — pure profile mutation. GET
│   │   │                                 /public/{userUuid} and GET /search live in `social-service`'s
│   │   │                                 own `UserApi` instead (see that section) since they need
│   │   │                                 `FriendService` for relationship enrichment — that module
│   │   │                                 now resolves the base lookup itself rather than reaching
│   │   │                                 into this now-standalone service
│   │   └── impl/                      — AuthController / UserController
├── mapper/
│   └── UserMapper.java                — entity → dto/UserInfoResponse
├── config/
│   ├── KeycloakAdminProperties.java   — server-url/realm/client-id/client-secret for Keycloak's
│   │                                    Admin REST API (identity-service-admin confidential
│   │                                    client's service account, manage-users role — see
│   │                                    docker/keycloak/realm-export.json). Needs its own explicit
│   │                                    @Import on IdentityServiceApplication (plain
│   │                                    @ConfigurationProperties, no @Component stereotype)
│   └── KeycloakAdminConfig.java       — the Keycloak admin-client bean (client_credentials grant);
│                                        no explicit @Import needed, default scanning covers it
├── dto/
│   ├── UserInfoResponse.java
│   ├── user/UpdateProfileRequest.java
│   └── auth/{RegisterRequest,RegisterResponse}.java — request/response for POST /register;
│                                        RegisterResponse matches gui's own RegisterResponse type
│                                        exactly, since the account is created pre-verified and gui
│                                        logs the user in itself afterward (no tokens in this response)
├── exception/
│   └── IdentityErrorCode.java         — this module's first ErrorCode enum: EMAIL_ALREADY_EXISTS,
│                                        KEYCLOAK_USER_CREATE_FAILED, EMAIL_ALREADY_VERIFIED,
│                                        VERIFICATION_EMAIL_SEND_FAILED, KEYCLOAK_USER_UPDATE_FAILED
└── security/
    ├── SecurityConfig.java            — everything requires auth except `/actuator/**` and
    │                                    `POST /api/v1/auth/register` (a brand-new user has no token
    │                                    yet) — no admin surface here, unlike
    │                                    content-service/ecommerce-service; pure OAuth2 resource
    │                                    server, verifies bearer tokens against Keycloak's JWKS
    ├── (no local KeycloakRealmRoleConverter.java anymore — uses the shared
    │    infra.security.KeycloakRealmRoleConverter bean instead, see infra section above)
    ├── KeycloakJwtAuthenticationConverter.java — kept local (one of only two in the reactor doing
    │                                    real divergent work, social-service's is the other); the
    │                                    one converter in the reactor that still *delegates* its
    │                                    JIT-provisioning rather than inlining it: calls this
    │                                    module's own in-process
    │                                    `service/UserService.findOrCreateFromKeycloak` directly,
    │                                    since both live in this same standalone app. Delegates
    │                                    role-mapping to infra's shared KeycloakRealmRoleConverter
    │                                    too, rather than duplicating that half of the work.
    └── service/                       — UserService/Impl, narrowed to findOrCreateFromKeycloak
                                          (KeycloakUserInfo carrier record, same package),
                                          resolveCurrentUser, findByEmail/
                                          findByUserUuid(Optional)/findById, updateStatus,
                                          updateProfile (renames the user in Keycloak first via
                                          KeycloakAdminService.updateUsername when the username
                                          actually changed, before writing the local row — Keycloak
                                          re-syncs preferred_username into this row on every
                                          request, so a local-only rename would just be reverted on
                                          the caller's next call), updateAvatar; plus
                                          KeycloakAdminService/Impl — createUser(RegisterRequest)
                                          derives username from the email's local part
                                          (deriveUsernameBase, sanitized to [a-z0-9_], capped at 30
                                          chars) with numeric-suffix collision retry (withSuffix,
                                          up to 50 attempts, only on an actual username conflict —
                                          an email conflict still fails as EMAIL_ALREADY_EXISTS);
                                          resendVerificationEmail; updateUsername/
                                          assignDerivedUsername (the latter for a brand-new
                                          Google/Facebook login's Keycloak-default username==email)
                                          share a private applyUsername rename-attempt helper,
                                          mapping a Keycloak 409 to
                                          CommonErrorCode.USER_USERNAME_ALREADY_EXISTS
```

**Deleted outright** (all superseded by Keycloak): `security/JwtTokenProvider`,
`security/jwt/{TokenClaims,AccessTokenClaims,RefreshTokenClaims}`, `security/PasswordEncoderConfig`,
`security/service/{CustomOAuth2UserService,CustomOidcUserService}`,
`security/handler/OAuth2LoginSuccessHandler`, `security/service/StateTokenService`(`Impl`),
`security/service/RefreshTokenBlacklistService`(`Impl`), `service/{OtpService,EmailService}`(`Impl`),
the original `dto/auth/*`/`dto/RegisterRequest` (password-confirmation/OTP-oriented — a new, smaller
`dto/auth/{RegisterRequest,RegisterResponse}` pair came back later for the revived `register`
endpoint, see the tree above; not the same classes),
`dto/{OAuth2UserInfo,GoogleOAuth2UserInfo,FacebookOAuth2UserInfo,OAuth2UserInfoFactory}`, and (as
part of this extraction) `service/seed/UserSeeder` — relocated to `gateway` at the time, since it
only ever wrote via `common`'s `UserRepository` directly and `gateway` still needed to seed its own
`product.USER` for the modules still embedded there. `UserSeeder` was later deleted outright in
`gateway` too, once `product.USER` itself was dropped (see `gateway`'s section below) — it has no
home anywhere in this reactor anymore. This module needs no seed data of its own — a seeded demo
account has no matching Keycloak identity, so this module's own `identity.USER` table only ever
fills via JIT-provisioning on a real login. The Keycloak-migration pom cleanup (JJWT,
Redis-for-blacklist, mail-for-OTP — all left over from before those classes were deleted) happened
alongside this extraction too.

`gateway`'s own `KeycloakJwtAuthenticationConverter` used to inline this same find-or-create logic
directly via `common`'s `UserRepository`, JIT-provisioning its own local `User` copy into
`product.USER`, once calling into this module's `UserService.findOrCreateFromKeycloak` across a
module boundary stopped being possible. It has since dropped that entirely — `gateway` now uses
`infra`'s shared, claims-only `KeycloakJwtAuthenticationConverter`, same shape as `ecommerce-service`'s/`task-service`'s/
`content-service`'s/`ai-service`'s converters, since nothing in `gateway` ever read that row back
(zero REST controllers). **This module (`identity-service`) is now the sole deployable in the whole
reactor that persists a `User` row at all** — see root `CLAUDE.md`'s Security section.

**Compiles cleanly** (full reactor; needs `JAVA_HOME` pointed at a JDK 21 install) but hasn't been
run against a real Postgres yet — same unverified-at-runtime caveat as `ecommerce-service`.

---

## ecommerce-service

Study-project e-commerce vertical slice, **and now a standalone Spring Boot application, not part
of the monolith**. Full scope/rationale for all five epics lives in `docs/user-stories/`
(`README.md` + `01-catalog-search.md` through `05-reviews-recommendations.md`).
**Epic 1 (Catalog & Search)** is fully built (all 7 user stories): admin
CRUD for `ProductCategory`/`Product` including independent variant/image add-remove-reorder, the
outbox relay, and a public browse/search/detail surface with attribute-value filtering.
**Epic 2 (Cart & Checkout) is now fully built** — the cart half (US-2.1–2.4), showcasing this
epic's own locked pattern (Redis as a *primary* store, not a cache), and checkout (US-2.5–2.7,
`Address`/`Order`/`OrderLine`/`CheckoutService`) on top of it.

**Extraction (done):** own `EcommerceServiceApplication` entry point, own `ecommerce` Postgres
schema (same `dev-premier` database as the monolith, not a separate database instance —
per-service-per-schema, see root `CLAUDE.md`'s Database Conventions), own JWT verification
(`security/` — verifies tokens issued elsewhere, never issues its own), own port (`8081`), own
Liquibase changelog applied via the consolidated `services-liquibase` job — **this module has no
standalone `ecommerce-service-liquibase.yml` file of its own** (an earlier revision of this
section wrongly claimed one existed; only `task-service`/`social-service` have that leftover
single-service compose file, see root `CLAUDE.md`'s Database Conventions section). `gateway` no
longer has a Maven dependency on this module, but **does now proxy external client traffic to it**
— `GatewayRoutesConfig`'s `ecommerceServiceRoutes()` bean routes `/api/v1/admin/products/**`,
`/api/v1/admin/product-categories/**`, and `/api/v1/public/products/**` (an earlier revision of
this section said this proxy wasn't built yet; it is).

**Compiles cleanly** (full reactor including the extraction changes; needs `JAVA_HOME` pointed at
a JDK 21 install) but hasn't been run against a real Postgres yet — the Liquibase migration
against the `ecommerce` schema, the native SQL in `ProductSearchViewRepository.search`, and the JWT
verification path are all still unverified at runtime.

**Does not persist a `User` row at all** — none of this module's own entities have a foreign key
onto a user, so `infra`'s shared, claims-only `KeycloakJwtAuthenticationConverter` only ever needs
"who is the caller, and are they an admin," both fully answerable from the verified JWT's claims. An earlier revision briefly
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
│   ├── OutboxEvent.java        — shared transactional-outbox table every future epic will reuse;
│   │                              status (OutboxEventStatus: PENDING/PROCESSING/PROCESSED/FAILED)
│   │                              is the relay's claim/dispatch signal, attemptCount/lastError
│   │                              make a poison message diagnosable; aggregateType is an enum
│   │                              (OutboxAggregateType, DB CHECK-backed — small, slow-growing set);
│   │                              eventType stays a plain string — one Java field can only be one
│   │                              enum type, and every future epic keeps adding its own event
│   │                              types to this same shared table
│   ├── Address.java            — @Embeddable value object (fullName/line1/line2/city/state/
│   │                              postalCode/country), embedded on Order — no standalone table;
│   │                              Epic 2 locked "single inline address, no saved address book"
│   ├── Order.java               — table CUSTOMER_ORDER, not ORDER (a reserved SQL keyword in
│   │                              PostgreSQL, same reason social-service's Group maps to
│   │                              MESSAGE_GROUP); ownerUuid is a plain claims-based column, never
│   │                              a User FK; shippingAddress/subtotal/shippingFee/total are all
│   │                              snapshotted at creation; lines/statusHistory both cascade
│   │                              ALL/orphanRemoval. Epic 3 (docs/user-stories/
│   │                              03-order-lifecycle-inventory.md) added idempotencyKey (US-3.3,
│   │                              nullable, stamped at PENDING->PAYMENT_PROCESSING),
│   │                              paymentProcessingStartedAt (US-3.4's reconciliation clock), and
│   │                              cancelRequested (US-3.6's queued-cancel-mid-payment flag)
│   ├── OrderLine.java           — productVariantId is a plain column, deliberately not a
│   │                                ProductVariant FK (ProductServiceImpl.removeVariant can
│   │                                hard-delete a variant outright); sku/productName/unitPrice are
│   │                                copied at purchase time for the same reason; no stored
│   │                                lineTotal — derived at read time by CheckoutMapper
│   └── OrderStatusHistory.java  — Epic 3, US-3.5: one row per Order lifecycle transition
│                                    (fromStatus/toStatus/optional reason); fromStatus is null only
│                                    for the very first row; DTE_CREATION doubles as the
│                                    "occurred at" timestamp, so there's no separate column for it
├── enums/
│   ├── OutboxEventStatus.java     — PENDING, PROCESSING, PROCESSED, FAILED
│   ├── OutboxAggregateType.java   — PRODUCT (widen only when a later epic adds an aggregate root)
│   └── OrderStatus.java           — Epic 3's full 8-value state machine (PENDING,
│                                      PAYMENT_PROCESSING, CONFIRMED, EXPIRED, FAILED, CANCELLED,
│                                      SHIPPED, DELIVERED), added in one pass since the whole state
│                                      machine is fully specified by that epic's own user stories —
│                                      unlike OutboxAggregateType's incremental, one-per-epic growth
├── exception/
│   └── EcommerceErrorCode.java  — PRODUCT_CATEGORY_*/PRODUCT_*/PRODUCT_VARIANT_*/PRODUCT_IMAGE_*/
│                                    CART_*/CHECKOUT_* codes, implements common's ErrorCode interface
├── repository/
│   ├── ProductCategoryRepository.java / ProductRepository.java / ProductVariantRepository.java
│   │   (plus, since Epic 3, reserve(variantId, qty)/release(variantId, qty)/
│   │   confirmSale(variantId, qty)/restock(variantId, qty) — atomic conditional @Modifying
│   │   UPDATEs, same claim-style shape as OutboxEventRepository.claim, so two checkouts racing the
│   │   same variant can't both oversell it) / ProductImageRepository.java /
│   │   OutboxEventRepository.java (findIdsByStatus + an atomic claim(id, from, to) conditional
│   │   UPDATE) / ProductSearchViewRepository.java
│   │   (findByProductId/deleteByProductId + a native search() query — tsvector+trgm keyword
│   │   match, category/price-range/inStock filters, all via the "(:param IS NULL OR ...)" idiom
│   │   for one static query covering every optional-filter combination) / OrderRepository.java
│   │   (plain JpaRepository plus, since Epic 3 Phase 3, findIdsByStatusAndDteCreationBefore — the
│   │   reservation-expiry job's poll query; still no findByOwnerUuid/"list my orders" query,
│   │   planned for Phase 5 alongside the shopper-facing order API)
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
├── orderstatus/                 — Epic 3 Phase 3's GoF State pattern, same self-contained-mechanism
│   │                                shape as outbox/ above
│   ├── OrderStatusHandler.java     — Strategy interface: status() + expire/cancel/ship/deliver,
│   │                                   each defaulting to ORDER_INVALID_STATUS_TRANSITION so a
│   │                                   concrete handler only overrides what's valid from its status
│   ├── OrderStatusTransitions.java — static helpers (releaseReservations/restockSoldLines/
│   │                                   transitionTo) shared by the handlers below; not a shared
│   │                                   abstract base — PaymentProcessingOrderStatusHandler needs no
│   │                                   ProductVariantRepository at all
│   ├── PendingOrderStatusHandler.java / PaymentProcessingOrderStatusHandler.java /
│   │   ConfirmedOrderStatusHandler.java / ShippedOrderStatusHandler.java — one per non-terminal
│   │   OrderStatus; no handler class exists for EXPIRED/FAILED/CANCELLED/DELIVERED (no outgoing
│   │   transitions at all — see OrderStatusHandlerRegistry's own Javadoc for why that's by design)
│   ├── OrderStatusHandlerRegistry.java — Map<OrderStatus, OrderStatusHandler> built from every
│   │                                       handler bean (same shape as OutboxEventDispatcher);
│   │                                       a status with no registered handler falls back to one
│   │                                       with no overrides, rejecting every action identically
│   ├── OrderReservationExpiryJob.java / OrderReservationExpiryProcessor.java — US-3.2's
│   │   poller/single-item-processor split (same shape as OutboxRelay/OutboxEventProcessor);
│   │   app.ecommerce.order.reservation-timeout (default PT15M) / .expiry-check.poll-interval
│   │   (default PT1M)
│   ├── PaymentHandoffService.java — US-3.3's two independent @Transactional steps:
│   │   startPaymentProcessing(orderId, callerUuid) commits PENDING -> PAYMENT_PROCESSING before
│   │   any gateway call; resolvePayment(orderId, outcome) applies the verdict afterward in a
│   │   second transaction — a crash between the two must leave the order durably
│   │   PAYMENT_PROCESSING, not silently roll back and risk a double charge on retry
│   └── OrderReconciliationJob.java — US-3.4, @Scheduled: polls
│       OrderRepository.findIdsByStatusAndPaymentProcessingStartedAtBefore for orders stuck in
│       PAYMENT_PROCESSING past app.ecommerce.order.reconciliation.grace-period (default PT2M),
│       calls payment.PaymentGatewayPort.checkStatus for the ground truth (never assumes an
│       outcome), applies it via PaymentHandoffService.resolvePayment; one poison order's
│       exception is caught/logged so it doesn't block the rest of the batch
├── payment/                     — Epic 4's eventual home, seeded now since Epic 3 Phase 4 needs a
│   │                                seam to call
│   ├── PaymentGatewayPort.java     — GoF Adapter (Structural): charge(idempotencyKey, amount) /
│   │                                    checkStatus(idempotencyKey), both -> PaymentOutcome
│   ├── PaymentOutcome.java         — SUCCEEDED / DECLINED / PENDING
│   └── NoOpPaymentGatewayPort.java — the only implementation today; always returns SUCCEEDED
│                                        instantly. Delete outright once Epic 4 adds a real adapter
├── security/                    — this app's own filter chain, independent of gateway's; pure
│   │                                OAuth2 resource server (Keycloak-backed, same as every other
│   │                                deployable — the old JJWT-based JwtVerifier/
│   │                                JwtAuthenticationFilter shown in earlier revisions of this doc
│   │                                are gone, see docs/CHANGELOG.md's Keycloak migration entries)
│   ├── SecurityConfig.java          — /api/v1/public/** permitAll, /api/v1/admin/** hasRole(ADMIN),
│   │                                    stateless, .oauth2ResourceServer(...) verifying bearer
│   │                                    tokens against Keycloak's JWKS — never issues tokens itself
│   │   (no local KeycloakRealmRoleConverter.java/KeycloakJwtAuthenticationConverter.java
│   │    anymore — both moved to infra.security as shared beans, see infra section above;
│   │    picked up via this module's existing @ComponentScan reaching infra. The shared
│   │    KeycloakJwtAuthenticationConverter builds the CustomOAuth2User principal directly
│   │    from the verified JWT's claims — sub stands in for userUuid — persists nothing.) This
│                                        module's own entities have no foreign key onto a user, so
│                                        the only real need was resolving the caller's identity/role,
│                                        fully answerable from the token alone (see this module's
│                                        own CLAUDE.md for the "Option C" reasoning)
├── config/web/WebMvcConfig.java  — registers infra's shared CurrentUserIdArgumentResolver — new
│                                    for Epic 2's cart (this module's first @CurrentUserId
│                                    consumer; Epic 1 had no entity with an owner column). No local
│                                    resolver class of this module's own — a local copy existed
│                                    briefly before moving to infra.security, see infra section above
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
├── service/{Cart,CartLine,CartService}.java — Epic 2's cart (US-2.1–2.4): Cart/CartLine are plain
│   │   domain records, not entities (no table backs a cart — it lives entirely in Redis)
│   ├── impl/CartServiceImpl.java — one Redis hash per cart (cart:{userUuid}, variantId->quantity
│   │   via StringRedisTemplate); addItem increments (HINCRBY, not read-then-write); setQuantity's
│   │   0 branch removes the line and skips availability validation; 30-day TTL refreshed on every
│   │   mutation, never a read (US-2.4's deliberate abandoned-cart cleanup)
│   └── {CheckoutCommands,CheckoutPreview,CheckoutResult,CheckoutService}.java — Epic 2's checkout
│       half (US-2.5–2.7), a two-step preview+confirm flow on top of CartService; impl/
│       CheckoutServiceImpl.java re-validates fresh on confirm (never trusts a client-cached
│       preview), shares one requireCheckoutableCart guard (empty cart / all-lines-unavailable)
│       between preview and confirm, and clears the Redis cart only after orderRepository.save
│       succeeds; flatShippingFee externalized via app.ecommerce.checkout.flat-shipping-fee.
│       Epic 3 Phase 2 (US-3.1): confirm now calls ProductVariantRepository.reserve per line
│       before building the Order, in the same transaction — insufficient stock throws
│       ORDER_INSUFFICIENT_STOCK and rolls back the whole request; confirm also appends the
│       order's first OrderStatusHistory row (fromStatus null, toStatus PENDING)
├── service/OrderService.java / impl/OrderServiceImpl.java — Epic 3 Phases 3–4 (US-3.6–3.8, 3.3):
│   │   thin cancel(orderId, callerUuid)/ship(orderId)/deliver(orderId) wrappers around
│   │   orderstatus.OrderStatusHandlerRegistry (find-or-404, dispatch, save); cancel hides
│   │   ownership the same way ProductService.getActiveBySlug hides a deactivated slug. Also
│   │   initiatePayment(orderId, callerUuid) — orchestrates orderstatus.PaymentHandoffService's two
│   │   transactional steps around a payment.PaymentGatewayPort.charge call; deliberately not
│   │   itself @Transactional (a class-level annotation would let a crash mid-gateway-call roll
│   │   back the PAYMENT_PROCESSING marker OrderReconciliationJob depends on). No REST layer yet —
│   │   Phase 5's job
├── service/seed/                — starter sample catalog (developer-swag theme), gated by
│   │                                app.seed.enabled
│   ├── ProductCategorySeeder.java  — extends infra's CsvSeeder<ProductCategory>; idempotency key
│   │                                    is name itself (not a decoupled seedId like content-service's
│   │                                    seeders — a fixed-sample-dataset simplification)
│   ├── ProductSeeder.java          — implements Seeder directly (joins products.csv +
│   │                                    product_variants.csv by name); routes through
│   │                                    ProductService.create/deactivate, not a bare repository
│   │                                    save, so PRODUCT_CHANGED fires and ProductSearchView gets
│   │                                    populated
│   ├── ProductImageSeeder.java     — extends CsvSeeder<Void>; generates placeholder JPEGs
│   │                                    (PlaceholderImageGenerator, java.awt/ImageIO) and uploads
│   │                                    them through ProductService.uploadImage via a new
│   │                                    InMemoryMultipartFile (byte-array-backed MultipartFile —
│   │                                    Spring's MockMultipartFile is spring-test-scoped only)
│   └── EcommerceDataSeedingRunner.java — ApplicationRunner, @ConditionalOnProperty
│                                           ("app.seed.enabled"), explicit categories→products→images
│                                           order, same shape as content-service's/social-service's
│                                           own runners
├── mapper/                      — MapStruct: ProductCategoryMapper / ProductMapper (an abstract
│                                    class, not a plain interface — injects infra's StorageService
│                                    to resolve each ProductImage.storageKey into a presigned url
│                                    via @AfterMapping, same pattern as identity-service's
│                                    UserMapper; also maps ProductVariant→ProductVariantResponse
│                                    and ProductImage→ProductImageResponse for ProductResponse's
│                                    nested lists) / ProductSearchViewMapper (also an abstract class
│                                    now, resolving primaryImageStorageKey into a presigned
│                                    primaryImageUrl the same way — without it the storefront grid
│                                    would have nothing but an unusable private MinIO key to render)
│                                    / CartMapper (hand-written, not MapStruct — computes
│                                    subtotal/itemCount across only the available lines, real
│                                    aggregation logic MapStruct's per-field model doesn't fit;
│                                    toLineResponse(CartLine) extracted as its own public method
│                                    so CheckoutMapper can reuse it) / CheckoutMapper (also
│                                    hand-written, injects CartMapper for every cart-line shape it
│                                    surfaces — a preview's lines, and any lines dropped at confirm)
├── api/                         — REST layer
│   ├── ProductCategoryApi.java / ProductApi.java — admin CRUD (/api/v1/admin/**), incl.
│   │                                 POST/DELETE .../variants/{id} and
│   │                                 POST/DELETE/PATCH .../images/{id} for independent
│   │                                 variant/image mutation (US-1.6), plus
│   │                                 POST .../images/upload (multipart file+sortOrder) — the real
│   │                                 upload path, backed by infra's StorageService; addImage
│   │                                 still exists for a caller that already knows a storage key
│   ├── ProductSearchApi.java    — public browse/search + GET /{slug} detail
│   │                                 (/api/v1/public/products, US-1.1/1.2/1.3/1.4)
│   ├── PublicProductCategoryApi.java — GET /api/v1/public/product-categories, a read-only
│   │                                 counterpart to ProductCategoryApi's admin-gated list — added
│   │                                 for the storefront's category filter rail, which a
│   │                                 non-admin/logged-out shopper can't reach the admin endpoint
│   │                                 for; delegates to the same ProductCategoryService.list(null)
│   ├── CartApi.java              — /api/v1/cart (US-2.1–2.4), authenticated-only — no new
│   │                                 SecurityConfig rule needed (falls under the existing default
│   │                                 anyRequest().authenticated()); every mutating method returns
│   │                                 the freshly-resolved CartResponse, not just 200
│   ├── CheckoutApi.java          — /api/v1/checkout (US-2.5–2.7), authenticated-only, same rule as
│   │                                 CartApi; GET /preview (review) + POST /confirm (creates the
│   │                                 order, 201)
│   └── impl/                    — ProductCategoryController / ProductController (admin-gated
│                                    automatically via this module's own security/SecurityConfig
│                                    /api/v1/admin/** rule) / ProductSearchController /
│                                    PublicProductCategoryController (both public via that same
│                                    config's /api/v1/public/** rule) / CartController /
│                                    CheckoutController
└── dto/                         — ProductCategoryResponse/CreateProductCategoryRequest/
                                     UpdateProductCategoryRequest, ProductResponse/
                                     CreateProductRequest/UpdateProductRequest,
                                     ProductVariantRequest/ProductVariantResponse,
                                     ProductImageRequest/ProductImageResponse,
                                     CartResponse/CartLineResponse/AddCartItemRequest/
                                     UpdateCartItemRequest,
                                     AddressRequest/AddressResponse/OrderLineResponse/
                                     CheckoutPreviewResponse/CheckoutConfirmResponse,
                                     UpdateProductImageSortOrderRequest,
                                     ProductSearchResponse
```

Liquibase migrations: `ecommerce-service/.../database/sql/2026/0.0.2/202608040001__0.0.2__DKP-0023__add_ecommerce_catalog_tables.sql`
under this module's **own** changelog tree now (not `gateway`'s) — `PRODUCT_CATEGORY`, `PRODUCT`,
`PRODUCT_IMAGE`, `PRODUCT_VARIANT`, `PRODUCT_SEARCH_VIEW`, `OUTBOX_EVENT` (with its
`STATUS`/`ATTEMPT_COUNT`/`LAST_ERROR` columns), plus `CREATE SCHEMA ecommerce`, the `pg_trgm`
extension, and GIN indexes for `tsvector`/trigram/JSONB containment search on
`PRODUCT_SEARCH_VIEW`; `202608240001__0.0.2__DKP-0034__add_ecommerce_order_tables.sql` —
`CUSTOMER_ORDER` (named to dodge the `ORDER` reserved keyword, with the embedded `Address`
columns inline) and `ORDER_LINE`; and (Epic 3 Phase 1)
`202608300001__0.0.2__DKP-0035__add_order_reservation_and_status_history.sql` — widens
`CUSTOMER_ORDER.STATUS`'s `CHECK` to the full 8-value state machine, adds `IDEMPOTENCY_KEY`
(partial-unique-indexed)/`PAYMENT_PROCESSING_STARTED_AT`/`CANCEL_REQUESTED`, a
`(STATUS, DTE_CREATION)` index for the Phase-2 expiry/reconciliation jobs' poll queries, and the
new `ORDER_STATUS_HISTORY` table. All three applied via the consolidated `services-liquibase` job
in `docker-compose.apps.yml` — see the Liquibase note above.

Not yet built: `ProductCategory` delete, and the rest of Epic 3 (the shopper/admin `OrderApi` REST
surface, Phase 5 — Phases 1–4 above already cover the data model, US-3.1's reservation,
US-3.2/3.6/3.7/3.8's state-machine logic, and US-3.3/3.4's payment handoff/reconciliation behind a
stub gateway, all reachable today only via direct service calls, not HTTP) plus a real payment
gateway adapter and the rest of Epic 4, plus Epic 5. The `gateway`-side HTTP proxy, variant/image
add/remove/reorder endpoints, and Epic 2's checkout half (all listed as not yet built in earlier
revisions of this section) are all done now — see above.

**Test suite:** `src/test/java/.../service/impl/` (`ProductCategoryServiceImplTest`,
`ProductServiceImplTest`, `ProductSearchServiceImplTest`, `ProductChangedOutboxEventHandlerTest`,
`CheckoutServiceImplTest`) and `src/test/java/.../outbox/` (`OutboxEventDispatcherTest`,
`OutboxEventProcessorTest`) — plain JUnit 5/Mockito/AssertJ unit tests, 126 passing (Epic 3 Phase 2
added stock-reservation cases to `CheckoutServiceImplTest`; Phase 3 added a full `orderstatus/`
package test suite — one class per handler, plus registry/expiry-job/`OrderServiceImpl` tests;
Phase 4 added `payment/`'s own tests plus `PaymentHandoffServiceTest`/`OrderReconciliationJobTest`
and more cases on the Phase 3 handler/registry/`OrderServiceImpl` tests), no Docker
needed. Plus
`src/test/java/.../repository/ProductSearchViewRepositoryIT` — a real Postgres Testcontainers
integration test for US-1.3/1.4's native `tsvector`/`pg_trgm`/JSONB-containment search matching,
which a mock can't verify. See `ecommerce-service/CLAUDE.md`'s test-suite note for the full
reasoning and the Docker caveat.

Compiles against `common` + `infra` as ordinary library dependencies; has **no** Maven dependency
on any other feature module, and none of them (including `gateway`) depend on it either — see
root `CLAUDE.md`'s dependency-order section for why Epic 5's originally-planned dependency on
`ai-service` needs rethinking now that this module is standalone. See `ecommerce-service/CLAUDE.md`
for the rules this module follows.

---

## gateway

Renamed from `api` once its last REST controller (`UserApi.search`/`getPublicProfile`) moved to
`social-service` (see `docs/CHANGELOG.md`) — this module holds **zero REST controllers of its own**
today. Still the Spring Boot entry point and, nominally, "the one module allowed to depend on more
than one feature module" — but that's now **doubly** moot: `ai-service`'s own extraction removed the
last embedded feature module, so `gateway` depends on nothing but `common`+`infra` and has **zero**
embedded feature modules left to depend on more than one of. This closes out the
microservices-extraction-plan project — see root `CLAUDE.md`'s Long-term direction section.

**Now the single entry point for external clients** — proxies HTTP traffic to all six standalone
services via Spring Cloud Gateway Server MVC (`routing/GatewayRoutesConfig`, below) — the first
thing this module has gained since losing its last embedded feature module, not another extraction.

```
gateway/src/main/java/com/ttg/devknowledgeplatform/
├── routing/
│   ├── GatewayRoutesConfig.java       — one RouterFunction<ServerResponse> @Bean per backend
│   │                                    service (GatewayRouterFunctions.route +
│   │                                    GatewayRequestPredicates.path + HandlerFunctions.http),
│   │                                    23 routes total across the six services. Paths forwarded
│   │                                    unchanged — no prefix stripping. Three top-level prefixes
│   │                                    (/api/v1/users, /api/v1/public, /api/v1/admin) are shared
│   │                                    by more than one service and need resource-specific
│   │                                    patterns one segment deeper to disambiguate — see the
│   │                                    class's own Javadoc for the full table and root
│   │                                    CLAUDE.md's Architecture → Routing section for a summary.
│   │                                    content-service's /internal/content-items/** is
│   │                                    deliberately not routed (service-to-service only).
│   │                                    ai-service's /api/v1/chat/stream isn't routed through
│   │                                    this class either, but it IS still proxied by this app —
│   │                                    see ChatStreamProxyController below.
│   │                                    /api/v1/chat/sessions/** (plain REST) is routed normally.
│   ├── ChatStreamProxyController.java — hand-rolled proxy for just /api/v1/chat/stream, bypassing
│   │                                    Gateway Server MVC's RouterFunction DSL, which has real,
│   │                                    documented problems proxying Server-Sent Events. Plain
│   │                                    @RestController using the JDK's own HttpClient (not
│   │                                    Spring's RestClient — needs the upstream status code
│   │                                    available before committing to stream the body, which
│   │                                    RestClient.exchange()'s callback-scoped API doesn't fit).
│   │                                    Relays bytes with an explicit flush() after every read.
│   │                                    Forwards Authorization/Content-Type/Accept verbatim, plus
│   │                                    traceparent (optional) — the one route needing an explicit
│   │                                    forwarding parameter for it, since the other 22 get it
│   │                                    automatically via Gateway MVC's default header-forwarding.
│   ├── StreamingProxyAsyncConfig.java — async-dispatch wiring StreamingResponseBody needs (a
│   │                                    60s timeout matching ai-service's own
│   │                                    SseStreamTemplate.SSE_TIMEOUT_MS, plus a dedicated
│   │                                    bounded ThreadPoolTaskExecutor, streamRelayExecutor,
│   │                                    rather than Spring MVC's default unbounded per-request
│   │                                    thread creator). Named for the mechanism, not today's one
│   │                                    caller — configureAsyncSupport's setTaskExecutor sets the
│   │                                    one default for the whole app, so a future second
│   │                                    streaming-proxy endpoint would use this same bean
│   │                                    regardless of its name
│   └── GatewayServicesProperties.java — app.services.* — each service's base URL
│                                         (localhost:<port> by default, overridden in
│                                         application-docker.yml to Compose DNS names)
├── (no config/ package anymore — JacksonConfig, the last class left under it, moved to infra's own
│    config/json/JacksonConfig.java once every standalone service's @SpringBootApplication was
│    widened with an explicit @ComponentScan reaching infra's sibling package (see the note below
│    and each service's own entry-point Javadoc) — living in infra now means all seven apps in this
│    reactor pick up the shared ObjectMapper customization automatically, not just gateway's
│    (nonexistent) one. Chat-specific rate limiting (ChatRateLimiter/RateLimitProperties/
│    ChatRateLimitInterceptor), the sseStreamExecutor pool (config/thread/), the SSE/@CurrentUserId
│    MVC wiring (config/web/), and the Bucket4j Redis connection (config/cache/RedisCacheConfig) had
│    already moved out earlier — to ai-service, the only module left with an SSE endpoint, a chat
│    rate limit to enforce, or a REST controller to resolve @CurrentUserId for.
│    RedisCacheConfig's other two beans (cacheManager, baseRedisCacheConfiguration — the
│    @EnableCaching machinery) did NOT move anywhere: a reactor-wide grep for
│    @Cacheable/@CacheEvict/@CachePut during ai-service's extraction found zero real usages anywhere
│    in the codebase, so they were deleted outright as dead code instead, along with infra's
│    CacheTtlProperties/CacheNames (now fully orphaned) — see ai-service/CLAUDE.md's Rules section
│    for the full finding.
│
│    Fixed alongside the JacksonConfig move: none of the six standalone services' entry-point
│    classes actually widened their component scan to reach infra's sibling package before this —
│    Spring Boot's default @ComponentScan is rooted at the @SpringBootApplication class's own
│    package and does not recurse into a sibling like infra. This meant several already-shipping
│    beans (identity-service's/social-service's StorageService injection,
│    ecommerce-service's/content-service's SlugService injection, social-service's/ai-service's
│    AsyncEventThreadPoolConfig-backed @EventHandler dispatch) would have failed to resolve at
│    Spring context startup. social-service was additionally missing @EnableAsync entirely, which
│    doesn't error — Spring silently runs @Async methods synchronously instead — so its two
│    FriendRequest*EventListener classes had been dispatching on the calling thread instead of the
│    dedicated asyncEventExecutor pool the whole time. All six entry-point classes now carry an
│    explicit @ComponentScan(basePackages = {"<own-package>", "com.ttg.devknowledgeplatform.infra"})
│    (see each service's own section below and its class-level Javadoc), and social-service gained
│    @EnableAsync.
├── (no database/ package anymore — this app's whole Liquibase changelog tree was deleted outright
│    once its last live table, product.USER, was dropped and every other table in it was already
│    orphaned history from the six embedded-module extractions; see the Database section below.
│    liquibase-core was removed from pom.xml alongside it, and the keycloak-schema bootstrap that
│    tree used to own (DKP-0024) moved to the repo root's docker/postgres/init.sql instead)
├── (no event/ package — every listener moved into the module that owns the event it reacts to:
│    FriendRequestSentEventListener/FriendRequestAcceptedEventListener → social-service, before
│    that module's own later extraction; ContentPublishedEventListener also moved to ai-service on
│    the same reasoning, then was later deleted outright as dead code during content-service's own
│    extraction — its event never had a publisher wired up; none ever had a gateway-specific
│    dependency)
├── security/                         — OAuth2-resource-server verification (edge concerns); Keycloak
│   │                                    is the identity provider — this app never issues tokens,
│   │                                    only verifies them. No WebSocket/STOMP transport here
│   │                                    anymore — see below. **No caller-identity persistence of
│   │                                    any kind anymore either** — see the converter's own entry
│   ├── SecurityConfig.java           — .oauth2ResourceServer(jwt -> ...) verifying bearer tokens
│   │                                    against Keycloak's JWKS (spring.security.oauth2.
│   │                                    resourceserver.jwt.issuer-uri); no .oauth2Login() anymore
│   │   (no local KeycloakRealmRoleConverter.java/KeycloakJwtAuthenticationConverter.java of this
│   │    app's own left at all — both moved to infra.security as shared beans, see infra section
│   │    above, picked up via this app's existing @ComponentScan reaching infra. infra's shared
│   │    KeycloakJwtAuthenticationConverter builds CustomOAuth2User straight from the token's
│   │    claims — jwt.getSubject() standing in for userUuid — no database read or write at all.
│   │    Used to JIT-provision/refresh this app's own local product.USER row directly via
│   │    common's UserRepository even earlier still; retired to claims-only once it became clear
│   │    nothing in this app ever read that row back (zero REST controllers, and the authorization
│   │    decision was always driven by the token's own claims, never the row) — and by that point
│   │    was already byte-for-byte identical to ecommerce-service's/task-service's/
│   │    content-service's/ai-service's own copies, which is exactly why all five now share the one
│   │    infra bean instead of five separate files. identity-service (and social-service) are the
│   │    two deployables that still keep a local converter, since both persist a real row —
│   │    identity.USER/social.PROFILE respectively — see those modules' own sections.)
│   └── CorsConfig.java
│       (no local JsonAuthenticationEntryPoint.java anymore either — moved to infra.security as a
│       shared bean, see infra section above; picked up via this app's existing @ComponentScan
│       reaching infra. **No `CurrentUserResolver` here anymore** — it read the JIT-provisioned row back to
│       resolve an Integer PK, but its only two callers had already moved to other modules in
│       earlier extractions, leaving zero real callers — confirmed via grep before deleting.
│       **No `WebSocketConfig`/`StompAuthChannelInterceptor` here either** — both relocated to
│       `social-service`'s own `security/` package once that module became a standalone app owning
│       the whole WebSocket/STOMP transport for chat; this app never had a second use for it)
```

**No `service/` package here anymore.** `service/seed/{UserSeeder,DataSeedingRunner}` — this app's
last seeder — was deleted outright once `product.USER` itself was dropped: `UserSeeder` had nothing
left to seed, `DataSeedingRunner` had nothing left to orchestrate. `gateway/src/main/resources/data/`
is gone in its entirety alongside them — `data/csv/users.csv` (the seeder's own input) and
`data/init-admin-user.sql` (a standalone script that inserted directly into `product.USER`, never
wired into `DataSeedingRunner` or any other mechanism) both had nowhere left to write to.
`categories.csv`/`tags.csv`/`question-answers/*.md` had already moved to `content-service`'s own
resources in an earlier extraction.

Everything that used to live flat here — every feature's REST controllers, DTOs, and MapStruct
mappers, including the one composed `UserApi.search`/`getPublicProfile` endpoint (moved to
`social-service`, which used to reach into `identity-service` for the base lookup before both
modules became standalone — it resolves the base lookup itself now, against its own entity) —
moved into the owning module, each now standalone (`content-service`, `ai-service`, `social-service`,
`identity-service`); see those modules' sections and `docs/CHANGELOG.md`'s `[Unreleased]` entries for
the full move and its rationale. Chat-specific rate limiting
(`ChatRateLimiter`/`RateLimitProperties`/`ChatRateLimitInterceptor`), the `sseStreamExecutor` pool,
the Bucket4j Redis connection, and the SSE/`@CurrentUserId` MVC wiring all moved out too, to
`ai-service` — the last module with any of those runtime concerns left to serve — and the
`asyncEventExecutor` thread pool moved to `infra` in an earlier extraction; see those modules'
sections. What's left here is transport/security edge infra (`SecurityConfig`, JWT filter — **no
STOMP wiring, no SSE/thread-pool config, no cache config, and no local user persistence of any kind
anymore**, see above) plus the new `routing/` package proxying all six services' traffic (see the
tree above). **This app has no Liquibase migrations left at all**, not even
frozen ones — the whole changelog tree was deleted outright, see the Database section below.

**This module currently has no test suite of its own** — its only tests
(`ws/AbstractStompIntegrationTest.java`/`ws/DmMessagingStompIntegrationTest.java`, plus
`application-test.yml`/`keycloak/test-realm-export.json`) were relocated to `social-service` in
full once that module got its own `@SpringBootApplication` to boot them against (see that module's
own section) — they existed here only because `WebSocketConfig`/`StompAuthChannelInterceptor` and
`social-service`'s `DmMessagingController` used to get wired together exclusively in this app.

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

- Schema: `product` — now holds **zero live tables**. Every table it ever held (`USER` included,
  its last holdout) was dropped outright by `DKP-0033` once nothing in this reactor mapped any of
  them anymore; see below. The schema container itself is left in place, just empty.
- Sequences: one per table (`TABLE_NAME_SEQ`)
- Audit columns on every entity via `AbstractEntity`
- pgvector HNSW index on `ai.content_embedding.embedding` (cosine distance, `vector_cosine_ops`) —
  lives in `ai-service`'s own `ai` schema now, not `product`
- `SYS_PARAM` — general-purpose key-value table; stores corpus centroid vectors and future AI/config
  parameters — also relocated to `ai-service`'s own `ai` schema (see below)
- **Historical, dropped:** `CATEGORY` / `TAG` / `CONTENT_ITEM` / `CONTENT_ITEM_TAG` /
  `QUESTION_ANSWER` / `ARTICLE` (`DKP-0001`-`0004`/`0009`/`0013`/`0014`/`0018`) used to back
  `content-service`'s entities of the same names while that module was still embedded. Every
  changeset that created them stays untouched (frozen, already-run history), but the tables
  themselves no longer physically exist — `DKP-0033` dropped them alongside every other `product`
  table (see below). `content-service` migrates a fresh snapshot of this same shape into its own
  `content` schema instead (`DKP-0031` in that module's own tree), with `AUTHOR_UUID` (a plain
  Keycloak-subject-id column) replacing `AUTHOR_ID` entirely — see that module's `CLAUDE.md`.
- **Historical, dropped:** `FRIEND_REQUEST` / `FRIENDSHIP` / `USER_BLOCK` (`DKP-0015`) and
  `MESSAGE_GROUP` / `GROUP_MEMBER` / `CHANNEL` / `DM_THREAD` / `DM_MESSAGE` / `CHANNEL_MESSAGE`
  (`DKP-0019`) used to back `social-service`'s entities of the same names while that module was
  still embedded. Both changesets stay untouched (frozen, already-run history, per this repo's
  never-edit-an-executed-changeset convention), but the tables themselves no longer physically
  exist — `DKP-0033` dropped them too. `social-service` migrates a fresh snapshot of this same
  shape into its own `social` schema instead (`DKP-0029`/`DKP-0030` in that module's own tree),
  with every FK repointed at `social.PROFILE` (that module's own lean entity) instead of
  `product.USER`, and no `SEED_ID` on the pair tables for either — see that module's `CLAUDE.md` for
  why (a pair's identity has no editable-field equivalent to `NAME`/`EMAIL` that could invalidate a
  pair-based idempotency check).
- **Historical, dropped:** `CONTENT_EMBEDDING` / `CHAT_SESSION` / `CHAT_MESSAGE` / `SYS_PARAM` /
  `PIPELINE_METRICS` (`DKP-0005`/`DKP-0006`/`DKP-0007`/`DKP-0008`/`DKP-0010`/`DKP-0011`/`DKP-0012`)
  used to back `ai-service`'s entities of the same names while that module was still embedded — the
  last module to leave this list, closing out `gateway`'s embedded-feature-module history entirely.
  All changesets stay untouched (frozen, already-run history), but the tables no longer physically
  exist — `DKP-0033` dropped them too. `ai-service` migrates a fresh snapshot of this same shape
  into its own `ai` schema instead (`DKP-0032` in that module's own tree), with
  `CHAT_SESSION.USER_UUID`/`PIPELINE_METRICS.USER_UUID` (plain Keycloak-subject-id columns)
  replacing `USER_ID` entirely — see `ai-service/CLAUDE.md`.
- **Historical, dropped:** `PROJECT` / `TASK` (`DKP-0020`-`0022`) used to back `task-service`'s
  entities of the same names while that module was still embedded — same treatment, `DKP-0033`
  dropped them too. `task-service` migrates its own fresh snapshot instead (`DKP-0028` in that
  module's own tree), with a plain `OWNER_UUID` column replacing the old `OWNER_ID` FK entirely.
- **`USER` (`DKP-0002`, plus `DKP-0016`'s `SEED_ID`/`DKP-0017`'s index/`DKP-0025`'s
  `KEYCLOAK_SUBJECT_ID`) — dropped too, the last of the 23 tables `product` ever held.** Unlike the
  four bullets above, this wasn't an artifact of an earlier extraction — `gateway` mapped this one
  directly, right up until `DKP-0033`. `identity-service`'s own `User` entity (moved there from
  `common`, see that module's section and root `CLAUDE.md`'s Security section) is the sole entity in
  this whole reactor mapped to a `USER` table today, into `identity.USER` — a fresh, independent
  table, not a migration of `product.USER`'s actual rows.
- **`DKP-0033`** — the changeset that dropped all 23 `product` tables above in a single statement
  (naming every table, `CASCADE` for the FKs between them — safe here specifically because every
  possible dependent is inside the same drop list, confirmed via a reactor-wide grep for
  `REFERENCES product.`). Each table's sequence dropped automatically alongside it (`ALTER SEQUENCE
  ... OWNED BY`, declared back in each table's own creating changeset) — no separate `DROP
  SEQUENCE` needed. The `product` schema container itself is left in place, just empty.
- **`gateway` has no Liquibase changelog tree left at all** — every changeset described above
  (including `DKP-0033` itself) was deleted outright rather than left as frozen history, once
  nothing in this reactor needed any of it anymore. `liquibase-core` was removed from
  `gateway/pom.xml`, `spring.liquibase.*` removed from its `application*.yml` files, and the
  `<build><resources>` block that used to expose changelog XML/SQL on the classpath removed too.
  The one genuinely-still-needed thing this tree used to bootstrap — the `keycloak` Postgres schema
  itself (`DKP-0024`; Keycloak's own internal migration assumes its schema already exists and fails
  otherwise) — moved to the repo root's `docker/postgres/init.sql` instead (`CREATE SCHEMA IF NOT
  EXISTS keycloak`), which already runs automatically before Postgres reports healthy, which every
  service's `depends_on` already waits on. `content-service`'s, `social-service`'s,
  `task-service`'s, `identity-service`'s, `ecommerce-service`'s, and `ai-service`'s tables were
  never here — each standalone service migrates its own schema from its own changelog tree, see
  above.
  - Naming: `YYYY/VERSION/YYYYMMDDHHMI__VERSION__TICKET__description.sql`

---

## Deployment

Seven independently-runnable Spring Boot processes exist today — `gateway` (now a bare
JWT-verification shell with zero embedded feature modules, zero local user persistence, and zero
Liquibase migrations of its own),
`ecommerce-service`, `identity-service`, `task-service`, `social-service`, `content-service`, and
`ai-service` (the latter six standalone microservices-study extractions, `ai-service` the sixth and
final) — each with its own `Dockerfile` (multi-stage: `maven:3.9.9-eclipse-temurin-21` build stage
running `mvn -pl <module> -am package` against the full reactor, `eclipse-temurin:21-jre-jammy`
runtime stage). All seven Dockerfiles use the **repo root** as their build context, since the Maven
reactor build needs sibling-module sources (`docker build -f gateway/Dockerfile .`, not
`docker build gateway/`). `gateway`'s `Dockerfile` only `COPY`s the sources of modules it actually
depends on (`common`/`infra`) plus every module's `pom.xml` (needed for Maven to parse the reactor's
full `<modules>` list even for modules it won't build) — it does not copy `identity-service`,
`ecommerce-service`, `task-service`, `social-service`, `content-service`, or `ai-service` sources.

`docker-compose.apps.yml` (repo root) brings up all seven app containers plus
**one consolidated `services-liquibase` container** that runs all six standalone services'
migrations sequentially in a single `sh -c` loop (`ecommerce-service` → `identity-service` →
`task-service` → `social-service` → `content-service` → `ai-service`, each its own
`liquibase ... update` invocation against its own mounted changelog directory) — replaces what used
to be six separate inline services (`ecommerce-liquibase`, `identity-liquibase`, `task-liquibase`,
`social-liquibase`, `content-liquibase`, `ai-liquibase`), one container per service. Each of the six
app containers' `depends_on` now points at this one `services-liquibase` service instead of its own
dedicated runner. Sequential execution is a deliberate improvement over the old shape, not just a
consolidation: all six changelogs share one Postgres instance and one Liquibase
`DATABASECHANGELOG`/`DATABASECHANGELOGLOCK` tracking pair regardless of container count, so six
containers starting in parallel (as they could before, since none of them depended on each other)
could contend for that lock; one container running them one at a time never contends with itself.
Only `task-service-liquibase.yml` and `social-service-liquibase.yml` — the two standalone
single-service compose files that predate this consolidation, for running just one service's
migration against Postgres's host-exposed port (`host.docker.internal`) outside the combined
apps-compose flow — are unaffected; `ecommerce-service`, `identity-service`, `content-service`, and
`ai-service` never got an equivalent standalone file of their own, so the consolidated job is their
only migration path (a doc/reality mismatch caught and fixed 2026-08-16 — several `CLAUDE.md`/
`pom.xml`/`application.yml` comments across this reactor used to claim all six had one). `gateway` has no migration runner of its own at all
(neither the old `dkp-liquibase` nor a slot in the new consolidated loop), since it has no Liquibase
changelog left to run; the one thing that runner used to do beyond `gateway`'s own concerns
(bootstrapping the `keycloak` schema, `DKP-0024`) now happens in `docker/postgres/init.sql`
instead. It has no infra containers of its own — it
must be run combined with `docker-compose.infra.yml` in one command, since that's
what puts every container in a single Compose project/network so service-name DNS
(`postgres`/`redis`/`minio`/`keycloak`) resolves:

```bash
docker compose -f docker-compose.infra.yml \
                -f docker-compose.apps.yml \
                up -d --build
```

`ecommerce-service`, `identity-service`, `task-service`, `social-service`, `content-service`, and
`ai-service` share the same `dev-premier` Postgres database as `gateway`, each in its own schema
(`ecommerce`/`identity`/`task`/`social`/`content`/`ai` vs. `gateway`'s `product`, which now holds no
live tables at all — see the Database section above) — per-service-per-schema, not
per-service-per-database (see root `CLAUDE.md`'s Database Conventions and the
`project-microservices-extraction-plan` memory for why). `task-service` and `social-service` are
the only two with their own standalone single-service `*-liquibase.yml` compose file at the repo
root for migrating outside the combined apps-compose flow (`task-service-liquibase.yml`,
`social-service-liquibase.yml`) — see the Migration Runners note above for why the other four don't
have one despite older docs claiming otherwise. `social-service` additionally needs a real MinIO connection at runtime
(`app.storage.*`, for avatar/attachment presigned URLs via `infra`'s `StorageService`) — the only one
of the six standalone extractions with that requirement, since none of the others ever mapped
avatar/attachment data. `ai-service`'s own container is the one with a genuine inter-service runtime
dependency on another standalone service today: `CONTENT_SERVICE_BASE_URL`/`INTERNAL_API_KEY` point
its `ContentServiceClient` at `content-service`'s Compose service name
(`http://content-service:8085`) — this used to be `gateway`'s container that carried these env vars,
back when `ai-service` was still embedded there; they moved to `ai-service`'s own container block
alongside `OPENAI_API_KEY`, `REDIS_HOST`/`REDIS_PASSWORD`, and an `ANTHROPIC_API_KEY` (optional) once
it went standalone. `gateway`'s own container was simplified in the same step — it no longer needs
any of those env vars or a `redis`/`content-service` `depends_on`, just
`SPRING_PROFILES_ACTIVE`/`KEYCLOAK_ISSUER_URI`/`FRONTEND_URL`, depending only on `minio`'s
healthcheck now that its own `dkp-liquibase` dependency is gone too.

`common`, `infra`, and the root `pom.xml` needed no changes for any of this — they already function
as this repo's shared-foundation modules (proven by `ecommerce-service` already depending on
`common`+`infra` as ordinary library jars, with zero Maven dependency on `gateway`, before
`identity-service`, `task-service`, `social-service`, `content-service`, and `ai-service` followed
the same pattern). `infra` did lose two classes as a direct result of `ai-service`'s extraction,
though: `CacheTtlProperties`/`CacheNames` were deleted as dead code once `gateway`'s
`RedisCacheConfig` (their only consumer) was found to have zero real `@Cacheable` usages anywhere in
the reactor and was deleted rather than moved — see `ai-service/CLAUDE.md`'s Rules section for the
full finding. Kafka/RabbitMQ messaging and a `kubernetes/` directory are not part of this yet — see
`docs/CHANGELOG.md`'s `[Unreleased]` entry for current scope.
