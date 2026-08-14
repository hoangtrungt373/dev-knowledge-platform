# CLAUDE.md — social-service

Module-local guidance for `social-service`. Read alongside the root `CLAUDE.md`.

## What lives here

Friend graph (search visibility, requests, friendships, blocking) plus chat (groups/channels and
1:1 DMs). Package root: `com.ttg.devknowledgeplatform.social.*`. Chat was added here as new
packages rather than a separate module, per the original plan — see `docs/CHANGELOG.md`'s "Friend
Management (Phase 1 — friend graph)" and "Chat MVP (Phase 2 ...)" entries for the full reasoning
and data model.

**Now a standalone Spring Boot application, not part of the monolith** — the fourth module pulled
out, following the `ecommerce-service`/`identity-service`/`task-service` precedent (see the
`project-microservices-extraction-plan` memory for the full extraction history). Concretely: its
own `SocialServiceApplication` entry point, its own `social` Postgres schema (separate from the
monolith's `product` schema), its own JWT verification *and* its own full WebSocket/STOMP transport
(`security/WebSocketConfig`/`StompAuthChannelInterceptor` — this is the only one of the five
standalone extractions so far that had to bring real-time transport with it, not just REST), its
own port (`8084`), and its own Liquibase changelog + `social-service-liquibase.yml` docker-compose
file. `gateway` no longer has a Maven dependency on this module — it never called into this
module's Java classes in-process to begin with (`FriendApi`/`GroupApi`/`DmApi`/`UserApi` were
already this module's own REST layer, just riding on `gateway`'s Spring context via the Maven
dependency), but `gateway` **did** own the WebSocket/STOMP wiring that assembled this module's
`GroupMessagingController`/`DmMessagingController` into a running broker — that wiring relocated
here in full (see `security/` below), and `gateway`'s own copies were deleted outright since chat
was their only use case. **`gateway`-side HTTP proxying to this service is not built yet** — until
it is, this service is only reachable directly on its own port, same limitation
`ecommerce-service`/`identity-service`/`task-service` have.

