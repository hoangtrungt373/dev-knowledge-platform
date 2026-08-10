# CLAUDE.md — common

Module-local guidance for `common`. Read alongside the root `CLAUDE.md` (Operational Rules,
Documentation Protocol, etc. apply here too — this file only covers what's specific to `common`).

## What lives here

- `entity/` — truly cross-module entities only: `AbstractEntity` (audit columns) is the sole one left
  today. Feature-specific entities (content, friend graph, chat sessions, etc.) live in their own
  module — see `docs/PROJECT_STRUCTURE.md`. `ChatSession`/`ChatMessage` and `SysParam` used to live
  here too; a full audit of every class in this module (grepping real imports, not Javadoc mentions)
  found zero consumers outside `ai-service` for either, so both moved there along with their enums
  (`ChatMessageRole`, `ParamKey`) and `SysParamRepository`/`SysParamService`(`Impl`) — see
  `ai-service/CLAUDE.md` and `docs/CHANGELOG.md`. `User` (+ its `UserRepository` and
  `UserProvider`/`UserRole`/`UserStatus` enums) followed the same path once `gateway` dropped its own
  local copy of it and `identity-service` became the sole remaining consumer — see
  `identity-service/CLAUDE.md`'s `entity`/`repository`/`enums` sections and `docs/CHANGELOG.md`'s
  `[Unreleased]` entry. **Do not resurrect a `User`-shaped entity here** just because a future module
  needs "the caller's identity" — every module built since has answered that with a plain claims-based
  `ownerUuid`/`authorUuid`/`userUuid` column instead (see root `CLAUDE.md`'s Security section); only
  reach for a real persisted row shared across modules if a genuine second deployable needs to
  JIT-provision and query the *same* row `identity-service` already owns, which would be a network
  call to that service, never a shared entity class again.
- `enums/` — `ContentType`/`ContentStatus`/`QuestionDifficulty` moved here from `content-service` as
  step 1 of that module's standalone-service extraction (see root `CLAUDE.md`'s Long-term direction
  section) — `ai-service` uses all three on its own public REST contracts (`ChatRequest.sourceTypes`,
  `RagFilter`, `PublicContentApi`) and internal indexing filters, not just as
  `content-service`-internal plumbing, so a plain cross-service Java import would break once
  `content-service` stops being a Maven dependency. `TagStatus` stayed behind in `content-service` —
  confirmed via grep it has no consumer outside that module. `UserProvider`/`UserRole`/`UserStatus`
  moved to `identity-service` alongside `User` (see the `entity/` bullet above) — this package holds
  no `User`-related enum anymore.
- `exception/` — `ApiException`, `BusinessException`, `ResourceNotFoundException`,
  `GlobalExceptionHandler`, `ErrorCode` (interface), `CommonErrorCode` (implements `ErrorCode`),
  `RateLimitExceededException` — the last one looks single-consumer (`ai-service`'s
  `ChatRateLimiter`/`ChatRateLimitInterceptor` are its only throwers), but `GlobalExceptionHandler`
  has a compile-time `@ExceptionHandler(RateLimitExceededException.class)`, and
  `GlobalExceptionHandler` itself must stay here — so this one stays too, same reasoning as
  `ErrorResponse` below.
- **No `repository/` package left here at all** — `UserRepository` was the only thing in it,
  and it moved to `identity-service` alongside `User` (see the `entity/` bullet above) once
  `gateway` dropped its own local copy and `identity-service` became the sole consumer.
- `dto/` — cross-module carrier types: `PagedResponse` (generic pagination envelope, used by
  controllers in every feature module), `CustomOAuth2User` (implements `OAuth2User`; needed here
  rather than `identity-service` because `content-service`'s `ArticleApi`/`QuestionAnswerApi` also
  use it as an `@AuthenticationPrincipal` parameter type, and `content-service` can't depend on
  `identity-service`), `ErrorResponse` (the REST error envelope `GlobalExceptionHandler` builds for
  every module's exceptions — only `gateway`'s `JsonAuthenticationEntryPoint` imports it directly
  today, but it has to stay wherever `GlobalExceptionHandler` is, which must be here).
  `ConversationContext`/`ConversationTurn` used to live here too — moved to `ai-service` (see
  `entity/` above for why).
- `annotation/CurrentUserId` — marker annotation for "the authenticated caller" controller
  parameters; lives here so every feature module's controllers can use it without depending on
  `gateway` or each other. **No `HandlerMethodArgumentResolver` lives here or in `gateway`** —
  `gateway` has had zero REST controllers (and so zero `@CurrentUserId` consumers) since well before
  it dropped its own local `User` persistence; each standalone service that actually has a
  `@CurrentUserId`-annotated controller parameter owns its own duplicated resolver instead
  (`identity-service`/`social-service` resolve an `Integer` PK against their own local row;
  `task-service`/`content-service`/`ai-service` resolve a `String` straight off the JWT claims, no
  database lookup — see root `CLAUDE.md`'s Security section for the full per-module breakdown).
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
