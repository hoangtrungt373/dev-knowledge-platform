# CLAUDE.md — task-service

Module-local guidance for `task-service`. Read alongside the root `CLAUDE.md`.

## What lives here

Personal task/project management — the knowledge-platform's task tracker. Package root:
`com.ttg.devknowledgeplatform.task.*`. Built in phases; see `docs/CHANGELOG.md`'s `[Unreleased]`
entries for the full reasoning behind each decision below.

**Now a standalone Spring Boot application, not part of the monolith** — the third module pulled
out, following the `ecommerce-service`/`identity-service` precedent (see the
`project-microservices-extraction-plan` memory for the full extraction history). Concretely: its own
`TaskServiceApplication` entry point, its own `task` Postgres schema (separate from the monolith's
`product` schema, though the same physical Postgres instance/database — per-service-per-schema, see
root `CLAUDE.md`'s Database Conventions), its own port (`8083`), and its own Liquibase changelog/
docker-compose file. `gateway` no longer has a Maven dependency on this module at all — unlike
`identity-service`'s extraction, this one needed no call-site rewrite in `gateway`: `ProjectApi`/
`TaskApi` were already this module's own REST layer, just riding on `gateway`'s Spring context via
the Maven dependency, so dropping it was a pure `pom.xml` edit. **`gateway`-side HTTP proxying to
this service is not built yet** — until it is, this service is only reachable directly on its own
port, same limitation `ecommerce-service`/`identity-service` have.

- `TaskServiceApplication` — `@SpringBootApplication` +
  `@Import({JacksonConfig.class, TraceContextFilter.class, KeycloakRealmRoleConverter.class,
  KeycloakJwtAuthenticationConverter.class})` entry point. Names the exact `infra` beans this
  module uses instead of widening `@ComponentScan`/`@ConfigurationPropertiesScan` to the whole
  sibling `infra` package the way an earlier revision did — that broad-scan approach took three
  rounds of real startup failures to get right (`ConflictingBeanDefinitionException` from a stale
  build artifact, a bare `@ConfigurationPropertiesScan` not reaching `infra` at all, then
  `infra.config.thread.AsyncEventThreadPoolConfig` getting instantiated and failing to construct
  even though this module dispatches no `@EventHandler`) before landing on this explicit-import
  shape instead — see `infra/CLAUDE.md`'s note and `docs/CHANGELOG.md`'s `[Unreleased]` entry for
  the full history. `AsyncEventThreadPoolConfig` is deliberately **not** imported here — this
  module has no `@EventHandler` to dispatch, so unlike a blanket package scan, that bean (and its
  Micrometer instrumentation) is simply never created in this context. **No
  `@EntityScan`/`@EnableJpaRepositories`** — this
  module doesn't touch `common.entity.User`/`common.repository.UserRepository` at all (unlike
  `identity-service`, and *unlike an earlier revision of this module itself* — see the "No local
  `User` copy" rule below). Default scanning already covers this module's own `entity`/`repository`
  packages.
- `security/` — this app's own filter chain, independent of `gateway`'s, since it now runs on its
  own port and must guard its own endpoints regardless of whether `gateway` is proxying to it
  (mirrors `identity-service`'s/`ecommerce-service`'s `security/`). `SecurityConfig` requires
  authentication on every endpoint (`/api/v1/projects/**`, `/api/v1/tasks/**`) except
  `/actuator/**` — single-user personal task tracker, no public or admin-only surface. **No
  `security/KeycloakRealmRoleConverter`/`KeycloakJwtAuthenticationConverter` classes of this
  module's own anymore** — both moved to `infra.security` as shared beans (see `infra/CLAUDE.md`),
  picked up via this module's existing `@ComponentScan` reaching `infra`.
  `infra.security.KeycloakJwtAuthenticationConverter` builds the
  `CustomOAuth2User` principal directly from the verified JWT's claims — no local `User` row at
  all, mirroring `ecommerce-service`'s converter rather than `gateway`'s/`identity-service`'s (see
  the "No local `User` copy" rule below). **No local `CurrentUserResolver` anymore** — this module
  uses the shared `infra.security.CurrentUserResolver.resolveUserUuid(...)` instead (see
  `infra/CLAUDE.md`), picked up via this module's existing `@ComponentScan` reaching `infra`.