**No coupling to `common.entity.User`** — this was a deliberate, explicit decision (see the
`project-microservices-extraction-plan` memory), not the default extraction pattern. Every
relationship in this module's entity graph points at `entity.SocialProfile` instead, this module's
own **lean, module-local** projection of a Keycloak identity — never the shared `common.entity.User`
class `gateway`/`identity-service` reuse. Rationale: this module genuinely needs a real local
identity row (unlike `ecommerce-service`/`task-service`'s claims-based "Option C" shape) because it
searches/lists/joins across *other* users' profile data (friend search, group membership, DM
threads) — but reusing `common.entity.User` would mean carrying that shared entity's full
auth-lifecycle column set (`password`, OAuth `provider`, `role`, `emailVerified`, `enabled`) into a
module that has no auth-lifecycle concern at all and never reads most of them. `SocialProfile`
carries only the columns this module's own code actually reads/writes (verified by grepping real
usages, not guessed): `profileUuid`/`keycloakSubjectId`/`email` for JIT-provisioning lookup,
`username`/`firstName`/`lastName`/`profilePicture`/`status` for search and display, `seedId` for
the demo-data seeders. `role`/`provider`/`emailVerified` were also trimmed out of the public
`UserInfoResponse` API shape (previously auto-mapped by MapStruct from `User`'s matching field
names) — grepped for real consumers first (`gui`'s `@auth` feature reads those three fields only off
the *logged-in user's own* account dashboard via `identity-service`'s unrelated endpoint, never off
a friend's public profile) and confirmed dropping them is safe.

- `SocialServiceApplication` — `@SpringBootApplication` + `@ComponentScan(basePackages =
  {"...social", "...infra"})` + `@EnableAsync`. Both were added/fixed in the same pass, once an
  audit found no standalone service in this reactor actually reached `infra`'s sibling package by
  default: this module injects `infra.service.StorageService` (`FriendMapper`/`MessagingMapper`
  avatar/attachment presigned URLs) and its two `FriendRequest*EventListener`s extend `infra`'s
  `AsyncEventHandler`, needing `infra`'s own `AsyncEventThreadPoolConfig` bean. `@EnableAsync` was
  missing entirely before this fix — a quieter bug than a missing bean, since Spring doesn't error
  on a missing `@EnableAsync`, it just silently runs `@Async` methods synchronously — so both
  listeners had been dispatching on the calling thread instead of the dedicated
  `asyncEventExecutor` pool the whole time. See `infra/CLAUDE.md`'s `JacksonConfig` note for the
  full reactor-wide finding.
- `api/` (+ `api/impl/`) — REST controllers and their interfaces, moved here from `gateway` (named
  `api` at the time): `FriendApi`/`FriendController`, `GroupApi`/`GroupController`,
  `DmApi`/`DmController`, plus the STOMP counterparts `GroupMessagingApi`/`GroupMessagingController`
  and `DmMessagingApi`/`DmMessagingController` (`@MessageMapping`, not `@RequestMapping` — live push
  for group/DM chat). The interface/impl split (`FooApi` carries the HTTP/messaging annotations
  and Javadoc, `FooController implements FooApi` carries none) is unchanged from when this lived in
  `gateway`. `GroupMessagingController`/`DmMessagingController` are now wired into a real broker by
  this module's own `security/WebSocketConfig` (relocated from `gateway` — see below), not a
  cross-module reference.
  - `api/UserApi` + `api/impl/UserController` — a **second** `UserApi`, distinct from
    `identity-service`'s own (same `/api/v1/users` base mapping, different two methods:
    `getPublicProfile`/`search` here vs. `updateProfile`/`uploadAvatar` there, on a separate
    standalone app now). Resolves the base profile lookup directly via this module's own
    `SocialProfileRepository` and this module's own `FriendMapper.toUserInfo` (a local
    `UserInfoResponse` DTO, duplicated from `identity-service`'s equivalent, minus the
    `role`/`provider`/`emailVerified` fields — see above) before applying this module's own
    `FriendService` enrichment.
