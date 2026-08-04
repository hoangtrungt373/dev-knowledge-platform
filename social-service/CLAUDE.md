# CLAUDE.md — social-service

Module-local guidance for `social-service`. Read alongside the root `CLAUDE.md`.

## What lives here

Friend graph (search visibility, requests, friendships, blocking) plus chat (groups/channels and
1:1 DMs). Package root: `com.ttg.devknowledgeplatform.social.*`. Chat was added here as new
packages rather than a separate module, per the original plan — see `docs/CHANGELOG.md`'s "Friend
Management (Phase 1 — friend graph)" and "Chat MVP (Phase 2 ...)" entries for the full reasoning
and data model.

- `api/` (+ `api/impl/`) — REST controllers and their interfaces, moved here from `gateway` (named
  `api` at the time): `FriendApi`/`FriendController`, `GroupApi`/`GroupController`,
  `DmApi`/`DmController`, plus the STOMP counterparts `GroupMessagingApi`/`GroupMessagingController`
  and `DmMessagingApi`/`DmMessagingController` (`@MessageMapping`, not `@RequestMapping` — live push
  for group/DM chat). The interface/impl split (`FooApi` carries the HTTP/messaging annotations
  and Javadoc, `FooController implements FooApi` carries none) is unchanged from when this lived in
  `gateway`. `GroupMessagingController`/`DmMessagingController` reference `gateway`'s
  `StompAuthChannelInterceptor` only in a Javadoc `{@code}` mention (that class stays in `gateway`,
  wired up by `gateway`'s `WebSocketConfig`) — not a real import, so the module-dependency rule below
  still holds. Needs `spring-boot-starter-websocket` on the classpath (see `pom.xml`) purely for
  the STOMP annotations/`SimpMessagingTemplate` the messaging controllers use.
  - `api/UserApi` + `api/impl/UserController` — a **second** `UserApi`, distinct from
    `identity-service`'s own (same `/api/v1/users` base mapping, different two methods:
    `getPublicProfile`/`search` here vs. `updateProfile`/`uploadAvatar` there). This is the one
    place in the whole reactor where a controller reaches across into a sibling module
    (`identity-service`'s `UserService`/`UserMapper`, for the base profile lookup) before applying
    this module's own `FriendService` enrichment — see the module-dependency rule below for why
    that direction (not the reverse) is correct.
- `mapper/` — `FriendMapper`, `MessagingMapper` (MapStruct, moved from `gateway`). Both are abstract
  classes (not plain interfaces) needing an injected `infra`'s `StorageService` for presigned-URL
  resolution — MapStruct interfaces can't hold instance fields. `MessagingMapper` `uses =
  FriendMapper.class` for `User` → `UserSummaryResponse` so avatar resolution isn't duplicated.
- `dto/friend/`, `dto/messaging/` — REST response/request records, moved from `gateway`
  (`UserSummaryResponse`, `FriendRequestResponse`, `FriendSummaryResponse`,
  `UserSearchResultResponse`; `SendMessageRequest`, `ChangeRoleRequest`, `CreateGroupRequest`,
  `CreateChannelRequest`, `GroupResponse`, `ChannelResponse`, `GroupMemberResponse`,
  `ChannelMessageResponse`, `DmThreadResponse`, `DmMessageResponse`, `MessageAttachmentRequest`,
  `MessageAttachmentResponse`, `WsErrorResponse`). Plain records, no behavior — same shape as when
  they lived in `gateway`.
- `entity/` — `FriendRequest`, `Friendship`, `UserBlock`, `Group` (maps to table `MESSAGE_GROUP` —
  `GROUP` is a reserved word in PostgreSQL), `GroupMember`, `Channel`, `DmThread`, `DmMessage`,
  `ChannelMessage` (not in `common` — domain-specific, following the precedent of `ai-service`'s
  own `ContentEmbedding`/`PipelineMetrics`). `DmMessage`/`ChannelMessage` are flat, single-level
  entities (both extend `AbstractEntity` directly, duplicating the same ~6 columns) rather than
  sharing a `@MappedSuperclass` — Lombok's `@Builder`/`@AllArgsConstructor` don't include inherited
  fields (only `@SuperBuilder` does, unused elsewhere in this codebase), which would have silently
  dropped `sender`/`content`/etc. from the builder. `Group` has no `ownerId` column — the owner is
  whichever `GroupMember` row holds `role = OWNER`, one source of truth instead of a duplicated ref.
- `enums/` — `FriendRequestStatus`, `RelationshipStatus` (computed, not persisted),
  `GroupMemberRole` (`OWNER`/`ADMIN`/`MEMBER`), `MessageType` (`TEXT`/`IMAGE`/`FILE` — tags the
  primary content for rendering only; text and an attachment may coexist on one message row).
- `repository/` — `FriendRequestRepository`, `FriendshipRepository`, `UserBlockRepository`,
  `GroupRepository`, `GroupMemberRepository`, `ChannelRepository`, `DmThreadRepository`,
  `DmMessageRepository`, `ChannelMessageRepository`, `repository/spec/UserSpecification`.
  Read/write access to `User` itself goes through `common`'s own `UserRepository` directly — there
  used to be a module-local `SocialUserRepository` wrapping it (needed a distinct name from
  `gateway`'s old copy to avoid a Spring bean-name collision), retired once `UserRepository` moved
  to `common` and became the one repository every module can share.
- `event/` — `FriendRequestSentEvent`, `FriendRequestAcceptedEvent` (records) and their listeners,
  `FriendRequestSentEventListener`/`FriendRequestAcceptedEventListener` (moved in from `gateway` —
  currently just log; the seam for a future in-app/email notification). Event + listener
  co-located in one package, same convention `ai-service` uses for its own `PipelineCompletedEvent`.
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
- `service/seed/` — sample social-graph data for the Friend Management GUI (`FriendGraphSeeder`,
  `UserBlockSeeder`), not corpus/content-style seed data — see
  `docs/SEED_DATA_AUTHORING_GUIDE.md`'s "User / friend graph sample data" section. Requires
  `gateway`'s `UserSeeder` to have already run (references users by `User.seedId`). `UserBlockSeeder` extends
  `infra`'s `CsvSeeder<T>`; `FriendGraphSeeder` implements its own `seed()` since an `ACCEPTED` row
  persists two entities (`FriendRequest` + `Friendship`), which doesn't fit the one-entity-per-row
  template.
  - `DmThreadSeeder` — sample data for the `@messaging` DM chat GUI: one random lorem-ipsum
    conversation (5–15 messages, backdated across the last ~2 weeks) per existing `Friendship`
    row, not a CSV — pairs come straight from `FriendshipRepository.findAll()`, since a thread can
    only exist between already-friended users anyway. Idempotent per pair (skips if a `DmThread`
    already exists for it), same shape as `FriendGraphSeeder`'s guard. Requires `FriendGraphSeeder`
    to have already run. Backdating a message's `dteCreation` needs `DmMessageRepository`'s
    `backdateCreatedAt` (a JPQL bulk update) — see that method's Javadoc for why a normal
    entity-managed save/update can't do it (`AbstractEntity`'s `@PrePersist` always resets it to
    "now", and the column is `updatable = false`).
  - No seeder yet for `Group`/`Channel` — that GUI hasn't landed (Phase 2 of `@messaging`).

## Rules specific to this module

- **Depends on `common` + `infra` + `identity-service`. Never add a dependency on `gateway`,
  `ai-service`, or `content-service`.** `identity-service` is the one exception to the
  parallel-sibling rule: it depends only on `common`+`infra` itself (same as this module), so this
  module reaching into it is a one-directional sibling dependency, not a cycle — the same shape as
  `ai-service` depending on `content-service`. It exists specifically for `api/UserApi`'s
  `getPublicProfile`/`search` (need `identity-service`'s `UserService`/`UserMapper` for the base
  profile lookup before this module's own `FriendService` enrichment). Don't reach for
  `identity-service` for anything else without a similarly concrete reason — this isn't a general
  license to pull in arbitrary sibling functionality.
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
- **Notification delivery (email/in-app) DOES now live here too, if you're implementing it** —
  `FriendRequestSentEvent`/`FriendRequestAcceptedEvent` are defined here, published via
  `ApplicationEventPublisher`, and listened for here (`event/FriendRequestSentEventListener`/
  `FriendRequestAcceptedEventListener`, currently just logging). This module already depends on
  `identity-service` (for `api/UserApi`), so reaching its `EmailService` to actually send a
  notification needs no new dependency when that gets implemented.
- Request-status transitions use exhaustive Java 21 `switch` (no `default`) over
  `FriendRequestStatus`, so a future status addition is a compile error at every transition check
  site — keep following that pattern rather than adding a `default` branch.
- Mutual invisibility is a real security/privacy property, not a nice-to-have: any new lookup that
  can return a user who has blocked the viewer must throw the same `USER_NOT_FOUND` used for a
  genuinely nonexistent user, never a distinguishable "blocked" error.
- Liquibase migrations for this module's tables (`DKP-0015` for the friend graph, `DKP-0019` for
  chat) still live under `gateway`'s changelog tree, same as `content-service` — don't create a
  per-module changelog folder. `FriendRequest`/`Friendship`/`UserBlock` do NOT have their own
  `SEED_ID` (unlike `User`'s, added in `DKP-0016`) — a pair's identity has no editable-field
  equivalent to `NAME`/`EMAIL` that could invalidate a pair-based idempotency check, so
  `FriendRequestRepository.existsBetween`/`UserBlockRepository.existsEitherDirection` are guard
  enough on their own. Chat's tables have no `SEED_ID` either, for the same reason.
