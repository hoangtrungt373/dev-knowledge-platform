# CLAUDE.md — gui

Module-local guidance for `gui`. Read alongside the root `CLAUDE.md`. This module is independent of
the Maven reactor (plain Vite/npm project) and talks to the backend only over HTTP — it has no
visibility into which backend module actually owns a given entity.

## What lives here

```
gui/src/
├── features/
│   ├── auth/       — login/signup/OTP/OAuth2 callback, profile ("Dashboard"), admin login
│   ├── chat/        — the RAG chat UI (session sidebar, message stream, sources panel)
│   ├── friends/      — friend graph UI (search, requests, friends list, blocking)
│   ├── messaging/     — 1:1 DM chat over STOMP WebSocket (Phase 1; groups/channels are Phase 2)
│   ├── content/       — content-CRUD admin screens (Category/Tag/QuestionAnswer)
│   ├── ai/             — ai-service admin/monitoring screens (pipeline metrics, embeddings index)
│   └── tasks/           — personal task/project management, fronting task-service
├── app/          — app shell: App.tsx (routes), main.tsx, theme.ts, NavBar, GuestRoute/PrivateRoute,
│                    admin-shell/ (AdminLayout, AdminDashboard — the admin nav frame + landing page)
└── shared/        — httpClient, common.types-equivalent (types.ts, incl. PagedResponse), the
                      NotificationContext, storage.ts (STORAGE_KEYS), colors.ts, errorHandler.ts,
                      useSubmitGuard, ConfirmDialog
```

Each `features/<name>/` folder owns its own `api/`, `types.ts`, `pages/`, `components/`, `hooks/` —
whichever of those it needs; nothing is centralized by layer anymore (see "Why this shape" below).
Cross-directory imports use path aliases (`@shared/*`, `@app/*`, `@auth/*`, `@chat/*`, `@friends/*`,
`@messaging/*`, `@content/*`, `@ai/*`, `@tasks/*` — defined in both `tsconfig.json`'s `compilerOptions.paths` and
`vite.config.ts`'s `resolve.alias`, and must be kept in sync between the two) instead of relative
`../../` traversal — imports within a single feature (e.g. a page importing its own feature's
`api/`) stay relative (`../api/chatApi`), only *cross*-feature imports use the alias.

- Routing: `react-router-dom` v6, wired up in `app/App.tsx`. **No route-level code-splitting today**
  — every page component is still a static import (`React.lazy`/`Suspense` was tried and reverted;
  see `docs/CHANGELOG-ARCHIVE.md`/git history if picking this back up — the diagnosis, that every
  OIDC redirect round trip (`Login.tsx`'s/`AdminLogin.tsx`'s/social login's `window.location.href`
  flows, RP-initiated `logout()`, the email-verification `sendVerifyEmail` redirect) forces a full
  page reload that fetches every route's entire dependency tree regardless of which one route it
  lands on, still holds — this was deferred as a "later" optimization, not ruled out). An unused
  `app/ErrorBoundary.tsx` from that attempt is still in the tree — not wired into `App.tsx` — since
  it's a real prerequisite (lazy-loading needs an error boundary for a failed chunk load) if this
  gets revisited, rather than something to delete and re-write from scratch next time.
- No global state library (no Redux/Zustand) — state is React Context + hooks.
- **No test framework is configured yet** — `package.json` only has `dev`/`build`/`preview` scripts,
  no `vitest`/`jest`, no test config file. `app/App.test.tsx` and `src/reportWebVitals.ts` are
  create-react-app-era leftovers that don't compile under `tsc --noEmit` (missing
  `@testing-library/react`/`web-vitals` — never added as dependencies) and were never part of the
  real Vite entry chain (`index.html` points at `main.tsx`, not `index.tsx`); pre-existing, not
  touched by the feature-folder move. Don't assume a `npm test` command works or invent test file
  conventions; if asked to add tests, that's a new tooling decision, not an existing pattern to follow.

## Why this shape (feature folders, not a package-per-feature split)

Before this reorg, every top-level folder (`api/`, `types/`, `components/`, `pages/`) held files for
every feature area mixed together — the same "centralize by layer" shape the backend moved away from
in its vertical-slice refactor (see root `CLAUDE.md`). Working on one feature meant touching four
different top-level directories, and `api/index.ts`/`types/index.ts` barrel files grew one export
line per feature forever. A full audit (every file, every import) found the "admin" grouping was
also conflating two orthogonal things: domain ownership and role-based access. Category/Tag/
QuestionAnswer CRUD fronts `content-service`; pipeline-metrics/embeddings monitoring fronts
`ai-service`'s admin endpoints — the only thing they had in common was requiring the `ADMIN` role,
which is a routing/access concern (`PrivateRoute requireRole="ADMIN"`), not a domain one — mirroring
how the backend keeps `@PreAuthorize("hasRole('ADMIN')")` on individual controller methods *inside*
`content-service`/`ai-service` rather than a separate "admin module."