- `mapper/` — `FriendMapper`, `MessagingMapper` (MapStruct, moved from `gateway`). Both are abstract
  classes (not plain interfaces) needing an injected `infra`'s `StorageService` for presigned-URL
  resolution — MapStruct interfaces can't hold instance fields. `MessagingMapper` `uses =
  FriendMapper.class` for `SocialProfile` → `UserSummaryResponse` so avatar resolution isn't
  duplicated.
- `dto/friend/`, `dto/messaging/` — REST response/request records, moved from `gateway`
  (`UserSummaryResponse`, `FriendRequestResponse`, `FriendSummaryResponse`,
  `UserSearchResultResponse`; `SendMessageRequest`, `ChangeRoleRequest`, `CreateGroupRequest`,
  `CreateChannelRequest`, `GroupResponse`, `ChannelResponse`, `GroupMemberResponse`,
  `ChannelMessageResponse`, `DmThreadResponse`, `DmMessageResponse`, `MessageAttachmentRequest`,
  `MessageAttachmentResponse`, `WsErrorResponse`). Plain records, no behavior — same shape as when
  they lived in `gateway`.
- `entity/SocialProfile` — this module's own lean projection of a Keycloak identity; see "No
  coupling to `common.entity.User`" above for the full reasoning and exact column list. Table
  `social.PROFILE`.
- `entity/` (the rest) — `FriendRequest`, `Friendship`, `UserBlock`, `Group` (maps to table
  `MESSAGE_GROUP` — `GROUP` is a reserved word in PostgreSQL), `GroupMember`, `Channel`, `DmThread`,
  `DmMessage`, `ChannelMessage` (not in `common` — domain-specific, following the precedent of
  `ai-service`'s own `ContentEmbedding`/`PipelineMetrics`); every `@ManyToOne` that used to point at
  `common.entity.User` now points at `SocialProfile` instead. `DmMessage`/`ChannelMessage` are flat,
  single-level entities (both extend `AbstractEntity` directly, duplicating the same ~6 columns)
  rather than sharing a `@MappedSuperclass` — Lombok's `@Builder`/`@AllArgsConstructor` don't
  include inherited fields (only `@SuperBuilder` does, unused elsewhere in this codebase), which
  would have silently dropped `sender`/`content`/etc. from the builder. `Group` has no `ownerId`
  column — the owner is whichever `GroupMember` row holds `role = OWNER`, one source of truth
  instead of a duplicated ref.
- `enums/ProfileStatus` — `SocialProfile`'s presence enum (`ONLINE`/`OFFLINE`/`AWAY`/`BUSY`).
  Deliberately this module's own enum, not a reuse of `common.enums.UserStatus` — that type is a
  field on `common.entity.User`, which this module no longer maps at all.
- `enums/` (the rest) — `FriendRequestStatus`, `RelationshipStatus` (computed, not persisted),
  `GroupMemberRole` (`OWNER`/`ADMIN`/`MEMBER`), `MessageType` (`TEXT`/`IMAGE`/`FILE` — tags the
  primary content for rendering only; text and an attachment may coexist on one message row).
- `repository/SocialProfileRepository` — this module's own repository over `SocialProfile`; the
  replacement for `common`'s shared `UserRepository`, which this module no longer uses at all.
- `repository/` (the rest) — `FriendRequestRepository`, `FriendshipRepository`,
  `UserBlockRepository`, `GroupRepository`, `GroupMemberRepository`, `ChannelRepository`,
  `DmThreadRepository`, `DmMessageRepository`, `ChannelMessageRepository`,
  `repository/spec/UserSpecification` (now specified over `SocialProfile`, not `common.entity.User`).
- `security/` — this app's own filter chain *and* WebSocket/STOMP transport, independent of
  `gateway`'s. `SecurityConfig` requires authentication on every endpoint except
  `/api/v1/users/public/**` (public profile lookup — `UserApi.getPublicProfile` degrades gracefully
  for an anonymous viewer, same rule `gateway` carried before extraction), the `/ws/**` handshake,
  and `/actuator/**`. **No local `KeycloakRealmRoleConverter` anymore** — this module uses
  `infra.security.KeycloakRealmRoleConverter` (the shared bean, see `infra/CLAUDE.md`) for
  `realm_access.roles` → `ROLE_*` mapping instead, picked up via this module's existing
  `@ComponentScan` reaching `infra`. `security/KeycloakJwtAuthenticationConverter` itself stays
  local (one of only two converters in the reactor — `identity-service`'s is the other — that do
  real divergent work beyond claims-only mapping, so neither was a candidate for `infra`'s shared
  claims-only converter): it JIT-provisions/refreshes this app's own
  local `SocialProfile` row directly via `SocialProfileRepository` — inlined here (like `gateway`'s/
  `ecommerce-service`'s/`task-service`'s), not delegated (unlike `identity-service`'s), and writes
  only the fields `SocialProfile` actually has (no password placeholder, no role, no
  emailVerified/enabled flags — see "No coupling to `common.entity.User`" above).
  `CurrentUserResolver` resolves the authenticated `CustomOAuth2User` principal's UUID to this app's
  own local `SocialProfile` numeric PK, duplicated from `gateway`'s class of the same name.
  `WebSocketConfig`/`StompAuthChannelInterceptor` — relocated here from `gateway` in full (see the
  module-level note above); `StompAuthChannelInterceptor` reuses this module's own
  `KeycloakJwtAuthenticationConverter` on STOMP `CONNECT` and this module's own
  `GroupService.isChannelMember` to authorize channel-topic `SUBSCRIBE`s — same mechanics as
  before, just no longer crossing a module boundary to reach either.
- `config/web/` — `WebMvcConfig` (registers `CurrentUserIdArgumentResolver`),
  `CurrentUserIdArgumentResolver` (REST) and `CurrentUserIdMessageArgumentResolver` (STOMP) — both
  duplicated from `gateway`'s classes of the same name, both resolving against this module's own
  `SocialProfileRepository` now.
- `event/` — `FriendRequestSentEvent`, `FriendRequestAcceptedEvent` (records) and their listeners,
  `FriendRequestSentEventListener`/`FriendRequestAcceptedEventListener` (moved in from `gateway` —
  currently just log; the seam for a future in-app/email notification). Event + listener
  co-located in one package, same convention `ai-service` uses for its own `PipelineCompletedEvent`.
  **No dependency on `identity-service`'s `EmailService`** for a future notification — that class
  was deleted outright during `identity-service`'s own Keycloak migration (superseded by Keycloak's
  built-in email flows) and `identity-service` is a standalone service now anyway; a real
  notification feature here would need `infra`-level mail support or a network call, not an
  in-process dependency.
- `service/FriendService` (+ `impl/`) — single service, no sub-service split.
- `service/GroupService` and `service/DmService` (+ `impl/`) — two separate services, not one,
  despite both being "chat": they gate access differently (open-add + role checks vs.
  friend-required) and share no entities, so merging them would mix two unrelated authorization
  models in one class. `DmServiceImpl` depends on `FriendService` as a collaborator (reuses
  `getRelationshipStatus`) rather than querying `Friendship`/`UserBlock` directly — one
  implementation of the canonicalization + mutual-invisibility logic, not two. Blocking has **no
  effect inside a shared group channel** — it only gates DMs; if you're adding a feature that
  touches both, don't assume a block hides a member's channel messages, it doesn't in this MVP.
  `GroupServiceImpl.requireManagementRole` is a Java 21 exhaustive switch (no `default`) over
  `GroupMemberRole`, same technique `FriendServiceImpl.requirePending` uses for
  `FriendRequestStatus` — follow that pattern for new role/status-shaped checks rather than
  introducing a State-pattern class hierarchy.
- `service/MessageAttachmentInput` — record shared by `DmService.sendMessage`/
  `GroupService.postMessage`; `objectKey` is a MinIO object key (already uploaded by the caller),
  not a URL — resolved to a presigned URL by this module's own `mapper/MessagingMapper` at read
  time (formerly `gateway`'s mapper, before the move documented above), same pattern as avatars.
- `exception/SocialErrorCode` — `FRIEND_*`/`DM_*`/`GROUP_*`/`CHANNEL_*` codes, implements
  `common`'s `ErrorCode` interface, same pattern as `content-service`'s `ContentErrorCode` (moved
  out of `common`'s `CommonErrorCode`, which used to hold every module's non-content codes).
  Renamed from `FriendErrorCode` once this module grew beyond just the friend graph — one enum per
  module (not per sub-domain) is the established convention, not one enum per entity group.
- `service/seed/SocialProfileSeeder` — this module's own copy of the demo-user seeder, reading this
  module's own `data/csv/users.csv` (duplicated from `gateway`'s file, same `id`/seed-key values so
  `FriendGraphSeeder`/`UserBlockSeeder`/`DmThreadSeeder` keep working unchanged). Necessary because
  seed accounts have no real Keycloak identity to JIT-provision from, and this module is a separate
  deployable now with no access to `gateway`'s resources/classpath at runtime — each service's
  seeded row for "the same" demo person is an independent copy, exactly like a real Keycloak login
  would independently JIT-provision one in each service that needs it. Simpler than `gateway`'s
  `UserSeeder`: no `password`/`provider`/`emailVerified`/`enabled` to fill, since `SocialProfile`
  has none of those columns.
- `service/seed/{FriendGraphSeeder,UserBlockSeeder,DmThreadSeeder}` — sample social-graph data for
  the Friend Management GUI, unchanged in shape from before extraction (see
  `docs/SEED_DATA_AUTHORING_GUIDE.md`'s "User / friend graph sample data" section) except that they
  now resolve `SocialProfile` (via this module's own `SocialProfileRepository.findBySeedId`) instead
  of `common.entity.User`. `UserBlockSeeder` extends `infra`'s `CsvSeeder<T>`; `FriendGraphSeeder`
  implements its own `seed()` since an `ACCEPTED` row persists two entities (`FriendRequest` +
  `Friendship`), which doesn't fit the one-entity-per-row template. `DmThreadSeeder` — sample data
  for the `@messaging` DM chat GUI: one random lorem-ipsum conversation (5–15 messages, backdated
  across the last ~2 weeks) per existing `Friendship` row, not a CSV. No seeder yet for
  `Group`/`Channel` — that GUI hasn't landed (Phase 2 of `@messaging`).
- `service/seed/DataSeedingRunner` — this module's own seeding orchestrator (`ApplicationRunner`,
  gated by `app.seed.enabled`), running `SocialProfileSeeder` → `FriendGraphSeeder` →
  `UserBlockSeeder` → `DmThreadSeeder` in order. Not a continuation of `gateway`'s runner of the same
  name — `gateway`'s own `DataSeedingRunner` used to inject the latter three seeders directly (all
  three already lived in this module even before extraction), which stopped compiling once
  `gateway` dropped its Maven dependency here; those seeders (and the CSV files they read) moved
  fully into this module's own orchestration instead.

## Rules specific to this module

- **Depends only on `common` + `infra`. Never add a dependency on `gateway`, `ai-service`,
  `content-service`, `identity-service`, `ecommerce-service`, or `task-service`** — and none of them
  may depend on this module either anymore, now that it's a standalone deployable with no shared
  Spring context. Cross-service communication would need a real network call (through `gateway`,
  once proxying exists), never a `pom.xml` entry. This module used to have a one-directional
  dependency on `identity-service` (`api/UserApi`'s `getPublicProfile`/`search`, for the base
  profile lookup before this module's own `FriendService` enrichment) — removed once
  `identity-service` was extracted into a standalone service; that lookup goes through this
  module's own `SocialProfileRepository` now, not even `common`'s `UserRepository`.
- **No coupling to `common.entity.User` — `SocialProfile` is this module's own entity, not a shared
  one.** See the module-level note above for the full reasoning (real search/list/join need, but no
  auth-lifecycle concern to justify the shared entity's full column set) and exactly which columns
  were kept vs. dropped. Don't reintroduce a `common.entity.User`/`UserRepository` reference
  anywhere in this module without confirming that's actually wanted — it would reintroduce the
  exact coupling this extraction deliberately removed.
- **Every service here returns entities, never a mapper/DTO type** — `FriendService`'s own Javadoc
  documents this: "Returns entities rather than REST DTOs — `FriendMapper` does the
  entity-to-response mapping." `GroupService`/`DmService` follow the same rule; any new method does
  too.
- **REST/STOMP controllers, DTOs, and mappers now DO live here** (`api/`, `dto/friend/`,
  `dto/messaging/`, `mapper/`) — moved from `gateway` (named `api` at the time) so this module's
  HTTP surface ships alongside the entities/services it wraps, same as `content-service`'s
  equivalent packages. This reverses the module's original convention (they used to live in the
  entry-point module); don't assume older docs or commit history describing "that's `api`'s job"
  still apply.
- Request-status transitions use exhaustive Java 21 `switch` (no `default`) over
  `FriendRequestStatus`, so a future status addition is a compile error at every transition check
  site — keep following that pattern rather than adding a `default` branch.
- Mutual invisibility is a real security/privacy property, not a nice-to-have: any new lookup that
  can return a user who has blocked the viewer must throw the same `USER_NOT_FOUND` used for a
  genuinely nonexistent user, never a distinguishable "blocked" error.
- **Liquibase migrations for this module's tables live in this module's own changelog tree now**
  (`database/sql/social-service.xml` + `2026/0.0.2/*.sql`), applied via the standalone
  `social-service-liquibase.yml` docker-compose file at the repo root — the opposite of every
  embedded feature module (which still migrate via `gateway`'s changelog tree per root `CLAUDE.md`'s
  Database Conventions). `DKP-0029` adds this module's own `social.PROFILE` table; `DKP-0030` adds a
  fresh snapshot of the friend graph + chat tables (final shape of `gateway`'s old `DKP-0015`/
  `DKP-0019`), with every FK repointed at `social.PROFILE` instead of `product.USER`. Don't move
  future migrations back under `gateway`'s tree; this module owns its own schema lifecycle now.
  `FriendRequest`/`Friendship`/`UserBlock` do NOT have their own `SEED_ID` (unlike `SocialProfile`'s)
  — a pair's identity has no editable-field equivalent to `NAME`/`EMAIL` that could invalidate a
  pair-based idempotency check, so `FriendRequestRepository.existsBetween`/
  `UserBlockRepository.existsEitherDirection` are guard enough on their own. Chat's tables have no
  `SEED_ID` either, for the same reason.
- **This module's own test suite lives here now** (`src/test/java/.../ws/`) —
  `AbstractStompIntegrationTest` (Testcontainers Postgres/MinIO/**Keycloak** — the last via
  `com.github.dasniko:testcontainers-keycloak` importing a dedicated, minimal test realm,
  `src/test/resources/keycloak/test-realm-export.json`, separate from the dev realm export — boots
  the full context; **no Redis container**, unlike `gateway`'s original version of this suite, since
  this module has no Redis-backed bean of its own) + `DmMessagingStompIntegrationTest`. Relocated
  from `gateway` (which used to be the only place `WebSocketConfig`/`StompAuthChannelInterceptor`
  and `DmMessagingController` were assembled together in a running app) now that this module has its
  own `@SpringBootApplication` to boot them against. `persistUser()` provisions a matching Keycloak
  user per call via the admin client (`KeycloakContainer.getKeycloakAdminClient()`), linked by
  `keycloakSubjectId`, so tests exercise `KeycloakJwtAuthenticationConverter`'s realistic find-path;
  `accessTokenFor`/`refreshTokenFor` fetch real tokens via a Resource Owner Password grant against a
  test-only client (the real `gui` client disables that grant — never reuse this client shape
  outside tests).
- **Any new public STOMP topic needs a `SUBSCRIBE`-time authorization check** in
  `StompAuthChannelInterceptor`, the same way `/topic/channels/{id}` has one via this module's own
  `GroupService.isChannelMember`. The simple in-memory broker has no per-destination ACL — anyone
  who knows the topic string can subscribe unless this interceptor rejects it. Prefer
  `convertAndSendToUser`'s private per-user queue over a public topic when a destination is
  inherently 1:1 (e.g. DMs) — it needs no such check at all.
- **STOMP handling cannot rely on Open-Session-In-View.** REST controllers can safely map a
  service's returned entity's lazy associations after the service call returns, because Spring
  Boot's default `spring.jpa.open-in-view=true` (never overridden here) keeps the Hibernate session
  open for the whole HTTP request via a servlet filter. STOMP message handling never goes through
  that filter, so the same pattern in a `@MessageMapping` method risks a
  `LazyInitializationException`. Caught while building `DmMessagingController`: don't navigate
  `message.getDmThread().getUser1()/getUser2()` there (existing-thread case makes those genuine lazy
  proxies) — resolve what you need via a fresh repository call or from data already fully loaded
  within the same request instead. If this constraint is ever lifted (e.g. `open-in-view` gets
  explicitly disabled), every REST list endpoint here (`listMessages`, `listMyThreads`, etc.) would
  need the same treatment — they currently only work because OSIV papers over it.
