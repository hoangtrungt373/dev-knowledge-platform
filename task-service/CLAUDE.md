# CLAUDE.md — task-service

Module-local guidance for `task-service`. Read alongside the root `CLAUDE.md`.

## What lives here

Personal task/project management — the knowledge-platform's task tracker. Package root:
`com.ttg.devknowledgeplatform.task.*`. Built in phases; see `docs/CHANGELOG.md`'s `[Unreleased]`
entries for the full reasoning behind each decision below.

- `entity/` — `Project` (name, description, `owner`, `status`), `Task` (`project` nullable —
  standalone tasks allowed, `owner`, title, description, `status`, `priority`, `dueDate`,
  `parentTask` nullable self-`@ManyToOne` + `subtasks` `@OneToMany` — subtask nesting capped at one
  level; see Rules below).
- `enums/` — `ProjectStatus` (`ACTIVE`/`ARCHIVED`), `TaskPriority` (`LOW`/`MEDIUM`/`HIGH`/`URGENT`),
  `TaskStatus` (`TODO`/`IN_PROGRESS`/`DONE`; `canTransitionTo(target)` guards only the no-op case).
- `repository/` — `ProjectRepository`, `TaskRepository` (+ `JpaSpecificationExecutor<Task>`),
  `repository/spec/TaskSpecification` (dynamic filtering: project/status/priority/due-date range,
  always scoped to a caller-supplied `ownerId`, and always excludes subtasks — see Rules below).
- `exception/TaskErrorCode` — `PROJECT_NOT_FOUND`, `TASK_NOT_FOUND`, `TASK_INVALID_STATUS_TRANSITION`,
  `TASK_INVALID_PARENT`, implements `common`'s `ErrorCode` interface, same pattern as
  `social-service`'s `SocialErrorCode`.
- `service/{ProjectService,TaskService}` (+ `impl/`) — return entities, never DTOs, same convention
  as `social-service`'s `FriendService`. `{Project,Task}Commands` (nested `Create`/`Update` records)
  and `TaskFilter` are the service-layer input types, mirroring `content-service`'s
  `ArticleCommands` — no validation annotations there, that's the REST DTOs' job. `TaskService`
  also has `listSubtasks(ownerId, parentTaskId)` — unpaginated, since nesting is capped at one level.
- `dto/` — response records (`ProjectResponse`, `TaskResponse` — the latter's `projectId`/
  `parentTaskId` are flat `Integer`s, not nested objects) and request classes (`@Data`,
  `jakarta.validation` annotations — `Create/Update{Project,Task}Request`,
  `ChangeTaskStatusRequest`).
- `mapper/{ProjectMapper,TaskMapper}` — plain MapStruct interfaces (no injected collaborator like
  `StorageService` needed, unlike `social-service`'s `FriendMapper`).
- `api/{ProjectApi,TaskApi}` (+ `api/impl/`) — `/api/v1/projects`, `/api/v1/tasks`; every method
  takes `@CurrentUserId Integer userId` (not `@AuthenticationPrincipal CustomOAuth2User` — only the
  caller's id is ever needed here, so this module never needs
  `spring-boot-starter-oauth2-client`). `TaskApi`'s status-change endpoint is `POST /{id}/status`,
  matching this codebase's existing action-endpoint style for state transitions rather than `PATCH`.
  `GET /{id}/subtasks` lists a task's subtasks.

## Rules specific to this module

- **Depends on `common` + `infra` only.** There is no dependency on `content-service` — `Task` used
  to carry an optional `@ManyToOne` FK to `content-service`'s `ContentItem` (mirroring `ai-service`
  → `content-service`), but that link was removed: it added a cross-module dependency for a feature
  no client ever used (see `docs/CHANGELOG.md`'s `[Unreleased]` entry). If a future need re-emerges
  for a task to reference a piece of RAG content, re-add the dependency deliberately rather than
  assuming this note is stale. **Never add a dependency on `gateway`, `social-service`, `ai-service`,
  or `identity-service`.**
- **MVP is single-user.** Every `Project`/`Task` has an `owner` (`common`'s `User`); there is no
  shared-membership model. Team collaboration was explicitly discussed and deferred as a future
  phase, not an oversight — don't add multi-member projects/task assignment without confirming
  scope first.
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
- **Liquibase migrations for this module's tables live under `gateway`'s changelog tree**
  (`DKP-0020` for the base `PROJECT`/`TASK` tables, `DKP-0021` for `TASK.PARENT_TASK_ID`, `DKP-0022`
  dropping `TASK.CONTENT_ITEM_ID` — see below) — no per-module changelog folder, same as every
  other feature module in this repo.
- **`DKP-0020` is now frozen — it has actually executed against a real (local dev) DB**, in its
  *original* shape (`CONTENT_ITEM_ID` and all). Two in-place edits were tried against it after the
  fact (removing `CONTENT_ITEM_ID`, then adding the subtask columns) — both got caught by a
  Liquibase checksum-mismatch failure on the next `update`, since neither edit matched what had
  actually executed. Resolved by reverting `DKP-0020` to exactly the version that ran, and putting
  both changes in their own new changesets instead: `DKP-0021` (adds `PARENT_TASK_ID`) and
  `DKP-0022` (drops `CONTENT_ITEM_ID`). **Any further `PROJECT`/`TASK` schema change needs its own
  new changeset** (`DKP-00xx`), not another edit to `DKP-0020`.
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
  way `ownerId` is always applied. There is no opt-in flag to include subtasks in that endpoint;
  fetch them via `GET /{id}/subtasks` / `TaskService.listSubtasks` instead.
- `TaskServiceImpl`'s project-ownership check (inside `createTask`/`updateTask`) goes straight to
  `ProjectRepository`, not `ProjectService` — it's a one-line `equals()` comparison, not shared
  multi-step logic like `social-service`'s `DmServiceImpl` reusing `FriendService`'s relationship
  resolution. Don't add a `ProjectService` dependency to `TaskServiceImpl` for this; only do so if a
  future change makes the check genuinely more than a single comparison.