**What we deliberately did NOT do**: split into separate npm packages/workspaces (a literal mirror
of the backend's Maven multi-module split). That split is justified on the backend by
microservices-readiness — each module becomes an independently *deployable* service. This is one SPA
with one Vite build and one deployment artifact; there's no second deployable in view, and workspace
tooling (npm/pnpm workspaces, Nx/Turborepo, or module federation) would add real overhead for zero
payoff today. Feature folders within the single package capture the same "own your full vertical
slice" benefit without that cost — revisit only if a genuine second deployable ever shows up.

## Rules specific to this module

- **One `api/*.ts` + `types.ts` pair per feature**, matching the backend's module boundaries now
  that the split lines up with them (`content` → `content-service`, `ai` → `ai-service`, `friends` →
  `social-service`, `auth` → `identity-service`). Two backend endpoint groups that live under one
  Java class got split across features on the GUI side because they front different backend
  modules — mirror this if the backend ever does the same:
  - `identity-service`'s `UserApi.java` (`updateProfile`/`uploadAvatar`, plus `/api/v1/auth/user`)
    → `@auth/api/profileApi.ts`.
  - `social-service`'s `UserApi.java` (`getPublicProfile`/`search`) → `@friends/api/userApi.ts` —
    kept as its own file, not folded into `friendApi.ts`, mirroring the backend's own
    `UserApi`/`FriendApi` split.
- **Cross-feature type reuse mirrors the backend's own dependency direction** — `@ai/types.ts`
  imports `ContentStatus`/`EmbeddingContentType` from `@content/types`, the same direction as
  `ai-service` depending on `content-service` for `ContentItem`. Never import the other way.
- **Path aliases, not deep relative imports, for anything outside the current feature.** If you add
  a new feature folder, add its alias to *both* `tsconfig.json` and `vite.config.ts` — they don't
  share config and silently drifting out of sync breaks either the editor/type-checker or the dev
  server/build, not both at once, which makes the mismatch easy to miss.
- **Friend Management UI** (`@friends/pages/FriendsPage.tsx`, `@friends/components/*`) — single page
  with MUI Tabs (Find People / Friends / Requests / Sent Requests / Blocked), Facebook-modeled. The
  one piece worth understanding before touching this area: `RelationshipActionButton` renders a
  different action per `RelationshipStatus` (`STRANGER`→Add Friend, `FRIENDS`→Friends▾ menu, etc.)
  via a `switch` with no `default` — TypeScript's exhaustiveness checking on a `strict: true`
  literal-union switch means adding a new `RelationshipStatus` value is a compile error here until
  handled, mirroring `social-service`'s own exhaustive-switch discipline over
  `FriendRequestStatus`. **Known backend gap**: `UserSearchResultResponse` doesn't carry a
  friend-request id, so `REQUEST_SENT`/`REQUEST_RECEIVED` render as informational chips (not
  actionable buttons) when encountered via search — cancel/accept/reject need a request id, which
  only the Requests tabs' `FriendRequest[]` responses actually carry. Don't try to wire those
  actions up from search results without first adding the id to that response.
  A "Message" icon button next to `FriendsMenuButton` (in both `FriendsList.tsx` and
  `RelationshipActionButton`'s `FRIENDS` case) navigates to `@messaging`'s
  `/messages/new/:recipientUuid` route.
- **`@messaging` (1:1 DM chat, Phase 1)** — fronts `social-service`'s `DmApi`/`DmMessagingApi`. Own
  feature folder rather than folded into `@friends`, even though both front the same backend module:
  chat is functionally distinct enough (and groups/channels land here too in Phase 2) to warrant it,
  same reasoning as `@ai`/`@content` both fronting different concerns despite once being lumped
  under one "admin" grouping. Key patterns, don't deviate without re-reading
  `context/StompConnectionContext.tsx` and `api/socket.ts` first:
  - **One shared STOMP connection for the whole app** (`StompConnectionContext`, provided in
    `App.tsx` next to `NotificationProvider`), not one per page/hook — avoids a repeat WS
    handshake + STOMP `CONNECT` auth round trip on every conversation switch, and centralizes the
    `/user/queue/errors` → `showError` wiring and the single real `/user/queue/dms` subscription
    (fanned out to listeners, not re-subscribed per component).
  - **No optimistic local message append.** The backend echoes every sent message back to its own
    sender, so `useDmThread`'s `send` only publishes over STOMP — the message renders once it comes
    back over the wire, same code path as a message from the other participant. This is also how a
    brand-new conversation (`/messages/new/:recipientUuid`, no thread yet) discovers its `threadId`.
  - Sending is always keyed by `recipientUuid` (`/app/dms/{recipientUuid}/messages`), never
    `threadId` — the backend resolves-or-creates the thread server-side regardless. History reads
    (`GET /api/v1/dms/{threadId}/messages`) are keyed by `threadId`, which is why a pending new
    conversation has no history to load.
  - **Attachments are not wired** — composer is text-only. `MessageAttachmentRequest`'s own Javadoc
    says the upload endpoint doesn't exist server-side yet; don't add an attachment button without
    a backend upload endpoint to call first.
  - `ConversationList` is presentational (thread data + loading state passed down from
    `MessagesPage`, which owns the one `useDmThreads()` call) rather than fetching its own data —
    lets the page resolve the active thread's `otherUser` for its header without a second fetch.
- **`@tasks` (personal task/project management)** — fronts `task-service`'s `ProjectApi`/`TaskApi`.
  `pages/TasksPage.tsx` is a 3-pane Todoist/Asana-style layout, not a tabbed hub: a left
  `components/TasksSidebar.tsx` (Inbox/Today/This week smart filters + a Projects list with inline
  create/edit/archive via `ProjectFormDialog`), a middle column (a section headline — icon + name,
  see below — then `TaskQuickAdd.tsx`, then either the Overdue/Today/Upcoming/Completed sectioned
  view — used for Inbox/Today/This week alike, see `isBucketedView` below — or, only for a project
  filter, a flat list — `utils/taskBuckets.ts` owns the bucketing/date-range math), and an
  always-reserved right
  `components/TaskDetailPanel.tsx` (all fields inline-editable, delete, read-only subtask list,
  empty-state placeholder when nothing is selected — see its own detailed note further down).
  Layout precedent copied from
  `@messaging/pages/MessagesPage.tsx`'s fixed-width-sidebar + flex-1-content shape originally, but
  no longer literally fixed-width — see the resizable-panels note further down. Not admin-gated —
  every `Project`/`Task` is owned by the caller, so it's a top-level
  `PrivateRoute` like `@friends`, not nested under `/admin`. "Inbox" (`InboxIcon`) is just a label
  over the plain `'all'` `TaskFilter` value, not a new variant — it's the same bucketed dashboard
  that was always the filter's initial/default state, now with an explicit, always-visible sidebar
  entry point back to it (it previously had none; the only way back was the initial page load or
  `TasksSidebar`'s own `handleArchive` falling back to `'all'` after archiving the active project).
  - **The 3 columns are resizable** via `react-resizable-panels` (new dependency — chosen over
    hand-rolling pointer-event drag logic, which has real edge cases: pointer capture, keyboard
    resize, min/max clamping, touch support). `TasksPage.tsx` wraps the 3 columns in
    `<Group orientation="horizontal">` / `<Panel id="tasks-sidebar"|"task-content"|"task-detail">` /
    `components/ResizeHandle.tsx` (the library's `Separator`, styled). **The installed version (4.x)
    renamed the whole API** from the commonly-referenced older docs for this library —
    `Group`/`Panel`/`Separator`, not `PanelGroup`/`Panel`/`PanelResizeHandle` — don't assume the
    older names from memory when touching this again; check `node_modules/react-resizable-panels/dist/react-resizable-panels.d.ts`
    directly if unsure. Default sizes are `20`/`40`/`40` (percent) — `task-content` and
    `task-detail` deliberately match, since the middle column being the same size as the detail
    panel by default was this feature's own requirement, not an arbitrary choice.
    - **Layout is persisted frontend-only** (same "stored client-side, never sent to the backend"
      approach as `useTaskOrder`), via the library's own `useDefaultLayout({ id, storage:
      window.localStorage, panelIds })` hook, not a hand-rolled read/write. `panelIds` **must** list
      the same three ids the `Panel`s use (`tasks-sidebar`/`task-content`/`task-detail`) or the
      persisted layout won't reapply correctly on mount — keep these in sync if a `Panel`'s `id`
      ever changes. Actual storage key:
      `react-resizable-panels:tasks-page-layout:tasks-sidebar:task-content:task-detail`.
    - `TasksSidebar.tsx` lost its fixed `width: 260, flexShrink: 0` (the `Panel` controls width now)
      and its own `borderRight` — `ResizeHandle` between it and `task-content` is the visual divider
      now, and drawing both would double up. `TaskDetailPanel.tsx`'s two return branches (`!task`
      empty state and the main content) both switched `flex: 1` → `height: '100%'`; the middle
      `task-content` column's wrapping `Box` (in `TasksPage.tsx`) made the same `flex: 2` →
      `height: '100%'` swap and dropped its own now-redundant `borderRight` too, while keeping its
      `pl`/`pr` gutter styling (`TASK_ROW_ACTIONS_GUTTER_PX`) untouched — sizing along the horizontal
      axis is the `Panel`'s job now, none of these three need their own width/flex-basis opinion,
      just `height: 100%` to fill their `Panel` vertically.
    - `ResizeHandle.tsx` styles the library's unstyled `Separator` (it only accepts plain
      `className`/`style`, no MUI `sx`) via MUI's own `styled()` from `@mui/material/styles` — **not**
      raw `@emotion/styled` (confirmed by an actual `tsc` error when tried first: its callback
      `theme` param type isn't aware of MUI's `palette` augmentation). A 4px-wide hit target with a
      thin 1px line centered inside it via `::after`, widening to 2px and recoloring to
      `primary.main` on `:hover`/`:active`/`:focus-visible` — the library exposes no "currently
      dragging" `data-*` attribute to hook a dedicated third state off, so `:active` is what covers
      the drag case.
    - Verified via a real render + `getBoundingClientRect` measurement in headless Chrome (not
      layout math alone, per this file's own established practice for anything box-model-related in
      `@tasks`): confirmed the default 20/40/40 split lands `task-content`/`task-detail` at
      pixel-identical widths, and that a synthesized drag on the sidebar/`task-content` separator
      moves exactly the dragged distance while leaving `task-detail` completely untouched.
  - The middle column's headline (`sectionLabel`/`SectionIcon` in `TasksPage.tsx`, plain local
    consts, not extracted anywhere) always names whichever filter is active: `InboxIcon`/`'Inbox'`,
    `TodayIcon`/`'Today'`, `DateRangeIcon`/`'This week'` — the exact same icons `TasksSidebar.tsx`
    uses for those entries, kept in sync deliberately (don't let one change without the other). A
    project filter falls back to the project's own name (`projects.find(p => p.id ===
    filter.projectId)?.name`) with a new `FolderIcon` — projects have no icon of their own in the
    sidebar (`TasksSidebar`'s project `ListItemButton`s are text-only), so this is a one-off default
    introduced just for this headline, not a convention reused elsewhere in `@tasks`.
  - `components/TaskRow.tsx` is a single leaf row reused both by the main list (via an optional
    `onSelect` prop, absent when rendering a subtask inside `TaskDetailPanel`) and by the detail
    panel's subtask list. Takes no `projects` prop — it never rendered a task-editing dialog itself
    (full editing lives in `TasksPage`/`TaskDetailPanel`), so don't add one back without a need.
    Row shape: optional leading drag handle (see drag-to-reorder below) / checkbox / title
    (inline-editable, see below) / due-date chip (if set) / a single "⋯" (`MoreHorizIcon`) button —
    no status `Chip`, no standalone Edit button; both were tried and superseded by the "⋯" menu
    (see `TaskOptionsMenu.tsx` below). Priority has no `Chip` either: it's the checkbox's
    `icon`/`checkedIcon` color (`CheckBoxOutlineBlankIcon` gray/blue/orange/red for
    Low/Medium/High/Urgent when unchecked, a fixed neutral `success.main` `CheckBoxIcon` when
    checked regardless of priority — a red checkmark on a completed Urgent task read as
    contradictory next to the strikethrough). Its own `Props` interface is exported as
    `TaskRowProps` (not just `Props`) so `SortableTaskRow` can type its wrapper as
    `Omit<TaskRowProps, 'dragHandle'>` without redeclaring the list. Both the `Checkbox` and the
    "⋯" button render unwrapped, no `Tooltip` — deliberate (`Tooltip` requires a non-empty
    `title`), don't reintroduce one without real label text.
  - **Inline title rename**: clicking the title `Typography` swaps it for a `TextField`
    (`variant="standard"`, `disableUnderline`, `autoFocus`), committed on blur/Enter, discarded on
    Escape (`startEditingTitle`/`commitTitleEdit`) — no dialog for a rename-only change. An
    `optimisticTitle` local state bridges the gap between commit and `onChanged()`'s refetch
    landing, so switching back to `Typography` doesn't blink to the stale pre-edit title for one
    frame. Selecting the row (`onSelect`, main-list usage only) also calls `startEditingTitle()`, so
    one click both opens `TaskDetailPanel` and focuses the rename field via `autoFocus` — don't
    remove that pairing without confirming it's no longer wanted, it wasn't accidental.
    The title wrapper `Box` **must** stay `display: 'flex', alignItems: 'center'` — without it, the
    title visibly jumps ~2px vertically switching between `Typography`/`TextField`, since
    `Typography` (inline content, subject to half-leading against the ambient line-height) and
    `TextField`'s root (`display: inline-flex`, exempt from half-leading) resolve their box position
    via different algorithms. This isn't a font-metrics thing — matching `font-size`/`line-height`
    between the two (the `.MuiInputBase-root`/`-input` overrides already there, kept for a real but
    separate reason: without them the input's font visibly grows to MUI's default 16px) cannot fix
    it; only forcing both under the same flexbox alignment does. Confirmed via headless-Chrome CDP
    `getBoundingClientRect` measurement, not just source-reading — don't "simplify" this back to a
    plain inline `Box` assuming the flex is decorative.
  - The "⋯" button is taken fully out of the row's flex flow — `position: absolute` (against
    `position: relative` on the row `Stack`) rather than just `opacity: 0` — landing in a dedicated
    gutter to the row's right rather than reserving in-flow width while hidden (that in-flow
    reservation used to push the due-date label left of the row's true right edge even while the
    button was invisible). The exported `TASK_ROW_ACTIONS_GUTTER_PX` constant (currently `28`)
    sizes both this button's `right` offset and part of the extra right padding its containers
    reserve (`TasksPage.tsx`'s list column, `TaskDetailPanel.tsx`'s subtask-list `Box`) — change it
    in one place, not independently in three (see the `8 +` flat-margin caveat further down for the
    other part of that padding, which is a separate, unrelated knob). It's still hidden via `opacity: 0`→`1` on
    hover/focus-within/`menuAnchor` set, same trigger as before: the row `Stack`'s
    `'&:hover .task-row-more, &:hover .task-row-drag-handle, &:focus-within .task-row-more,
    &:focus-within .task-row-drag-handle': { opacity: 1 }` (now covering the drag handle's class
    too — see below). `opacity`, not `display`/`visibility: hidden`, so keyboard users can still Tab
    to it before hovering. Both this button and the drag handle below drop the `size="small"` prop
    in favor of an explicit `sx={{ p: '4px' }}` (smaller than `size="small"`'s own ~5px), and their
    icons set `sx={{ fontSize: 18 }}` directly rather than the `fontSize="small"` prop — MUI's
    `"small"` icon preset is 20px with no smaller built-in preset, so shrinking further meant setting
    the CSS `font-size` directly. Reduced from an earlier, larger gutter/icon pairing that read as
    too much empty margin once both sides of the row were widened — if you need to resize either
    icon again, keep both in step (they're meant to read as the same size) and re-tune the `- 4`
    inset (in both this button's `right` and the drag handle's `left`) if the button's visual box
    changes enough to look off-center within the narrower gutter.
    `TasksPage.tsx`'s list-column `pl`/`pr` are `` `${8 + TASK_ROW_ACTIONS_GUTTER_PX}px` `` — the
    `8` and `TASK_ROW_ACTIONS_GUTTER_PX` terms are **not** interchangeable, don't collapse them into
    one constant or tune the wrong one when the row looks too cramped/wide.
    `TASK_ROW_ACTIONS_GUTTER_PX` sets how far the icon clears the row's own content (checkbox/date)
    — it's lower-bounded by the icon button's own footprint (~26px) and starts overlapping real row
    content if shrunk much past that, so treat it as load-bearing, not a free styling knob. The `8`
    is a flat margin from the column's *true* edge, with zero relationship to the icon's size — it's
    the one that's safe to tune freely if the overall row still looks too wide or too tight
    (`TaskDetailPanel.tsx`'s subtask-list `pr`, by contrast, is plain `` `${TASK_ROW_ACTIONS_GUTTER_PX}px` ``
    with no flat term at all, since subtask rows never render a drag handle and so never needed
    this extra outer-margin concern in the first place).
  - **Drag-to-reorder** (frontend-only — no backend order field or endpoint exists, and none is
    planned; see `docs/CHANGELOG.md`'s `[Unreleased]` entry for the full reasoning) is wired in for
    both `TasksPage.tsx`'s flat-list branch (project filter only — see `isBucketedView` above) and
    the bucketed Overdue/Today/Upcoming/Completed view (Inbox/Today/This week alike), via a separate
    `components/SortableTaskRow.tsx` wrapper — **not** built into `TaskRow.tsx` itself, so the far
    more common non-draggable case (subtask rows inside `TaskDetailPanel`) carries no `@dnd-kit`
    dependency. `SortableTaskRow` calls `useSortable({ id: task.id })` and passes a
    `DragIndicatorIcon` `IconButton` (`className="task-row-drag-handle"`, `{...attributes}
    {...listeners}`, its own `onClick` `stopPropagation` — same guard every other directly-clickable
    row child has) into `TaskRow` via its `dragHandle` prop, rather than making the whole row
    draggable — `TaskRow` already overloads click for select/rename (above), so a dedicated handle
    avoids click-vs-drag-start conflicts. Like the "⋯" button, the handle is `position: absolute`
    (against `TaskRow`'s own `position: relative` `Stack`) rather than left in-flow with just an
    `opacity: 0`/`1` toggle — an in-flow hidden handle would reserve its width at the row's start at
    all times, shifting everything else in the row right even while not hovering. It reuses the same
    `TASK_ROW_ACTIONS_GUTTER_PX` constant the "⋯" button's right-side gutter uses, mirrored onto the
    left (`TasksPage.tsx`'s list column widens both `pl` and `pr` by that amount now, not just `pr`)
    — `TaskDetailPanel.tsx`'s subtask-list container only ever needed the `pr` side, since subtask
    rows render plain `TaskRow` (never wrapped in `SortableTaskRow`) and so never render a drag
    handle to reserve room for in the first place.
    - In the bucketed view, each of the four buckets (Overdue/Today/Upcoming/Completed) gets its
      **own** `DndContext`/`SortableContext` and its own `useTaskOrder` call (view keys
      `'bucket:OVERDUE'`/`'bucket:TODAY'`/`'bucket:UPCOMING'`/`'bucket:COMPLETED'`, distinct from
      the flat-list branch's `` `project:${id}` `` key — `'today'`/`'week'` no longer have their own
      flat-list view keys at all, since they render bucketed now) — dragging only ever reorders
      within one bucket; there is no cross-bucket drop target. These bucket keys are deliberately
      **shared** across Inbox/Today/This week — a task due today keeps the same manual position
      within "today's tasks" regardless of which of the three sidebar entries got you there, since
      it's conceptually the same bucket either way. Bucket *membership* still
      comes entirely from `bucketTasks(tasks)` (due date/status), never from the manual order —
      don't wire cross-bucket dragging in as "move this task's due date" without confirming that's
      actually wanted, it'd be a materially different feature (a real mutation, not a client-side
      reorder). `TasksPage.tsx`'s `buckets` value is `useMemo`'d on `tasks` — required once bucket
      arrays started feeding `useTaskOrder`, since an unmemoized `bucketTasks(tasks)` call returns a
      new array reference on every render (even ones that don't touch `tasks`, e.g. selecting a
      row), which would otherwise re-run `useTaskOrder`'s `localStorage` reconciliation on every
      unrelated re-render. The 4 `useTaskOrder` calls are unrolled at the component's top level
      (`overdueOrder`/`todayBucketOrder`/`upcomingOrder`/`completedOrder`, collected into a
      `bucketOrders` record keyed by `TaskBucketKey`) rather than called inside
      `BUCKET_ORDER.map(...)` — React's rules of hooks require the same fixed number of hook calls
      every render, and `BUCKET_ORDER` is a fixed, known 4-key array, so this is safe; don't turn it
      into a loop calling the hook dynamically.
    - The actual order state/persistence lives in `hooks/useTaskOrder.ts` (one `localStorage` array
      of task ids per view, reconciled against every fresh fetch — see the hook's own doc comment),
      not in `TasksPage.tsx` or `SortableTaskRow` itself.
  - `TaskRow`'s `selected` prop gives the row a persistent `action.selected` background (vs. the
    plain `action.hover` tint for an unselected row's hover) — `TasksPage.tsx` passes
    `selected={selectedTask?.id === task.id}` at both call sites so the row backing the open
    `TaskDetailPanel` stays visually distinct even after the mouse moves away; hovering a
    `selected` row keeps `action.selected` rather than swapping to `action.hover`, so the "this is
    the open task" cue doesn't flicker. Not wired into `TaskDetailPanel`'s subtask-row usage —
    subtasks have no independent "open" state. `px: 1, mx: -1, borderRadius: 1` on the row keep the
    tint reading as a rounded highlight instead of a hard-edged rectangle.
  - The whole row is the click target for opening `TaskDetailPanel` (and, per above, for entering
    title-rename mode in the same click) — `onClick` lives on the row `Stack`, not the title
    `Typography` (clicking anywhere in the row, not just the title text, calls `onSelect`).
    Deliberately no `cursor: pointer` — the row's own hover background is the intended hover
    affordance. Because `Checkbox`, the "⋯" button, and (when present) the drag handle are all DOM
    descendants of that `Stack`, a native click on any of them still bubbles up to the row's
    handler — each calls `e.stopPropagation()` first in its own `onClick` so toggling done, opening
    the "⋯" menu, or grabbing the drag handle doesn't also fire `onSelect`/rename. `Menu`/
    `ConfirmDialog` contents don't need this: MUI portals them outside the row's DOM subtree, so
    their clicks never reach the row handler regardless of React-tree nesting. Give any new
    directly-clickable child of this row the same treatment.
  - Row height matches `TaskQuickAdd.tsx`'s "+ Add a task…" `TextField` (`size="small"`, ~37px) by
    design — `Stack`'s `minHeight: 37, py: 0` plus `Checkbox`'s `sx={{ p: '8px' }}` (its default
    small-size touch target is 38px on its own, taller than the target, so it needed shrinking too
    — `minHeight` on the `Stack` alone wasn't sufficient). If you add a taller child to this row,
    re-measure both elements' real `boundingBox()` in a browser rather than trusting theme-spacing
    arithmetic — that's how this was tuned, not guessed.
  - **`components/TaskOptionsMenu.tsx`** — the shared "⋯" menu body, extracted out of `TaskRow.tsx`
    so `TaskQuickAdd.tsx` could reuse it instead of keeping its own near-duplicate. Fully
    controlled via props, no internal notion of "a task": `priority`/`onPriorityChange` (always
    required) plus three independently optional pairs — `dueDate`/`onDueDateChange`,
    `status`/`onStatusChange`, `onDelete` — each pair's *presence* (not a separate boolean prop)
    decides whether that section renders at all, e.g. `Boolean(onStatusChange && status)` for
    Status. Content is a single `Box`, not a list of `MenuItem`s: a caption label above a `Stack
    direction="row"` of icon buttons per section (**Date, Priority, Status** in that order — Date
    leads since it's the one most often changed quickly), current value highlighted via `bgcolor:
    'action.selected'` on a `borderRadius: 1` square (not `MenuItem`'s `selected` prop, and
    deliberately not the `IconButton`'s own circular shape — a square reads more clearly as
    "active" among same-sized icons), plus a **Delete** `MenuItem` below a `Divider` when
    `onDelete` is passed. None of this opens a `Dialog` — menu-only, by design. Date icons:
    `WbSunnyIcon`/`WbTwilightIcon`/`CalendarViewWeekIcon` for Today/Tomorrow/This week (picked for
    legibility — verify an icon exists in the installed `@mui/icons-material` version by checking
    `node_modules/@mui/icons-material/` before using a new one, rather than assuming from memory),
    `CalendarMonthIcon` "Custom" toggles a native `<TextField type="date">` inline instead of
    applying immediately. `datePresetValue()` (from `utils/taskBuckets.ts`) computes each preset;
    the currently-matching one is compared by calendar day (`toDateString()`), not exact instant
    equality — an unmatched-but-set due date highlights **Custom** instead.
  - `TaskRow.tsx` passes all four pairs to `TaskOptionsMenu` — `onPriorityChange`/`onDueDateChange`
    call `taskApi.updateTask` with the task's current field values plus the one changed field
    (there's no dedicated "change priority"/"change due date" endpoint —
    `UpdateTaskPayload`/`TaskServiceImpl.updateTask` fully replace mutable fields, see
    `task-service/CLAUDE.md`, so every other field must be resent unchanged, same approach
    `TaskDetailPanel`'s own field edits use), `onStatusChange` calls `POST /{id}/status` directly (no
    confirm dialog — matches `TaskStatus.canTransitionTo`'s deliberately permissive backend
    behavior), and `onDelete` just opens `TaskRow`'s own `ConfirmDialog` (the actual delete stays
    outside `TaskOptionsMenu`). `TaskQuickAdd.tsx`'s "more options" menu passes only
    `priority`/`onPriorityChange={setPriority}` — no `dueDate`/`status`/`onDelete`, so only the
    Priority row renders; its calendar-icon trigger opens `components/DatePickerMenu.tsx` instead
    (Date presets + Custom picker), a standalone popover kept apart from `TaskOptionsMenu` because
    `TaskQuickAdd` wants that trigger visible on the input itself rather than folded into "more
    options." `TaskRow.tsx`'s due-date label click opens the same `DatePickerMenu` directly (its own
    `dateMenuAnchor` state, only rendered when `dueLabel` is set) as a second way to change an
    existing due date without opening "⋯" first — both paths call the same
    `handleDueDateChange`, so they can't drift out of sync. `DatePickerMenu` used to be duplicated
    inline in `TaskQuickAdd.tsx`; it was extracted once `TaskRow` needed the identical popover,
    rather than adding a third near-copy of it (`TaskOptionsMenu.tsx`'s own inline Date *section* is
    the one place this markup still isn't shared — it's a fieldset inside one combined menu, not
    its own standalone `Menu`, so it wasn't a clean fit for the same extraction without a bigger
    restructure).
  - `components/TaskQuickAdd.tsx`'s outer `Stack` carries `mx: -1` (deliberately **not** `px: 1` too
    — see below) so its `fullWidth` `TextField` lines up with `TaskRow.tsx`'s own row `Stack`, which
    bleeds 8px past its container on each side for its rounded-hover-highlight look (see
    `TaskRow.tsx`'s own note above). Copying `TaskRow`'s exact `mx: -1, px: 1` pair here was tried
    first and looked right on paper (both `Stack`s' own boxes did end up the same bled width,
    confirmed via `getBoundingClientRect`) but was still wrong: the two components draw their
    *visible* border at different nesting depths. `TaskRow`'s border-bottom is drawn directly on its
    own bled `Stack`, so the visible edge **is** the bleed — the `px: 1` there exists to push
    `TaskRow`'s *content* (checkbox/title) back in so it stays aligned while only the
    background/border bleeds. `TaskQuickAdd` has no such content-vs-border split: the `TextField`'s
    own outline *is* the thing that should bleed, so adding `px: 1` on its parent `Stack` just insets
    that visible border 8px back in from the bleed — reproducing the same mismatch one level deeper,
    which is exactly what happened the first time this was "fixed." `mx: -1` alone lets the
    `fullWidth` `TextField` itself fill the widened box, so its outline reaches the same edges
    `TaskRow`'s border does. Verified via a real headless-Chrome CDP `getBoundingClientRect`
    measurement of both, not just source-reading — don't reintroduce `px: 1` here based on
    source-level pattern-matching against `TaskRow` without re-measuring.
  - `components/TaskQuickAdd.tsx` has no visible date field, priority chip, or "Add" button — just
    the title `TextField` with two `InputProps.endAdornment` `IconButton`s: `CalendarMonthIcon`
    (opens `DatePickerMenu`, described above) and `ExpandMoreIcon` (opens the Priority-only
    `TaskOptionsMenu`) — deliberately the same chevron `TasksPage.tsx`'s bucket-header
    expand/collapse uses, not `ArrowDropDownIcon`, so every "opens more stuff below" trigger in
    this feature shares one glyph. Both icons just set local `dueDate`/`priority` state — no API
    call fires until the task is actually created, unlike `TaskRow`'s wiring of the same menu.
    The `ExpandMoreIcon` button has no `Tooltip` of its own — it used to show `"Priority: X"`, but
    that's redundant now that `TaskOptionsMenu`'s flag icons each carry their own tooltip (same
    reasoning as `TaskRow`'s checkbox/"⋯" button). The calendar icon keeps its `Tooltip`
    (`"Due X"`/`"Set due date"`) — not the same situation, since there's no per-icon tooltip inside
    its own popover conveying "no date set yet" the way the flag icons do for priority.
    Submit is Enter-only (no `Button`); `handleSubmit`'s `finally` block calls
    `inputRef.current?.focus()` so consecutive tasks can be typed without touching the mouse.
    **Don't re-add `disabled={saving}` to the title field** — it was there once and silently broke
    that refocus (a disabled input can't receive `.focus()`, and the input was still disabled in
    the DOM at the exact moment the call ran, one render behind `setSaving(false)`); the existing
    `if (!trimmed || saving) return` guard in `handleSubmit` already prevents a double-submit
    without it.
  - **`components/TaskDetailPanel.tsx` is fully inline-editable — there is no "Edit" dialog
    anymore** (`components/TaskFormDialog.tsx` was deleted outright; confirmed via a full-codebase
    grep it had no other importer). Header row: checkbox / vertical `Divider` / due-date icon+label
    (click → `DatePickerMenu`, the same shared popover `TaskRow`/`TaskQuickAdd` use) / a spacer /
    a priority flag `IconButton` (click → `TaskOptionsMenu` passed only `priority`/
    `onPriorityChange`, same minimal-usage shape `TaskQuickAdd`'s own trigger already uses) — then
    a horizontal `Divider`, then title and description, both click-to-edit the same
    `Typography` ⇄ `TextField` pattern `TaskRow`'s title already established (single-line title
    commits on blur/Enter; multiline description commits on blur only, since Enter needs to insert
    a real newline there instead of submitting). Checkbox toggling reuses `TaskRow`'s exact
    `changeTaskStatus` DONE/TODO call — same simplification `TaskRow` already made (this panel
    still has no way to set `IN_PROGRESS`; that's only reachable via `TaskOptionsMenu`'s Status
    row on a row's "⋯" menu, same as before this rewrite). Every field write goes through this
    panel's own `basePayload()` (current field values, one changed) — same full-replace constraint
    as everywhere else in `@tasks`.
    - **Deliberately deferred, not solved**: reassigning a task's project after creation, and
      adding a new subtask. Both used to go through `TaskFormDialog`; neither has a replacement.
      The project field/chip is gone from this panel entirely — a task's project is effectively
      fixed at creation (set via `TaskQuickAdd`) until this gets revisited. The subtask *list*
      itself still renders fine (doesn't depend on `TaskFormDialog`); only the old "+" add-subtask
      trigger (which opened `TaskFormDialog` with `parentTaskId` set) was removed, with nothing in
      its place — creating a subtask is currently unreachable from this panel. Don't build a
      replacement for either without confirming scope first; both were explicitly punted, not
      forgotten.
    - This panel's own `projects` prop was removed too (dead once the project field was dropped) —
      `TasksPage.tsx`'s one call site was updated to match. Don't reintroduce it speculatively.
  - **Known trade-off**: a subtask row (only ever rendered inside `TaskDetailPanel`, never
    independently selectable) still has no way to edit its title/description/due date at all —
    only Priority/Status/Delete via "⋯", same as before this rewrite. A top-level task's title/
    description/due date are inline-editable now (above), but that's specific to
    `TaskDetailPanel`'s own header/title/description markup — it doesn't extend to `TaskRow` or
    `TaskOptionsMenu`, so subtasks don't inherit it. Don't add a dialog-opening "Edit" back into
    the shared "⋯" menu without confirming that's actually wanted — the menu-only design for
    `TaskRow`/`TaskOptionsMenu` was explicit and predates this rewrite.
  - No content-item linking or project delete in the GUI yet — `Task.contentItemId` is never set
    from here (no picker built), and there's no delete action for projects since `ProjectApi` only
    exposes archive (no confirm dialog on archive either, straight `taskApi.archiveProject` call).
  - No unpaginated "list all projects"/"list all tasks" endpoint exists on the backend, so the
    project list (`TasksPage.tsx`'s `PROJECT_PICKER_SIZE`) and the dashboard/list fetch
    (`TasksPage.tsx`'s `DASHBOARD_SIZE = 200`) are pragmatic MVP caps, not a guarantee of
    completeness.
  - `TaskController`'s `ALLOWED_SORT_FIELDS` only allows `id`/`dteCreation` — `TasksPage.tsx` sorts
    the fetched page by due date client-side (no due date sorts last) rather than passing an
    unsupported `sortBy=dueDate`.
  - `TasksPage.tsx` fetches the project list exactly once — passed down as a prop only to
    `TasksSidebar` now (`TaskDetailPanel`'s own `projects` prop was removed once its project field
    was dropped; `TaskRow` never took one; `TaskFormDialog`, the only other one-time consumer, no
    longer exists), plus used directly in `TasksPage.tsx` itself for the project-filter headline's
    label. Don't add a second `taskApi.listProjects` call inside a child component; that was a real
    (non-`StrictMode`) duplicate `/api/v1/projects` request in an earlier version of this page.
    `TasksSidebar` filters
    the shared list to `ACTIVE` itself rather than being handed an already-filtered list, and calls
    `onProjectsChanged` (→ `TasksPage`'s `fetchProjects`) after create/edit/archive.
- **Two separate backend origins, not one — don't assume `VITE_BACKEND_URL` covers everything.**
  `gateway` (`VITE_BACKEND_URL`, default `http://localhost:8080`) covers everything over plain
  HTTP now, including SSE streaming chat — `@shared/api/httpClient.ts`, almost every feature's
  `api/*.ts`, and `@chat/api/chatApi.ts`'s `streamChat` (`gateway`'s own
  `routing/ChatStreamProxyController` relays that one path by hand, since Spring Cloud Gateway
  Server MVC's usual routing can't safely proxy Server-Sent Events — see that class's Javadoc).
  Only `@messaging/api/socket.ts`'s STOMP connection still bypasses `gateway`, via its own
  `VITE_SOCIAL_SERVICE_URL` (default `http://localhost:8084`, direct to `social-service`) — never
  routed at all, since Gateway Server MVC (and `ChatStreamProxyController`) only handle plain HTTP,
  not a WebSocket protocol upgrade. See `vite-env.d.ts`'s own comment and that file's own constant
  for the full reasoning. A third constant, `VITE_AI_SERVICE_URL` (direct to `ai-service`), existed
  briefly for `streamChat` before `ChatStreamProxyController` landed — removed once gateway could
  safely relay that endpoint itself; don't reintroduce it. All backend-origin constants in this
  codebase defaulted to `8081` (`ecommerce-service`'s port, a pre-extraction leftover) before any
  of this — if you find code still hardcoding that port, it's stale, not intentional.
- All backend calls go through `@shared/api/httpClient.ts` (auth headers, error normalization via
  `getUserFriendlyErrorMessage`/`getErrorDetails`) — don't call `fetch` directly from a
  page/component (the admin auth flow below is a deliberate exception, calling Keycloak's own
  endpoints directly rather than through `httpClient`/`gateway` — see its own note).
  **`httpClient.ts`'s silent-refresh-on-401 now does a real Keycloak `refresh_token` grant** —
  it used to POST to a pre-Keycloak-migration `identity-service` endpoint
  (`/api/v1/auth/refresh`) that no longer exists, so every 401 silently skipped straight to
  clearing storage and hard-redirecting to `/login`. `httpClient.ts` itself still has zero
  Keycloak knowledge (it's `@shared`, not a feature) — it exposes `setTokenRefreshHandler`, a
  module-level injection point a 401 calls if registered. `authService.ts` (which already owns
  the Keycloak URL/client constants) implements the real logic as `refreshAccessToken()`, and
  `main.tsx` wires the two together once at the composition root
  (`setTokenRefreshHandler(() => authService.refreshAccessToken())`) — this is why `main.tsx`
  imports `@auth/services/authService` even though `App.tsx` is its actual rendered content.
  **The one non-obvious piece**: a `refresh_token` grant must be requested from the exact Keycloak
  client the token was originally issued to, and this app now has two (`gui-password-login` for
  `loginWithPassword`, `gui` for the PKCE/social flows below) with no third place tracking which
  one a given session came from. Rather than adding that tracking, `refreshAccessToken()` decodes
  the `azp` (authorized party) claim straight off the currently-stored access token — Keycloak
  always stamps it with the requesting `client_id`, and decoding a JWT's payload doesn't care
  whether it's expired (only signature verification would, and this app never verifies signatures
  client-side — see `decodeJwtPayload`'s own comment).
  **`@auth/services/adminAuthService.ts` (admin login/logout) and `@auth/services/authService.ts`'s
  `loginWithPassword`/`logout` (regular login/logout) are both fixed now — deliberately using two
  *different* OAuth grant types, not drift.** `AdminLogin.tsx` uses Authorization Code + PKCE
  (`adminAuthService.startLogin()`/`handleCallback()`) against the `gui` Keycloak client — the
  recommended production shape (see `docs/CHANGELOG.md`'s `[Unreleased]` entry). `Login.tsx` uses
  direct password grant / ROPC (`authService.loginWithPassword()`) against a **separate** new
  `gui-password-login` client (`docker/keycloak/realm-export.json` —
  `directAccessGrantsEnabled: true`, kept apart from `gui` so that client never gains password-grant
  capability) — a deliberate learning choice, not the recommended pattern; don't "fix" `Login.tsx`
  into consistency with `AdminLogin.tsx` without reading `docs/CHANGELOG.md`'s entry on why. Both
  flows call straight from the browser to Keycloak (`http://localhost:8180` by default), never
  through `gateway`/`identity-service` — nothing for a backend to broker either way. Both `logout()`
  implementations now do a real RP-initiated logout against Keycloak's end-session endpoint instead
  of the old dead `POST /api/v1/auth/logout`. Shared plumbing between the two flows:
  `@auth/utils/pkce.ts` (admin-only — hand-rolled code_verifier/challenge/state, no new dependency),
  `@shared/utils/jwt.ts#decodeJwtPayload` (also used by `authService.isAuthenticated()`), and new
  `@auth/utils/keycloakClaims.ts#claimsToAuthTokens` (the "raw Keycloak token response → this app's
  `AuthTokens` shape" adapter — decode access-token claims, derive `role` — used by both
  `adminAuthService.handleCallback` and `authService.loginWithPassword` since it's identical
  regardless of which grant type produced the tokens). New `VITE_KEYCLOAK_URL`/
  `VITE_KEYCLOAK_REALM` env vars (`vite-env.d.ts`) back both. `@auth/api/authApi.ts`'s dead `login`
  method was removed outright (no remaining callers once `Login.tsx` switched to
  `authService.loginWithPassword`).
  **`SignUp.tsx` is fixed too** — `authApi.register` now calls a real, revived
  `identity-service` endpoint that creates the Keycloak account server-side via the Admin REST API
  (see that module's `CLAUDE.md`'s `KeycloakAdminService` note and `docs/CHANGELOG.md`'s
  `[Unreleased]` entry) — this needed a heavier mechanism than login regardless of grant type,
  since Keycloak's token endpoint only ever authenticates an *existing* user. `authApi.ts`'s
  `register` return type changed from `AuthTokens` to `RegisterResponse` (already defined in
  `types.ts`) to match — the new account is created pre-verified, so `SignUp.tsx`'s
  `handleSubmit` calls `authApi.register(...)` then `authService.loginWithPassword(email, password)`
  itself (the same call `Login.tsx` uses) rather than expecting tokens back from the register
  response directly.
  **Known gap, still not fixed — deliberately out of scope**: `VerifyOtp.tsx` itself (still calls
  `authApi.verifyOtp`/`resendOtp`, both of which hit `identity-service` endpoints that no longer
  exist — can't be fixed by switching grant type at all) and its `/verify-otp` route in `App.tsx`
  are still there, unreachable dead code now that nothing links to them (see the real email
  verification feature below) — left for the still-queued auth-folder cleanup pass rather than
  deleted as a drive-by here.

  **Real email verification landed, Keycloak-native and non-blocking** — registration now creates
  accounts with `emailVerified: false` (`identity-service`'s `KeycloakAdminServiceImpl.createUser`)
  and triggers a real Keycloak "Verify Email" action-token email via the Admin API's
  `sendVerifyEmail`, rather than the old "created pre-verified" scope choice. Deliberately
  **non-blocking**: `Login.tsx`'s direct password grant (ROPC) still works immediately after
  registration, since no Keycloak `requiredActions` are set — a blocking design (Keycloak rejects
  ROPC token issuance outright while a required action is pending, no way to complete it in-band)
  would have meant migrating `Login.tsx` off ROPC onto Authorization Code + PKCE first, a much
  bigger change than adding verification. `Dashboard.tsx`'s existing (previously dead-linked)
  email-verification banner is the real UI now — its button calls the new
  `authApi.resendVerificationEmail()` (`POST /api/v1/auth/resend-verification-email`,
  authenticated) instead of navigating to `/verify-otp`; `navigate`/`useNavigate` were dropped
  from `Dashboard.tsx` entirely since that banner button was their only remaining call site.
  **The stale-claim caveat is handled now, not just documented** — verification status is a JWT
  claim (`email_verified`), stamped at token-issuance time, so a plain reload/tab-refocus alone
  never picks up a change. `Dashboard.tsx` has its own `useEffect` for this: while
  `!user.emailVerified`, it calls `authService.refreshAccessToken()` (a real `refresh_token` grant
  works even on a still-valid, unexpired access token — no need to wait for actual expiry) then
  re-fetches `/api/v1/auth/user`, both once immediately on mount and again on every
  `visibilitychange` back to `visible`. Two reasons it needs both triggers, not just one: the
  redirect below is often a **brand-new** tab/page load that never fires `visibilitychange` on its
  own (the immediate on-mount check covers this); the visibility listener covers the other real
  case — the email link opened in a separate tab, with the user coming back to an *already-open*
  Dashboard tab. Fully silent/best-effort — a failed check has no visible error, since the next
  trigger just retries. The listener detaches once `emailVerified` flips `true`, so this doesn't
  keep polling forever.

  **The same stale-claim fix now also applies to editing username.** `Dashboard.tsx`'s Personal
  Information fields are always-mounted `TextField`s (`InputProps.readOnly` toggled by `isEditing`,
  not swapped for `Typography` — this keeps the Paper's height invariant across the edit/view
  transition, see `docs/CHANGELOG.md`'s `[Unreleased]` entry for the layout history). `handleSave`
  compares the submitted username against `user.username` and, when it actually changed, calls
  `authService.refreshAccessToken()` right after `profileApi.updateProfile` succeeds — before that
  call, `identity-service` has already renamed the user in Keycloak itself (see
  `identity-service/CLAUDE.md`'s `UserService.updateProfile` note), but the browser's currently-held
  access token still carries the old `preferred_username` claim (stamped at issuance), which would
  otherwise get JIT-synced back into the local DB row on the very next authenticated request. Only
  called when the username actually changed, to avoid an unnecessary refresh-token grant on a plain
  firstName/lastName edit.

  **The verification email's link redirects to `/login?emailVerified=true`, not `/dashboard`** —
  deliberately, to handle a real edge case: a user who logs out between registering and clicking
  the emailed link would otherwise get bounced `/dashboard` → (`PrivateRoute`, unauthenticated) →
  `/login` anyway, landing on a bare login form with zero feedback that verification actually
  succeeded. `/login` handles both cases in one redirect target: `GuestRoute` (wrapping `/login`)
  now forwards its current query string when it redirects an already-authenticated caller on to
  `/dashboard`, instead of dropping it — so a still-logged-in user still gets bounced straight
  through, same as before, just without losing `?emailVerified=true` along the way. Both
  `Login.tsx` and `Dashboard.tsx` have a small `useEffect` reading that param via `useSearchParams`,
  showing a one-time `showSuccess(...)` confirmation toast and then stripping the param
  (`setSearchParams` with its updater-function form, `{ replace: true }`) so refreshing doesn't
  re-show it. See `identity-service/CLAUDE.md`'s `sendVerifyEmail` note for the server side of this.

  **`authService.startOAuth`/`AuthCallback.tsx` (Google/Facebook login) are fixed too** — no longer
  call `identity-service`'s dead `/api/v1/auth/oauth2/authorization/{provider}` endpoint. Now a
  third Authorization Code + PKCE flow reusing the same `gui` Keycloak client
  `adminAuthService`/`AdminLogin.tsx` already established (`OAUTH_CLIENT_ID`/
  `OAUTH_CALLBACK_PATH`/`OAUTH_PKCE_*` constants in `authService.ts`, distinct sessionStorage keys
  from the admin flow's `admin_pkce_*` so the two can't clobber each other), plus one addition:
  `startOAuth` appends `kc_idp_hint=<provider>` to Keycloak's `/auth` redirect so its hosted login
  page skips straight to the given identity provider instead of showing its own
  username/password form first. `AuthCallback.tsx` (`/auth/callback`, already in `gui`'s
  `redirectUris`) now does a real code-exchange via `authService.handleOAuthCallback` — mirrors
  `AdminAuthCallback.tsx` structurally (including its `hasRun` ref guard against StrictMode's
  dev-mode double-invoke consuming the one-time-use code/verifier twice) but with no `ADMIN`-role
  gate. Keycloak itself still brokers the actual Google/Facebook OAuth dance
  (`docker/keycloak/realm-export.json`'s `identityProviders`) — this app never talks to either
  provider directly, same as before. `authApi.ts`'s dead `exchangeStateToken` method (the old
  state-token-exchange approach `AuthCallback.tsx` used to call) was removed outright — no
  remaining callers. **Still needs a real Google OAuth Client ID/Secret plugged into
  `docker/keycloak/realm-export.json`'s (or the Admin Console's) `google` identity provider, and
  `enabled: true`, before the Google button actually works** — see
  `docker/keycloak/README.md`'s "Google/Facebook login" section for the exact steps (including the
  Google Cloud Console redirect URI to register). Facebook's identity provider is still disabled
  with placeholder credentials — the same code path covers it once it gets real credentials too,
  untested until then.
- Token storage keys live in `@shared/constants/storage.ts` (`STORAGE_KEYS`) — don't hardcode
  `localStorage` key strings elsewhere.
- When the backend renames a field (e.g. the `userId` → `userUuid` rename in `CHANGELOG.md`), the
  GUI has historically needed a matching rename across the relevant feature's `types.ts`, `api/*.ts`
  method, and any component reading that field directly — check `CHANGELOG.md` for whether a
  backend rename you're picking up already has a documented GUI-side counterpart before assuming
  it's still pending.
- A few pages were already large enough before this reorg to smell like God Components mixing
  data-fetching + view + local logic (`@ai/pages/EmbeddingsPage.tsx` ~512 lines,
  `@auth/pages/Dashboard.tsx` ~407, `@ai/pages/PipelineMetricsPage.tsx` ~352,
  `@content/pages/QuestionAnswerFormPage.tsx` ~351) — worth extracting a custom hook per page if
  you're touching one of these for a feature change anyway, but that's a finer-grained cleanup this
  reorg didn't attempt.