- `config/web/WebMvcConfig` — registers `infra.security.CurrentUserIdArgumentResolver` (resolves
  `common.annotation.CurrentUserId String`-annotated controller parameters via
  `infra.security.CurrentUserResolver`, assigning the shared `resolveUserUuid` result to this
  module's own `ownerUuid` vocabulary). **No local `CurrentUserIdArgumentResolver` class of this
  module's own anymore** — moved to `infra.security` as a shared bean once it turned out to be
  byte-identical to `content-service`'s/`ai-service`'s (and, later, `ecommerce-service`'s) own
  copies; see `infra/CLAUDE.md`'s note.
- `entity/` — `Project` (name, description, `ownerUuid`, `status`), `Task` (`project` nullable —
  standalone tasks allowed, `ownerUuid`, title, description, `status`, `priority`, `dueDate`,
  `parentTask` nullable self-`@ManyToOne` + `subtasks` `@OneToMany` — subtask nesting capped at one
  level; see Rules below). `ownerUuid` is a plain `String` column (the Keycloak JWT's `sub` claim),
  never a `@ManyToOne` FK onto a `User` row — see the "No local `User` copy" rule below.
- `enums/` — `ProjectStatus` (`ACTIVE`/`ARCHIVED`), `TaskPriority` (`LOW`/`MEDIUM`/`HIGH`/`URGENT`),
  `TaskStatus` (`TODO`/`IN_PROGRESS`/`DONE`; `canTransitionTo(target)` guards only the no-op case).
- `repository/` — `ProjectRepository` (`findByOwnerUuid(String, Pageable)`), `TaskRepository` (+
  `JpaSpecificationExecutor<Task>`), `repository/spec/TaskSpecification` (dynamic filtering:
  project/status/priority/due-date range, always scoped to a caller-supplied `ownerUuid`, and
  always excludes subtasks — see Rules below).
- `exception/TaskErrorCode` — `PROJECT_NOT_FOUND`, `TASK_NOT_FOUND`, `TASK_INVALID_STATUS_TRANSITION`,
  `TASK_INVALID_PARENT`, implements `common`'s `ErrorCode` interface, same pattern as
  `social-service`'s `SocialErrorCode`.
- `service/{ProjectService,TaskService}` (+ `impl/`) — return entities, never DTOs, same convention
  as `social-service`'s `FriendService`. `{Project,Task}Commands` (nested `Create`/`Update` records)
  and `TaskFilter` are the service-layer input types, mirroring `content-service`'s
  `ArticleCommands` — no validation annotations there, that's the REST DTOs' job. `TaskService`
  also has `listSubtasks(ownerUuid, parentTaskId)` — unpaginated, since nesting is capped at one
  level.
- `dto/` — response records (`ProjectResponse`, `TaskResponse` — the latter's `projectId`/
  `parentTaskId` are flat `Integer`s, not nested objects) and request classes (`@Data`,
  `jakarta.validation` annotations — `Create/Update{Project,Task}Request`,
  `ChangeTaskStatusRequest`).
- `mapper/{ProjectMapper,TaskMapper}` — plain MapStruct interfaces (no injected collaborator like
  `StorageService` needed, unlike `social-service`'s `FriendMapper`).
- `api/{ProjectApi,TaskApi}` (+ `api/impl/`) — `/api/v1/projects`, `/api/v1/tasks`; every method
  takes `@CurrentUserId String ownerUuid` (not `@AuthenticationPrincipal CustomOAuth2User` — only
  the caller's own UUID is ever needed here). `TaskApi`'s status-change endpoint is
  `POST /{id}/status`, matching this codebase's existing action-endpoint style for state
  transitions rather than `PATCH`. `GET /{id}/subtasks` lists a task's subtasks.

## Rules specific to this module

- **Depends on `common` + `infra` only.** There is no dependency on `content-service` — `Task` used
  to carry an optional `@ManyToOne` FK to `content-service`'s `ContentItem` (mirroring `ai-service`
  → `content-service`), but that link was removed: it added a cross-module dependency for a feature
  no client ever used (see `docs/CHANGELOG.md`'s `[Unreleased]` entry). If a future need re-emerges
  for a task to reference a piece of RAG content, re-add the dependency deliberately rather than
  assuming this note is stale. **Never add a dependency on `gateway`, `social-service`, `ai-service`,
  `identity-service`, or `ecommerce-service`** — none of them may depend on this module either
  anymore, now that it's a standalone deployable with no shared Spring context. Cross-service
  communication would need a real network call (through `gateway`, once proxying exists), never a
  `pom.xml` entry.
- **MVP is single-user.** Every `Project`/`Task` has an `ownerUuid`; there is no shared-membership
  model. Team collaboration was explicitly discussed and deferred as a future phase, not an
  oversight — don't add multi-member projects/task assignment without confirming scope first.
- **No local `User` copy — `ownerUuid` is a plain column, never a `@ManyToOne User` foreign key.**
  An earlier revision of this module gave it its own JIT-provisioned `task.USER` table (mirroring
  `gateway`'s/`identity-service`'s pattern), on the assumption that `Project.owner`/`Task.owner`
  needing a real relational reference meant it needed a real local `User` row. Reverted once it
  became clear every ownership check here only ever compares two UUIDs ("is this row's owner the
  caller") and this module never needs to *display* another user's profile (username/avatar) — the
  two things that would actually justify a persisted copy. `KeycloakJwtAuthenticationConverter`
  builds `CustomOAuth2User` straight from the verified JWT's claims (`sub` → `userUuid`), same as
  `ecommerce-service`'s converter, and `infra.security.CurrentUserResolver`/
  `config.web.CurrentUserIdArgumentResolver` read that UUID directly with zero database access —
  see the `project-microservices-extraction-plan` memory's "Option C" discussion (written for
  `ecommerce-service`, equally applicable here). If a future feature needs to show another user's
  profile info (e.g. shared projects), reach for an event-driven read-model projection ("Option B")
  rather than resurrecting a persisted `User` copy.
- **A project/task owned by a different user throws the same `*_NOT_FOUND` as a genuinely missing
  id — never a distinguishable 403.** Mirrors `social-service`'s mutual-invisibility handling of
  blocked users (`FriendServiceImpl.resolveVisibleTarget`): don't leak "this id exists but isn't
  yours" to the caller. Every ownership check in `ProjectServiceImpl`/`TaskServiceImpl` follows this
  (`resolveOwnedProject`/`resolveOwnedTask`).
- **Services return entities, never this module's own `dto/` classes** — same split as
  `content-service`'s `ArticleService`/`social-service`'s `FriendService`. `{Project,Task}Commands`
  are the service-layer input type for multi-field mutations; REST DTOs translate into them at the
  controller, they never reach the service layer directly.
- **`TaskStatus.canTransitionTo` is deliberately permissive** — it only rejects a no-op transition
  (`target == this`); any other status may move to any other status. This is a personal task
  tracker, not a team approval workflow, so a strict linear `TODO → IN_PROGRESS → DONE` order would
  just get in the user's way. Don't add stricter transition rules, and don't replace this with a
  State-pattern class hierarchy, unless a transition needs to carry a real side effect (e.g.
  auto-stamping a completion timestamp) that a single guard method can no longer express cleanly.
- **Liquibase migrations for this module's tables live in this module's own changelog tree now**
  (`database/sql/task-service.xml` + `2026/0.0.2/*.sql`), applied via the standalone
  `task-service-liquibase.yml` docker-compose file at the repo root — the opposite of every embedded
  feature module (which still migrate via `gateway`'s changelog tree per root `CLAUDE.md`'s Database
  Conventions). Don't move future migrations back under `gateway`'s tree; this module owns its own
  schema lifecycle now, same as `ecommerce-service`/`identity-service`.
- **`DKP-0028` adds `task.PROJECT`/`task.TASK`** — the *only* migration in this module's own tree.
  `OWNER_UUID` is a plain `VARCHAR(36)` column with an index for filtering, never a foreign key
  (there is no `task.USER` table — see the "No local `User` copy" rule above). Otherwise a fresh
  snapshot of the *final* shape those tables reached in `gateway`'s tree, not a replay of that
  tree's own incremental history — see below for what that history was and why it doesn't get
  replayed here.
- **Historical, while this module was still embedded:** `gateway`'s changelog tree carried
  `DKP-0020` for the base `PROJECT`/`TASK` tables (in their *original* shape, `CONTENT_ITEM_ID` and
  all, `OWNER_ID` as a real FK to `product.USER` — it had already executed against a real local-dev
  DB by the time the two changes below came up, so two in-place edits against it were tried and
  both got caught by a Liquibase checksum-mismatch failure on the next `update`, since neither edit
  matched what had actually executed), `DKP-0021` (added `TASK.PARENT_TASK_ID`, as its own
  changeset instead of another edit to `DKP-0020`), and `DKP-0022` (dropped
  `TASK.CONTENT_ITEM_ID`, same reasoning). Those `gateway`-tree changesets are untouched (frozen,
  already-run history) but now describe an orphaned `product.PROJECT`/`product.TASK` pair
  `gateway`'s own Spring context no longer maps any entity to — this module's live schema is
  `task.PROJECT`/`task.TASK` from `DKP-0028` above, with `OWNER_UUID` replacing that `OWNER_ID` FK
  entirely (not just a re-pointed FK — see the "No local `User` copy" rule). **Any further
  `PROJECT`/`TASK` schema change needs its own new changeset** (`DKP-00xx`) in *this* module's own
  tree, not an edit to `DKP-0028` or a resurrection of `gateway`'s old tree.
- **Subtasks mirror `content-service`'s `Category` self-referential parent/child shape, capped at
  one level deep** (`Task.parentTask`/`subtasks`, not a new entity or a formal GoF Component/Leaf
  class hierarchy — a subtask is just a `Task` with `parentTask` set, so every existing endpoint
  works on it unchanged; `TASK.PARENT_TASK_ID`/`FK_TASK_PARENT`/`IDX_TASK_PARENT` live in `DKP-0021`,
  not `DKP-0020` — see above). Unlike `Category`, which allows an arbitrary tree via
  `CategoryServiceImpl.validateParentAssignment`'s ancestor walk, `TaskServiceImpl
  .validateParentAssignment` rejects any parent that is itself a subtask (`newParent.getParentTask()
  != null`) or any attempt to give a parent to a task that already has subtasks — a subtask can
  never have its own subtasks. Don't extend this to arbitrary depth without confirming scope first;
  it was a deliberate simplicity choice, not an oversight.
- **Deleting a task cascades to its subtasks** (`Task.subtasks` is `CascadeType.ALL,
  orphanRemoval = true` — same shape as `content-service`'s `ContentItem`→`ContentItemTag`), unlike
  `Category`'s `CATEGORY_HAS_CHILDREN` block-on-delete guard. This was a deliberate choice (cascade
  over block) — don't add a has-subtasks delete guard without confirming that's actually wanted.
- **A parent task's `status` never reacts to its subtasks' statuses** — fully manual/independent,
  consistent with `TaskStatus.canTransitionTo`'s deliberately permissive design. Don't add
  auto-complete-parent-when-all-subtasks-done logic without confirming scope first.
- **`GET /api/v1/tasks` (top-level list) always excludes subtasks** —
  `TaskSpecification.withFilters` unconditionally adds a `parentTask IS NULL` predicate, the same
  way `ownerUuid` is always applied. There is no opt-in flag to include subtasks in that endpoint;
  fetch them via `GET /{id}/subtasks` / `TaskService.listSubtasks` instead.
- `TaskServiceImpl`'s project-ownership check (inside `createTask`/`updateTask`) goes straight to
  `ProjectRepository`, not `ProjectService` — it's a one-line `equals()` comparison, not shared
  multi-step logic like `social-service`'s `DmServiceImpl` reusing `FriendService`'s relationship
  resolution. Don't add a `ProjectService` dependency to `TaskServiceImpl` for this; only do so if a
  future change makes the check genuinely more than a single comparison.
