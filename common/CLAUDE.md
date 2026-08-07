# CLAUDE.md — common

Module-local guidance for `common`. Read alongside the root `CLAUDE.md` (Operational Rules,
Documentation Protocol, etc. apply here too — this file only covers what's specific to `common`).

## What lives here

- `entity/` — truly cross-module entities only: `AbstractEntity` (audit columns), `User`.
  Feature-specific entities (content, friend graph, chat sessions, etc.) live in their own module —
  see `docs/PROJECT_STRUCTURE.md`. `ChatSession`/`ChatMessage` and `SysParam` used to live here too;
  a full audit of every class in this module (grepping real imports, not Javadoc mentions) found
  zero consumers outside `ai-service` for either, so both moved there along with their enums
  (`ChatMessageRole`, `ParamKey`) and `SysParamRepository`/`SysParamService`(`Impl`) — see
  `ai-service/CLAUDE.md` and `docs/CHANGELOG.md`.
- `enums/` — `UserProvider`, `UserRole`, `UserStatus` — all three are field types on `User`, so they
  stay here even though only `identity-service`'s OAuth2 provisioning logic actively branches on
  `UserProvider`: moving it there would force `common` (the base of the dependency graph) to depend
  on `identity-service`. `ContentType`/`ContentStatus`/`QuestionDifficulty` moved here from
  `content-service` as step 1 of that module's standalone-service extraction (see root `CLAUDE.md`'s
  Long-term direction section) — `ai-service` uses all three on its own public REST contracts
  (`ChatRequest.sourceTypes`, `RagFilter`, `PublicContentApi`) and internal indexing filters, not
  just as `content-service`-internal plumbing, so a plain cross-service Java import would break once
  `content-service` stops being a Maven dependency. `TagStatus` stayed behind in `content-service` —
  confirmed via grep it has no consumer outside that module.
- `exception/` — `ApiException`, `BusinessException`, `ResourceNotFoundException`,
  `GlobalExceptionHandler`, `ErrorCode` (interface), `CommonErrorCode` (implements `ErrorCode`),
  `RateLimitExceededException` — the last one looks single-consumer (`ai-service`'s
  `ChatRateLimiter`/`ChatRateLimitInterceptor` are its only throwers), but `GlobalExceptionHandler`
  has a compile-time `@ExceptionHandler(RateLimitExceededException.class)`, and
  `GlobalExceptionHandler` itself must stay here — so this one stays too, same reasoning as
  `ErrorResponse` below.
- `repository/UserRepository` — the sole repository over `User` for the whole reactor. Also moved
  here from `gateway` (named `api` at the time) specifically so `content-service`/`social-service`
  can reach it without depending on `gateway`; extends `JpaSpecificationExecutor<User>` for
  `social-service`'s dynamic user search. `findBySeedId` supports `UserSeeder`'s (`gateway`)
  idempotency check. `UserService`/`Impl` (registration, OAuth2 provisioning, password hashing) live
  in `identity-service` — see that module's `CLAUDE.md` — that's genuine auth-flow business logic,
  not a repository-level concern.
- `User.seedId` (nullable, DB `SEED_ID`, Liquibase `DKP-0016`) — same idempotent-seeding pattern
  as `content-service`'s entities (`DKP-0013`), for the same reason: `email`/`username` are
  human-editable, so they can't be the idempotency check `UserSeeder` (`gateway`) uses.
- `dto/` — cross-module carrier types: `PagedResponse` (generic pagination envelope, used by
  controllers in every feature module), `CustomOAuth2User` (implements `OAuth2User`; needed here
  rather than `identity-service` because `content-service`'s `ArticleApi`/`QuestionAnswerApi` also
  use it as an `@AuthenticationPrincipal` parameter type, and `content-service` can't depend on
  `identity-service`), `ErrorResponse` (the REST error envelope `GlobalExceptionHandler` builds for
  every module's exceptions — only `gateway`'s `JsonAuthenticationEntryPoint` imports it directly
  today, but it has to stay wherever `GlobalExceptionHandler` is, which must be here).
  `ConversationContext`/`ConversationTurn` used to live here too — moved to `ai-service` (see
  `entity/` above for why).
- `annotation/CurrentUserId` — marker annotation for "the authenticated user's integer PK" controller
  parameters; lives here (not `gateway`) so every feature module's controllers can use it without
  depending on `gateway`. The `HandlerMethodArgumentResolver`s that actually resolve it stay in
  `gateway` (`config/web/CurrentUserIdArgumentResolver`/`CurrentUserIdMessageArgumentResolver`) — a
  transport-edge concern, not something every module needs to reimplement.
- `util/UserUtils` — `getUserName()` (for audit-column string fields) and `isAuthenticated()` only.
  It does **not** have a `getCurrentUser()` method and architecturally can't — it lives here with no
  repository access, so it can't resolve a principal to a full `User` row. For that, see
  `identity-service`'s `UserService.resolveCurrentUser(CustomOAuth2User)` or the `@CurrentUserId`
  parameter annotation.

## Rules specific to this module

- **No Spring application context.** `spring-boot-starter-web`/`-security`/`-oauth2-resource-server`/
  `-oauth2-client` are declared `optional=true` — annotation/type support only (e.g. `HttpStatus` on
  `ErrorCode` implementations, `OAuth2User` on `CustomOAuth2User`), not full autoconfiguration.
  `spring-boot-starter-data-jpa` and `-validation` are the only non-optional Spring dependencies
  (needed for `@Entity`/`@MappedSuperclass`/bean validation on `AbstractEntity`).
- **Every module depends on this one** (it's the base of the dependency graph). Never add a
  dependency from `common` onto any feature module (`content-service`, `ai-service`,
  `social-service`, `identity-service`) or `gateway` — that would be circular.
- **`ErrorCode` is an interface, not a single enum.** `CommonErrorCode` here holds only
  `AUTH_*`/`OAUTH_*`/`USER_*`/`OTP_*`/`VALIDATION_*`/`SERVER_*`/`RESOURCE_*`/`REQUEST_*`/`RATE_*` —
  codes with no single feature-module owner. `AI_*` and `CHAT_*` moved to `ai-service`'s
  `AiErrorCode`/`ChatErrorCode`, `FRIEND_*`/`DM_*`/`GROUP_*`/`CHANNEL_*` to `social-service`'s
  `SocialErrorCode` (renamed from `FriendErrorCode` once that module grew beyond just the friend
  graph) — each mirroring `content-service`'s `ContentErrorCode`. A new error code only belongs here
  if it's not owned by one specific feature module — otherwise add it to (or create) that module's
  own `*ErrorCode` enum implementing this interface; ask before assuming which way a new code should go.
- Before adding any new "shared utility" class here, check whether it actually needs Spring context
  at all (if so, consider `infra` instead) and whether it's genuinely needed by more than one module
  (if it's only needed by one feature module, it probably belongs in that feature module instead).
  This rule is exactly what caught `ChatSession`/`ChatMessage`/`SysParam` and friends drifting here
  without a second real consumer — re-run this check with an actual grep of real imports (not
  Javadoc `{@code}`/`{@link}` mentions, which can imply a cross-module usage that was never real)
  if a class here looks unused elsewhere.
