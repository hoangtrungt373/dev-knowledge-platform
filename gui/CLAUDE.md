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
│   ├── tasks/           — personal task/project management, fronting task-service
│   └── ecommerce/        — ecommerce-service admin screens (Product Categories, Products incl.
│                            variant/image gallery management, Order Fulfillment) + pages/shop/
│                            (the public storefront: browse/search/filter, product detail) +
│                            pages/cart/, pages/checkout/ (Epic 2) + pages/orders/ (Epic 3's
│                            shopper-facing order history/detail, US-3.3/3.5/3.6)
├── app/          — app shell: App.tsx (routes), main.tsx, theme.ts, NavBar, GuestRoute/PrivateRoute,
│                    admin-shell/ (AdminLayout, AdminDashboard — the admin nav frame + landing page)
└── shared/        — httpClient, common.types-equivalent (types.ts, incl. PagedResponse), the
                      NotificationContext, storage.ts (STORAGE_KEYS), colors.ts, errorHandler.ts,
                      useSubmitGuard, ConfirmDialog
```

Each `features/<name>/` folder owns its own `api/`, `types.ts`, `pages/`, `components/`, `hooks/` —
whichever of those it needs; nothing is centralized by layer anymore (see "Why this shape" below).
Cross-directory imports use path aliases (`@shared/*`, `@app/*`, `@auth/*`, `@chat/*`, `@friends/*`,
`@messaging/*`, `@content/*`, `@ai/*`, `@tasks/*`, `@ecommerce/*` — defined in both `tsconfig.json`'s `compilerOptions.paths` and
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
- **`app/admin-shell/AdminLayout.tsx`'s sidebar is now collapsible, per request** (built alongside
  `@ecommerce/pages/ProductFormPage.tsx`'s two-column restructure — see that page's own note; the
  goal behind both was the same "give the form more room" ask). Still a MUI `Drawer` with
  `variant="permanent"` (unchanged — this is still not an overlay/slide-out drawer, the one other
  place in this app that references "AdminLayout's permanent sidebar" as its own precedent still
  holds), but its width is now `collapsed ? COLLAPSED_WIDTH (64) : EXPANDED_WIDTH (220)`, toggled
  by a `ChevronLeft`/`ChevronRightIcon` button in the sidebar's own header row. Collapsed state is
  **persisted to `localStorage`** (`adminSidebarCollapsed`) — a standing preference like a theme
  choice, not per-session UI state, so it should survive a reload; every other per-viewer
  convenience in this app that isn't shared/critical uses the same local-storage-not-Context
  pattern. **No matching change was needed on the main-content side** — this `Drawer` renders in
  normal document flow (`variant="permanent"` is not `position: fixed`), so `AdminLayout`'s main
  content `Box` (`flex: 1`) already reflows to fill whatever width the sidebar frees up; this is
  notably simpler than the classic MUI "persistent drawer + manually-synced `marginLeft` on the
  content" recipe, and don't reach for that heavier pattern if this ever needs revisiting. When
  collapsed: nav item labels, the "DKP Admin" brand text, and the user-info block's
  username/email all hide, leaving icon-only rows each wrapped in a `Tooltip` (`placement="right"`)
  so the icon's meaning isn't lost. This is a shared-shell change — affects all 14 routes under
  `AdminLayout` (11 page components, see `App.tsx`), not just `ProductFormPage`; verified via a
  clean `tsc --noEmit` and a successful `vite build` only, same as every other GUI change in this
  session — no Docker in this sandbox, so the actual collapse/expand interaction (and the
  persisted-preference reload behavior) hasn't been exercised in a real browser.
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
- **`@ecommerce` (admin CRUD for `ecommerce-service`'s Epic 1 — Product Categories, Products)** —
  fronts `ProductCategoryApi`/`ProductApi` through `gateway` (`GatewayRoutesConfig` already routes
  `/api/v1/admin/products/**`/`/api/v1/admin/product-categories/**`, so this feature uses the same
  `VITE_BACKEND_URL`/`httpClient` every other admin feature does — no third backend-origin
  constant needed, unlike `@messaging`'s direct-to-`social-service` case below).
  `pages/ProductCategoryListPage.tsx` + `components/ProductCategoryFormDialog.tsx` — a flat,
  unpaginated list (matching `ProductCategoryApi.list`'s own shape), no delete action since the
  backend exposes none. **Rebuilt to mirror `@content`'s hierarchical `CategoryListPage.tsx`/
  `CategoryFormDialog.tsx` instead of `TagListPage`/`TagFormDialog`** once `ProductCategory` gained
  parent/child hierarchy support (per request — see `ecommerce-service/CLAUDE.md`'s entity/service
  notes): the list page fetches both the flat list (`listProductCategories`, still what the table
  itself renders/searches) and the new `GET /tree` (`getProductCategoryTree`) in parallel, using
  only the tree to build an id→name lookup for a new "Parent" column (`category.parentId ?
  parentNameMap[...] : '—'`) — same `buildNameMap` helper as `@content`'s page, walking the tree
  once rather than re-deriving ancestry per row. The form dialog gained a "Parent category" `Select`
  (byte-identical `flattenTree`/`getSubtreeIds`/`collectSubtreeIds` helpers to `@content`'s
  `CategoryFormDialog`) — indented by depth, "None (root category)" for a root, and excluding the
  category's own subtree so an edit can't assign one of its own descendants as its new parent (the
  cycle guard the backend also enforces server-side; the GUI's exclusion is a UX nicety, not the
  actual guard). **Still no delete action** — unlike `@content`'s page, which does have one (its
  backend supports it) — this follow-up only added hierarchy, not delete; don't assume one exists.
  `types.ts`'s `ProductCategory` gained `parentId: number | null`, and a new
  `ProductCategoryTreeNode` type mirrors `@content`'s `CategoryTreeNode` exactly.
  `pages/ProductListPage.tsx` + `pages/ProductFormPage.tsx` mirror `@content`'s
  `QuestionAnswerListPage`/`FormPage`, but variant/image management is genuinely different between
  create and edit mode, not just a smaller version of the same form:
  - **Create mode**: variants are staged locally (a `DisplayVariant[]` with locally-generated
    string ids, not yet persisted) and submitted together with the basic fields in one
    `createProduct` call, since the backend requires ≥1 variant to create a product at all (US-1.6)
    — there's no way to create a variant-less product and add variants after the fact. Images
    can't be added yet either — uploading needs a real `productId`, which doesn't exist until
    after creation — so the create form shows a "save this product first" notice in the image
    gallery's place and, on success, navigates straight to the new product's own edit page rather
    than back to the list, so the natural next step (add images) is one click away.
  - **Edit mode**: variant add/remove and image upload/remove/reorder are independent, immediate
    API calls (`ecommerceApi.addVariant`/`removeVariant`/`uploadImage`/`removeImage`/
    `updateImageSortOrder`, each followed by a refetch of the whole product) — separate from the
    "Save" button, which only ever touches name/description/category via `updateProduct`. This
    mirrors `ProductApi`'s own shape: variants/images are independently mutable endpoints on the
    backend, not fields inside the update-basic-fields payload.
  - **Restructured into a two-column layout, per request, fixing three named complaints: the
    description editor felt too narrow, the sidebar couldn't free up space, and there was dead
    space on the right doing nothing.** Presented as an explicit layout choice via a preview-based
    question before building (two-column Shopify-admin-style vs. just widening the single column)
    — two-column was chosen. Root container's `maxWidth: 900` is gone entirely (was capping the
    *whole* page, description editor included); a new `Box sx={{ display: 'flex', gap: 3 }}` row
    holds a **Basic Info** column (`flex: '1 1 calc(68% - 12px)'`, `minWidth: 420` — Name +
    `ProductDescriptionEditor`, which is what actually needed the room) and a new, separate
    **Organization** column (`flex: '1 1 calc(32% - 12px)'`, `minWidth: 260` — just the Category
    `Select`, pulled out of the old single "Basic Info" `Paper`). Same `calc()`-gap-compensation
    flex technique `ProductDetailPage.tsx`'s own two-column gallery+info split already established
    (see that page's own note and `gui/CLAUDE.md`'s general note on why bare percentages plus a
    `gap` always over-wrap) — the two subtractions (`12px`/`12px`) sum to exactly the `gap: 3`
    (24px) between the columns. Variants and the Image Gallery/placeholder stay full-width **below**
    both columns, not squeezed into either one — matches the approved layout exactly, since neither
    component is a simple field that fits a sidebar-width column. **This needed a matching change to
    the shared shell, not just this page** — see `AdminLayout.tsx`'s new collapsible sidebar note
    below; the two changes were requested and built together, since a wider form and a narrower
    sidebar are the same underlying "give the form more room" goal.
  - **`components/ProductDescriptionEditor.tsx` — new, replacing the plain multiline `TextField`
    both create/edit modes used for `description`, per request (Phase 2 of the accepted
    sanitized-HTML plan — see `ecommerce-service/CLAUDE.md`'s `ProductDescriptionSanitizer` note
    for Phase 1 and the Markdown-vs-HTML discussion behind it).** TipTap-backed
    (`@tiptap/react`+`@tiptap/starter-kit`+`@tiptap/extension-link`+`@tiptap/extension-image`+
    `@tiptap/extension-underline`, all new dependencies, `^3.30.5`) — a real WYSIWYG editor, not a
    Markdown Edit/Preview toggle like `@content`'s `MarkdownField.tsx`, since a shopper-facing
    description needs inline images/spec layout Markdown can't produce (see that earlier
    discussion). **`StarterKit.configure({ horizontalRule: false, codeBlock: false })`
    deliberately disables those two nodes** — `ecommerce-service`'s
    `ProductDescriptionSanitizerTest` confirmed `<hr>` is dropped entirely and `<pre><code>`
    degrades to a bare inline `<code>` by the backend allowlist, so offering either in the toolbar
    would silently misrepresent what actually gets saved; everything else `StarterKit` enables by
    default (paragraph/headings/bold/italic/strike/lists/blockquote/hard break/undo-redo), plus
    `Link`/`Image`/`Underline` added on top, was verified to survive that same allowlist
    untouched. Toolbar is a small MUI `IconButton` row (`editor.isActive(...)` drives each
    button's active/primary color), not a component library's own toolbar — this app has no rich-
    text-editor UI kit dependency, and a handful of `IconButton`s was less surface than pulling
    one in. **Deliberately uncontrolled** — `value` only seeds `useEditor`'s `content` once and is
    re-applied via `editor.commands.setContent` exactly once more when `ProductFormPage`'s
    async `loadProduct` resolves in edit mode (`editor.isEmpty` guards against clobbering
    in-progress typing on a second render); `onChange` is how the parent form field learns about
    every edit afterward — a plain controlled `value` prop re-rendering TipTap's own internal
    ProseMirror document on every keystroke would fight the editor and lose cursor position, the
    same reason no rich-text editor is ever built as a fully controlled component.
    **`ProductFormPage.handleSubmit` needed a "has this actually got content" guard** — TipTap's
    "empty" document serializes as `"<p></p>"`, never `""`, so the old plain
    `description.trim() || undefined` omit-if-empty check (correct for the old `TextField`) would
    have sent that markup to the backend instead of omitting the field. Originally a local helper
    in this file; **moved to `utils/htmlContent.ts` (`hasVisibleHtmlContent`) once
    `ProductDetailPage.tsx`'s Phase-3 read side needed the exact same check** (strips tags, checks
    for either visible text or an `<img>` — an image-only description with no caption typed is
    still a real description) — see that page's own note. New npm dependencies pushed the
    production bundle from ~1.7 MB to ~2.1 MB gzip (539 KB → 672 KB) — ProseMirror (TipTap's
    underlying editor engine) is not small;
    not addressed here (code-splitting this page's editor behind a dynamic `import()` would be the
    fix, flagged as a follow-up, not done as part of this feature). Verified via a clean
    `tsc --noEmit` and a successful `vite build` only — no Docker in this sandbox, so this hasn't
    been exercised in a real browser; test the golden path (typing, each toolbar button, a real
    image upload, save, reload) before trusting it end-to-end.
    - **Post-Phase-3 follow-up: the "Image" toolbar button uploads a real file instead of
      prompting for a URL, per request.** A hidden `<input type="file" accept="image/*">`
      (`imageFileInputRef`), triggered by the toolbar `IconButton`'s own click (styling a native
      file input directly is awkward across browsers, so the visible button just proxies a click
      to a hidden real one) — `handleImageFileSelected` uploads via the new
      `ecommerceApi.uploadDescriptionImage(file)` and inserts the returned **permanent** URL;
      `uploadingImage` state disables the button and swaps its icon for a small `CircularProgress`
      while the request is in flight, and `showError` (added to this component for the first time
      — it had no error path before) surfaces an upload failure the same way every other admin-GUI
      upload does. Deliberately **replaces** the old `window.prompt('Image URL')` flow rather than
      offering both — that's how the request ("not pass the image url") was read. This needed a
      matching backend piece: `ecommerce-service`'s new
      `POST /api/v1/admin/products/description-images/upload` returns a **permanent** URL, not the
      gallery upload's presigned one — see `ecommerce-service/CLAUDE.md`'s
      `ProductDescriptionImageService` note and `infra/CLAUDE.md`'s `StorageService.uploadPublicImage`
      note for the full reasoning (a presigned URL baked into stored description HTML would
      silently expire, since nothing re-resolves `description` on read the way `ProductMapper`
      re-resolves gallery images) — this was worked through with the user in detail, including why
      the product gallery itself deliberately stays presigned-only, before building it.
    - **Further follow-up: pasting or dragging-and-dropping an image file into the editor now
      works too, per request** (asked directly: "does it work if the user copy paste the image" —
      answer was no, verified rather than assumed, then built). New
      `useEditor`'s `editorProps.handlePaste`/`handleDrop` — plain ProseMirror hooks TipTap passes
      straight through (`@tiptap/pm/view`'s `EditorView`), not a TipTap-specific API. Both check
      for at least one `image/*` file on the clipboard/drop event; if none, return `false` and let
      ProseMirror's default handling take over completely unchanged (a normal paste, or
      `handleDrop`'s own `moved` flag for an internal drag-reorder within the editor, both pass
      through exactly as before this existed). When there is an image file, both upload it through
      the identical `ecommerceApi.uploadDescriptionImage` path the toolbar button uses, via a new
      shared `uploadAndInsertImageAt(view, pos, file)` helper — **inserted via a raw
      `view.dispatch(view.state.tr.insert(pos, node))`, not `editor.chain().setImage(...)`**,
      because `handlePaste`/`handleDrop` only ever receive the low-level ProseMirror `view`, not
      the higher-level `editor` instance (referencing `editor` from inside the very `useEditor(...)`
      call that constructs it would be a chicken-and-egg problem — it doesn't exist yet). The
      toolbar button's own `handleImageFileSelected` was refactored onto this same shared helper
      too, so there's exactly one upload-and-insert code path now, not two. **Deliberately doesn't
      intercept an externally-hosted `<img src="https://...">` arriving as part of pasted HTML**
      (e.g. right-click-copying an image from a webpage, which some browsers represent as HTML
      with a live external URL rather than an actual file) — that URL is left as-is, subject to
      the same `LINKS`/`IMAGES` protocol check the sanitizer already applies to any other pasted
      link/image; re-hosting an arbitrary external image would need a real server-side
      fetch-and-reupload proxy, a separate feature not built here. Before building this, confirmed
      *why* it mattered by testing what happens if a pasted image ever did reach the editor as an
      inline base64 `data:` URI with no upload involved: new
      `ProductDescriptionSanitizerTest.stripsDataUriImageSourcesEntirely` (`ecommerce-service`, now
      165 unit tests total) shows the backend strips a `data:` `src` down to nothing on save — so a
      `data:`-URI image could never have survived being saved anyway, confirming this needed a real
      upload path and not just "let the browser's default paste behavior do something." Verified
      via a clean `tsc --noEmit` and a successful `vite build` only, same caveat as everything else
      in this feature — no Docker in this sandbox, so the actual paste/drop interaction hasn't been
      exercised in a real browser.
    - **Further follow-up: the content area's `maxHeight: 400`/`overflowY: 'auto'` was removed
      entirely, per request** — a capped, internally-scrolling editor hid part of whatever the
      admin had written, with no visual cue that more content existed below the fold. Considered
      alongside an alternative (reordering Variants/Images above the description) and rejected
      that alternative explicitly: reordering doesn't touch the editor's own fixed-height scrollbox
      at all, so it wouldn't have fixed the actual complaint on its own. The editor now grows to
      fit its content and lets the page itself scroll — one scrollbar, not a scrollbar nested
      inside a scrollbar — which is also the standard pattern real WYSIWYG editors use in a form
      (Shopify's product description, Notion, Google Docs), not just a stopgap. `minHeight: 160`
      is unchanged, still guarantees a reasonable empty-state click target.
  - `components/ProductVariantEditor.tsx` + `ProductVariantDialog.tsx` — the add-variant dialog's
    attribute key/value editor locks its key set to whatever the product's first variant already
    uses once one exists, enforcing US-1.6's "every variant shares the same attribute keys" rule
    client-side before ever hitting the backend's own check. The remove action is disabled
    client-side whenever only one variant remains, for the same reason (the backend rejects
    removing a product's last variant; no point round-tripping to learn that).
  - `components/ProductImageGallery.tsx` — upload via `httpClient.postForm` (same pattern as
    `@auth/api/profileApi.ts#uploadAvatar`), remove, and a real move-earlier/move-later reorder.
    **The reorder is a 3-step scratch-sort-order swap, not a direct 2-step swap** — the backend
    rejects a sort order that collides with any of the product's *other* images
    (`UpdateProductImageSortOrderRequest`, `UNIQUE (PRODUCT_ID, SORT_ORDER)`), so swapping two
    adjacent images' sort orders directly would have the second `PATCH` collide with the first
    image's not-yet-moved-away value for the instant before it moves; moving the first image to an
    unused scratch value (`SCRATCH_SORT_ORDER = 100000`, well outside any real gallery's range)
    first sidesteps that. `ProductImage.url` (a time-limited presigned URL, resolved server-side by
    `ProductMapper` — see `ecommerce-service/CLAUDE.md`) is what actually renders as each
    thumbnail; never construct a MinIO URL client-side from `storageKey`.
  - **`pages/shop/` — the public storefront (US-1.1–1.4), the app's first genuinely public feature
    besides `/login`/`/signup`.** Every other feature in this app sits behind `PrivateRoute`;
    `/shop`/`/shop/:slug` are plain, ungated routes in `App.tsx`, matching
    `ecommerce-service`'s own `permitAll` `/api/v1/public/products/**` — browsing works identically
    logged in or out, and `httpClient` already omits the `Authorization` header when there's no
    token, so no special-casing was needed there. `NavBar.tsx`'s "Shop" button is the one nav entry
    rendered unconditionally, outside the `isAuthed`/`!isAuthed` branches every other button lives
    in.
    - `ShopPage.tsx` — category rail (via a **new** `shopApi.listCategories()`, hitting a **new**
      backend endpoint `PublicProductCategoryApi` at `/api/v1/public/product-categories`; the
      existing `ecommerceApi.listProductCategories()` hits the admin-gated endpoint and would 403
      for a non-admin shopper) + price/in-stock filters + a dynamic attribute-facet panel + a
      paginated product grid (`components/shop/ProductCard.tsx`). **Attribute facet options are
      built from whatever's on the currently-loaded page of results, not the whole category** —
      there's no "every attribute value in this category" endpoint, so switching pages can reveal
      different facet options. A known, page-scoped approximation, not a bug; don't "fix" it by
      assuming a missing facet means that value doesn't exist anywhere in the category.
    - **`ProductDetailPage.tsx`** — the page container is `width: '80%'` (was a fixed
      `maxWidth: 1100`), per request, alongside the same change on `CartPage.tsx` and the
      `orders/` pages below, so all four ecommerce pages scale with viewport width instead of
      capping at a fixed pixel value. **Gained an MUI `Breadcrumbs` trail (`Shop → {categoryName}
      → {product.name}`), replacing the old plain "Back to Shop" button, per request** — the
      3-segment "flat" version discussed and chosen over a full multi-level category hierarchy
      (e.g. `Home & Living > Furniture > Outdoor furniture`): this app's `ProductCategory` is a
      flat taxonomy (see `ecommerce-service/CLAUDE.md`), a product only ever has *one* category,
      not a tree, so a deeper breadcrumb isn't something the current data model can produce without
      first adding real category hierarchy support (a backend data-model change, out of scope
      here). `Shop` links to `/shop` (`Link component={RouterLink}`); the category segment is
      **deliberately plain text, not a link** — `Product`/`ProductSearchResult` only carry
      `categoryName` (a string), never a `categoryId`, and `ShopPage`'s own category filter is
      driven by id, not name, with no URL-param support to pre-select it either, so there's no
      route this segment could correctly link to without inventing one; the product name segment
      is plain text too, being the current page. No new dependency — `Breadcrumbs`/`Link` are both
      `@mui/material` core. **`ProductCategory` gained real parent/child hierarchy support in a
      later follow-up** (see `ecommerce-service/CLAUDE.md`), and this breadcrumb was then **deepened
      to a real root→leaf ancestor trail, per request** — the "flat taxonomy" 3-segment version
      above is now history. New `utils/categoryPath.ts` (`buildCategoryPath(categories,
      categoryId)`) walks a flat `ProductCategory[]`'s `parentId` chain client-side (no dedicated
      "category path" endpoint — the flat list `shopApi.listCategories` already returns carries
      `parentId` now, so a `Map<id, category>` + upward walk is enough; a `seen` guard makes an
      unexpected cycle a no-op rather than an infinite loop, defense in depth only since the
      backend already rejects a cyclic `parentId` on write). The page fetches that flat list via a
      **second, independent** `useEffect`/`shopApi.listCategories()` call (parallel to the product
      fetch, not gating the page's own `loading` state) with a **silently swallowed** failure —
      deliberately no `showError` wired to this call, since the breadcrumb is a cosmetic
      enhancement and a toast over it would read as a real error to a shopper; a failed/still-
      loading fetch just falls back to the original single `product.categoryName` segment (the
      exact fallback this breadcrumb always rendered before hierarchy existed). Each ancestor
      segment stays **plain text, not a link** — same reasoning as before, unchanged: `ShopPage`'s
      category filter is still id-driven with no URL-param support to pre-select it, so there's
      still no route an ancestor segment could correctly link to. **Gained a "Product Details"
      article section below the gallery+info card, per request — Phase 3 of the accepted
      sanitized-HTML plan (see `ecommerce-service/CLAUDE.md`'s `ProductDescriptionSanitizer` note
      for Phases 1–2).** Renders `product.description` via `dangerouslySetInnerHTML`, but only
      after a client-side `DOMPurify.sanitize()` pass — this is **defense in depth on top of**
      `ProductDescriptionSanitizer`'s own on-write sanitization, not a substitute for it; the
      comment directly above the render call says so explicitly, so a future edit doesn't "simplify"
      this back to raw `product.description`. The section is omitted entirely (not rendered as an
      empty card) when there's nothing to show — new `utils/htmlContent.ts`
      (`hasVisibleHtmlContent`) is the same helper `ProductFormPage.tsx`'s submit guard uses (see
      its own note below), extracted to a shared util once both the write side and this read side
      needed the identical "TipTap's empty document is `<p></p>`, not `""`" check. Typography
      overrides on the rendered content (`& p`/`& h1`–`& h6`/`& ul`/`& blockquote`/`& table`/etc.)
      mirror `ProductDescriptionEditor.tsx`'s own content-area styles, so a description looks the
      same while being edited as it does once published — deliberately not extracted into a shared
      style object across the two files, since MUI's `sx` prop shape doesn't share cleanly between
      an editable ProseMirror surface and a `dangerouslySetInnerHTML` block without more
      abstraction than the ~15 lines of duplication was worth. New `dompurify` dependency
      (`^3.4.14`) — **no `@types/dompurify`**, since `dompurify` ships its own TypeScript types as
      of this version (confirmed before adding it — the separate `@types/dompurify` package is the
      older, now-redundant stub). Verified via a clean `tsc --noEmit` and a successful `vite build`
      only — no Docker in this sandbox, so this hasn't been exercised in a real browser; this
      closes out all 3 phases of the `Product.description` rich-content feature.
      The gallery+info row sits in its own `bgcolor:
      'background.paper'` card (`borderRadius: 2, p: 3`), per request, distinct from the page's own
      grey `background.default` behind it — `background.paper` rather than a hardcoded white so a
      future dark theme gets the right dark surface color automatically instead of a stuck-white
      box. Image gallery (main image + prev/next slide arrows + thumbnail strip) on the left,
      **`flex: '1
      1 calc(45% - 12.8px)'`**; on the right, a Shopee-referenced info panel (explicitly modeled on
      that layout, per request — this app still has no purchased/named template, this is just the
      closest real-world reference point for this one page's arrangement), **`flex: '1 1
      calc(55% - 19.2px)'`** — a fix per request, wider than the gallery since the info column
      (price box, row-layout variant picker, two large buttons) needs more horizontal room than an
      even split gave it; both sides keep `minWidth: 320` so the parent's `flexWrap: 'wrap'` still
      stacks them on a narrow viewport. **The `calc()` subtraction is load-bearing, not
      decoration**: the parent's `gap: 4` (32px) sits on top of the flex-basis sum, and
      flex-wrap's line-breaking decision is made against each item's *hypothetical* (pre-shrink)
      basis size — so bare percentages summing to 100% plus the 32px gap always overflow the line
      and force an unwanted wrap to two rows, regardless of how much the items could actually
      shrink to fit. The two `calc()` subtractions (12.8px / 19.2px) sum to exactly 32px, so the
      pre-shrink total comes back down to `100% - 32px`, matching the space the gap actually
      consumes — the two columns lay out side-by-side and only wrap once `minWidth: 320` genuinely
      can't fit both. Don't "simplify" this back to bare percentages — it reintroduces the
      always-wraps bug. **The main image has prev/next slide arrows, per request** —
      `ChevronLeftIcon`/`ChevronRightIcon` `IconButton`s absolutely positioned over the image's
      vertical center (`position: 'relative'` added to the image `Box` to anchor them), calling a
      `slideBy(delta)` helper that wraps `activeImageIndex` modulo `sortedImages.length` in either
      direction. Manual-only, no auto-advance — chosen over auto-advance or swipe/drag after asking
      (auto-advance needs a pause-on-hover timer for a feature product galleries rarely auto-play;
      swipe/drag needs gesture handling and is less discoverable on desktop) — and the existing
      thumbnail strip stays alongside it unchanged, still able to jump straight to any image; the
      arrows only add prev/next stepping over the same `activeImageIndex` state, nothing removed.
      Both arrows render only when `sortedImages.length > 1`, same guard the thumbnail strip
      already used. **Also overlaid: a row of small dot indicators, bottom-center of the main
      image** — one per image, filled `primary.main` for the current `activeImageIndex` and
      `background.paper` otherwise, clickable to jump straight to that image (same
      `setActiveImageIndex` the thumbnail strip already calls) — a third, purely visual way to see/
      change slide position alongside the thumbnails and arrows, sharing the same
      `sortedImages.length > 1` guard. **The category chip and product description are deliberately not shown
      here anymore** — a fix per request; both move to a future "article" section rendered below
      the info panel instead (not yet built — see the `product.description`/`categoryName` fields
      still on `Product`, just unused on this page for now). Panel content, top to bottom: title
      (`h5 fontWeight={400}`) → **a rating/sold-count/report row** (below, new) → price box → 
      divider → `VariantSelector` (`layout="row"` — see below) → a `Quantity` label/stepper/
      availability-text row → an `Add to Cart` + `Buy Now` button row.
      - **The rating/sold-count/report row is entirely faked data, per explicit request** — a
        module-level `FAKE_RATING = 4.9` / `FAKE_RATING_COUNT = 79` / `FAKE_SOLD_COUNT = 1000`,
        identical on every product, standing in for a real reviews/order-analytics backend that
        doesn't exist yet (Epic 5). Renders `{rating} <MUI Rating readOnly precision={0.1}>` |
        `{ratingCount} Ratings` | `{soldCount} Sold` (`Divider orientation="vertical" flexItem`
        between each), then a right-aligned (`flexGrow: 1` spacer) `Report` button —
        `startIcon={<FlagOutlinedIcon>}`, `disabled` with a "Coming soon" tooltip, same
        not-a-working-shortcut treatment as `Buy Now` below, since no report flow exists either.
        **When real ratings/sold-count land, replace the three constants with real
        `product`-sourced values — don't leave the fake numbers behind once real data exists.**
      - **Price box** — still a distinct, higher-emphasis block below that row, Shopee-style: a
        full-width `bgcolor: 'action.hover'` box (a fix per request — was `display: 'inline-block'`
        sized to the price text; `Box`'s default `display: 'block'` already fills the panel's width
        once `inline-block` is dropped) holding the price in `h4 fontWeight={400} color="error.main"`
        — bigger than the `h5` title, red, but not bold (a fix per request; both this and the title
        used to be much heavier, `700`/`800`, and read as too bold).
      - **Availability text lives in the quantity row, immediately after the stepper, not next to
        the price** — a fix per request (briefly tried right-aligned via a `flexGrow: 1` spacer,
        reverted to sitting right next to the stepper per follow-up). Three states: before a
        variant is resolved, a plain **"IN STOCK"**/"Out of stock" (`inStockDisplay`'s "any variant
        in stock" fallback — different variants have independent stock, so nothing more specific is
        knowable yet); once resolved, "N items available" or `utils/stock.ts`'s shared
        warning-colored "Only N left in stock!" (`isLowStock`/`lowStockMessage`,
        `LOW_STOCK_THRESHOLD = 5`, shared with `CartPage` below so the threshold/wording can't
        drift between the two places a shopper sees remaining stock), or "Out of stock" if that
        exact variant has none left. No separate binary in-stock/out-of-stock chip exists anymore,
        this replaced it.
      - **The quantity stepper's `+` button and manual entry both cap at the selected variant's own
        `stockQuantity - reservedQuantity`, minus whatever quantity of that exact variant is
        already in the shopper's own cart** — a fix (the cart-subtraction part; the raw
        stock/reserved cap already existed). Without it, a shopper who'd already added, say, all
        14 in-stock units of a variant to their cart could still open this page and add more —
        add-to-cart never reserves stock against `ProductVariant` (only checkout's `confirm` does,
        via `reserve`, per this app's own locked Epic 2 design), so `stockQuantity -
        reservedQuantity` alone doesn't reflect what the *cart* has already claimed. New
        `quantityAlreadyInCart` reads `useCart().cart?.lines.find(l => l.variantId ===
        selectedVariant.id)?.quantity ?? 0`; `availableForSelectedVariant` is now
        `Math.max(0, stockQuantity - reservedQuantity - quantityAlreadyInCart)`. Purely a
        client-side UX improvement, not a correctness fix at the data layer — the real oversell
        guard is still `CheckoutServiceImpl.confirm`'s atomic `reserve()` call (see
        `ecommerce-service/CLAUDE.md`), which this page's cap can never bypass; the point here is
        just not letting the shopper *think* they can add more than what's really left, only to
        find out at checkout. **Does not** account for other shoppers' carts (there's no such
        endpoint — reservedQuantity only tracks Epic 3's actual checkout-time reservations), so
        this remains a per-shopper approximation, same as `stockQuantity - reservedQuantity`
        always was. Resets to 1
        whenever the selected variant changes (a quantity picked for a different variant shouldn't
        silently carry over). Its `TextField` (`variant="standard"`, `disableUnderline`) sets
        `inputProps.style.padding: 0` on the raw `<input>` — a fix; the standard variant's default
        input padding is asymmetric top/bottom, which visibly shifted the number a bit higher than
        the flanking `−`/`+` `IconButton`s' centered glyphs.
      - **`Add to Cart` is `size="large"` plus explicit `px`/`py`/`fontSize` padding** — a fix; the
        default button read as too small to be the page's primary action. `Buy Now` sits next to it
        (`variant="outlined"`, same large sizing) but is **`disabled` with a "Coming soon" tooltip**
        — no "skip the cart, go straight to checkout" flow exists yet; it's rendered per the
        Shopee-style layout's own two-button row rather than omitted, but deliberately not left
        looking like a working shortcut that silently does nothing. Wrapped in a `<span>` per MUI's
        own requirement for a `Tooltip` on a `disabled` button. Renders "Log in to buy" as a single
        button in this row's place instead of both, unchanged, when
        `authService.isAuthenticated()` is false — Epic 2 is authenticated-only, no guest cart, so
        this avoids ever attempting the call at all rather than relying on `httpClient`'s 401
        fallback (same reasoning `useFriendRequestsCount`'s own auth guard already documents).
    - `api/shopApi.ts` — a separate file from `ecommerceApi.ts` (admin CRUD), mirroring the
      backend's own `ProductSearchApi`/`ProductApi` split. `getBySlug` reuses the existing `Product`
      type from Phase B/admin (same `ProductResponse` shape); `search` uses a **new**
      `ProductSearchResult` type (mirrors `ProductSearchResponse` — a genuinely different,
      denormalized shape, not reusable with `Product`).
  - **`pages/cart/CartPage.tsx` + `pages/checkout/CheckoutPage.tsx` — Epic 2's Cart & Checkout
    GUI (US-2.1–2.7), both `PrivateRoute`-gated (`/cart`, `/checkout`) unlike the public `/shop`
    routes above, since this epic is authenticated-only with no guest cart.** Discussed and decided
    with the user before building, same as the storefront's own template discussion: a **dedicated
    `/cart` page**, not a slide-out drawer (this app has zero drawer precedent — only
    `AdminLayout`'s *permanent* sidebar — and every other feature here is a full page), and a
    **single-page checkout with stacked sections** (order summary → address form → place-order
    button), not an MUI `Stepper` wizard (no wizard precedent exists anywhere in this app either,
    and the backend itself is a two-call `preview`/`confirm` flow, which a single page maps onto
    directly with no step-state to manage).
    - `context/CartContext.tsx` (`CartProvider`/`useCart`) — global cart state, same shape as
      `NotificationContext`/`StompConnectionContext`, provided in `App.tsx` (wrapping
      `StompConnectionProvider`, inside `NotificationProvider`). One source of truth so the
      NavBar's item-count `Badge` and every cart-touching page share the same data without each
      holding its own copy; `addItem`/`updateItem`/`removeItem` all set local state directly from
      the mutation response (the backend's own "every mutating endpoint returns the updated cart"
      contract — see `ecommerce-service/CLAUDE.md`), no extra refetch needed. **`changeVariant`
      (added for the cart's inline variant switcher, see below) is the one exception to "call the
      API, `setCart` the response"**: it makes two backend calls (`addItem` the new variant, then
      `removeItem` the old one, since `CartApi` has no dedicated swap endpoint) but deliberately
      calls `setCart` only once, after the second — calling it after the first too (the naive
      "just call the existing `addItem`/`removeItem` back to back" approach, tried first) renders
      the transient state where both the old and new variant's lines exist at once, which reads as
      a new line flickering in and immediately back out right as the swap completes. **The initial fetch
      only runs once, on mount, and does not react to login** (a client-side `navigate()` after
      login doesn't remount the provider) — `Login.tsx`/`SignUp.tsx`/`AuthCallback.tsx` all call
      `refresh()` explicitly right after a successful login as a result, and `NavBar`'s logout
      handler calls `clear()` in the opposite direction. `NavBar.tsx` calls `useCart()`
      unconditionally (Rules of Hooks — the provider always wraps it) but only renders the Cart
      button inside the existing `isAuthed` block, same as every other authenticated nav entry.
    - `api/cartApi.ts` (`getCart`/`addItem`/`updateItem`/`removeItem`) and `api/checkoutApi.ts`
      (`preview`/`confirm`) — two files, mirroring the backend's own `CartApi`/`CheckoutApi` split
      and this feature's existing admin-vs-public file-per-concern convention.
    - `types.ts` gained `CartLine`/`Cart`/`Address`/`OrderLine`/`CheckoutPreview`/
      `OrderConfirmation`, mirroring `CartResponse`/`CartLineResponse`/`AddressRequest`/
      `AddressResponse`/`OrderLineResponse`/`CheckoutPreviewResponse`/`CheckoutConfirmResponse`
      field-for-field (the backend's `@JsonInclude(NON_NULL)` omission of an unavailable line's
      fields beyond `variantId`/`quantity`/`available` is why those fields are optional on
      `CartLine`, not because they're ever optional on an available line).
    - `CartPage.tsx` — the page container is `width: '80%'` (was `maxWidth: 800`), per request —
      see `ProductDetailPage.tsx`'s note above, same change across both pages plus `orders/` below.
      **Two separate `bgcolor: 'background.paper'` cards, not one** (`borderRadius: 2`, `mb: 2`/`3`
      respectively) — the select-all/Delete-Selected header row is its own smaller card (`p: 2`),
      and the lines list + divider + subtotal is a second, separate card (`p: 3`) below it. A fix
      per request: these two used to share one card with no visual break between the header row
      and the first line; splitting them into two lets the page's own grey `background.default`
      show through the gap between them (the outer page `Box` never had its own paper `bgcolor` to
      begin with — only the inner content did, so nothing needed removing there, just splitting).
      Both use the same semantic-token reasoning as `ProductDetailPage`'s card (not a hardcoded
      white, so a future dark theme gets the right dark surface automatically). The "Your Cart"
      title above and the Continue Shopping/Checkout button row below both stay outside either
      card, same as `ProductDetailPage` keeps its own breadcrumb trail outside its card (that page
      used to have a plain "Back to Shop" button in this same spot — replaced by the breadcrumb
      trail, below, since a "Shop" crumb already covers the same "go back" action).
      **The header row's checkbox-to-text `Stack` uses `spacing={2}`, matching each
      `CartLineRow`'s own checkbox-to-thumbnail gap** — a fix per request; it was `spacing={1}`
      (8px), one size down from the 16px gap the per-line `Stack`'s own `spacing={2}` puts between
      its `Checkbox` and the product-image `Link`, so "Select all"/"N selected" sat 8px left of
      where each line's thumbnail starts. Both now line up on the same left edge.
      **Rows are separated by a horizontal `Divider` instead of each having its own border box** —
      a fix per request; the lines-list `Stack` now passes `divider={<Divider />}` (MUI's own
      built-in separator-between-children prop) rather than each `CartLineRow` drawing its own
      `border: '1px solid', borderColor: 'divider'` box (both the available and unavailable-line
      variants had one; both dropped it and switched from `p: 2` to `py: 2`, since there's no
      longer a box edge to pad against horizontally — the card around the whole list already
      provides that via its own `p: 3`).
      **Multi-select (post-Epic-2 follow-up, Phase 3 of 3) — bulk delete + "checkout only the
      selected items," per request; the last of three phases (Phase 1: backend bulk-remove
      primitive; Phase 2: backend `selectedVariantIds` on checkout; both in
      `ecommerce-service/CLAUDE.md`).** `selectedVariantIds` is a `Set<number>` in `CartPage`'s own
      state — starts empty on every visit, not restored/persisted across reloads; every
      `CartLineRow` (available and unavailable alike, since a bulk *delete* is just as sensible on
      a stale line as a live one) gets a leading MUI `Checkbox` bound to it. A header row above the
      list holds a "select all" `Checkbox` (checked when every line is selected, `indeterminate`
      when some but not all are) plus a "Delete Selected (N)" button that only renders once
      `selectedVariantIds` is non-empty — calls the new `CartContext.removeItems` (below), clears
      the selection on success, same per-request `showError` pattern every other mutation here
      uses. **The "Proceed to Checkout" button becomes "Checkout Selected (N)" once at least one
      *available* selected line exists** (`selectedAvailableVariantIds` — unavailable lines can be
      selected for bulk delete but can never be part of a checkout attempt, so they're filtered
      out here regardless of their checkbox state) — clicking it `navigate`s to `/checkout` with
      `state: { selectedVariantIds: selectedAvailableVariantIds }`; with nothing selected, the
      button reads "Proceed to Checkout" and navigates with no state at all, checking out the
      whole cart exactly as before this feature existed (`CheckoutPage.tsx`'s own note above
      covers the receiving end). `CartContext` gained `removeItems(variantIds)` — a straight
      pass-through to the new `cartApi.removeItems` (`POST /api/v1/cart/items/remove-batch`, not
      `DELETE` with a body — see `ecommerce-service/CLAUDE.md`), same "call the API, `setCart` the
      response" shape every other `CartContext` mutator already follows. One row per `CartLine`; an unavailable line renders
      grayed out with a "No longer available" chip and only a Remove control (mirrors the
      backend's own
      available-flag contract, same shape `CartLineResponse`'s Javadoc documents). Each available
      line renders a 64×64 shared `components/Thumbnail.tsx` (below) from `CartLine.primaryImageUrl`
      (a new field — `CartMapper` resolves it server-side from the product's own gallery, same
      presigned-URL pattern `ProductCard.tsx`'s storefront thumbnail uses — both now go through
      this same shared component), falling back to the same `ImageNotSupportedIcon` treatment when
      a product has no images yet; an unavailable line renders the same thumbnail slot with no
      image (nothing to show — the line only carries `variantId`/`quantity`), keeping row height
      consistent either way. **Passes `fade`** (`opacity` 0→1, 200ms) rather than popping in the
      instant the browser finishes decoding — added once the inline variant switcher (below) made
      the underlying blink visible: switching variants remounts the row (keyed by `variantId`), so
      a brand-new `<img>` node mounts every time, and `primaryImageUrl` is a freshly re-signed
      presigned URL on every cart fetch (even for the *same* underlying picture across two variants
      of one product), so the browser can never serve it from cache. The fade can't remove that
      network round trip, only smooth over it — `OrderLineRow`'s own use of the shared component
      doesn't pass `fade`, since its rows don't remount on the same kind of hot-swap this page's
      variant switcher causes. Quantity
      +/- buttons call `updateItem` directly (immediate mutation, no local staging) — the "−"
      button disables at quantity 1 rather than silently removing the line on decrement, since a
      dedicated Remove button already covers that action explicitly. **The "+" button also disables
      once `line.quantity` reaches `line.availableQuantity`** (a fix — it used to have no upper
      bound, same gap `ProductDetailPage`'s own stepper had, see above) — `availableQuantity` is a
      new, nullable `CartLineResponse` field (`stockQuantity - reservedQuantity` for that line's
      variant, added specifically for this; omitted, like every other field beyond
      `variantId`/`quantity`, when the line is unavailable) — and a low-stock line reuses the same
      `utils/stock.ts` helpers `ProductDetailPage` does, rendered as a caption **below the
      quantity stepper box** (a fix per request — used to sit under the unit-price line instead;
      the stepper `Stack` is now wrapped in an outer column `Stack` so the caption stacks directly
      beneath the `−`/qty/`+` box rather than the product-name column). **The quantity column
      (`sx={{ width: 160, flexShrink: 0 }}`) is now a fixed width — the actual fix, after two
      narrower attempts.** The column's own `Stack` is shrink-to-fit by default (sized to its
      widest child); since the low-stock caption only rendered conditionally, a low-stock row's
      column was wider than a normal row's (caption text vs. nothing), and `alignItems="center"`
      then centered the narrower stepper box within whatever width the column happened to be that
      row — shifting it left/right, row to row, exactly matching the mechanism reported. A first
      pass made the caption always render (`visibility: 'hidden'` with placeholder text when not
      low stock) to stabilize the column's *height*, and a second tried `alignItems="flex-start"`
      to stop the stepper re-centering within a still-variable *width* — reverted as insufficient,
      since the column's width still varied by caption message length (row to row) even once
      something always rendered, and that variability could also nudge the outer row's own flex
      distribution among siblings. Pinning the column to a fixed width removes the variability at
      its source rather than just changing how content aligns within it: `alignItems="center"` and
      the always-rendered caption were kept, now safe since the box they sit in never resizes.
      A per-row `pending` flag
      (keyed by `variantId`) disables only the row with an in-flight mutation, not the whole page.
      **Colors: per-unit "$X each" price is `color="text.primary"`, the row's right-aligned
      `lineTotal` is `color="error.main"`** — per request; tried the unit price in `error.main`
      first (matching `ProductDetailPage`'s red price treatment), moved the red to `lineTotal`
      instead per follow-up, since that's the number this row actually charges. The page-level
      Subtotal line (`h6 fontWeight={600}`) is `color="error.main"` too, per the same follow-up
      request, for the same reason — it's the number the page actually totals.
      **Only the thumbnail + product name link to `/shop/${line.productSlug}`** — a fix per
      request; the `Link` used to also wrap the variation chip and unit price, making that whole
      middle stretch of the row (including the gaps between them) clickable-to-navigate, which
      read as "the whole row navigates." Those two now sit in a plain sibling `Box` (`display:
      'flex', gap: 2`) alongside the `Link`, not nested inside it — same visual layout/spacing as
      before, but clicking the variation box or price no longer also navigates.
      react-router-dom's own `Link` (a real `<a href>`, not a `Box`/`onClick`+`navigate()` the way
      `ProductCard.tsx`'s `CardActionArea` does it), specifically so the browser's native
      right-click "Open link in new tab"/middle-click/ctrl-click all work for free; plain inline
      `style` (not `sx`, since `Link` isn't a MUI component) resets `color`/`textDecoration` and
      carries the flex layout. Sibling to the quantity controls/Remove button rather than wrapping
      them, so no `stopPropagation` juggling is needed the way `TaskRow` needs it in `@tasks` —
      `handleVariationBoxClick`'s own `preventDefault()`/`stopPropagation()` calls (previously
      needed since the variation box sat *inside* the Link) were removed as part of this fix, now
      dead code once the box moved outside it.
      **Name/variation chip/unit price still sit inline in one row, as three fixed-width
      columns** — a fix per an earlier request, once the page's wider `80%` container (above) left
      enough horizontal room to lay them out side by side instead of stacking three lines under
      the name. **Fixed widths, not just `flexShrink: 0`, per a follow-up fix**: name is
      `width: 220` with `noWrap` (ellipsis-truncates instead of the row wrapping); the variation
      chip's outer reserved-slot `Box` is `width: 300` (rendered even when `variationLabel` is
      empty, so the slot is still reserved — an unlabeled variant and a labeled one still line up
      in the column beside it); unit price is `width: 90`. Without these fixed widths, the variant
      chip/unit price positions drifted left/right per row depending on how long that row's own
      product name (or variation label) happened to be — a bare `flexShrink: 0` keeps an element
      from being squeezed, but does nothing to stop it from sitting wherever the *previous*
      sibling's natural width happens to end. **The chip itself (inside that reserved slot) is
      also a fixed `width: 300` box** (a further fix — briefly tried unbounded/`inline-block`,
      which sized to content and made the chip's own width drift again independent of the outer
      slot's fixed width; `boxSizing: 'border-box'` so its `border`/`px` don't push it past that
      width) → **rendered as two lines**: "Variation:" label + `KeyboardArrowDownIcon` on the first
      line (a `Stack direction="row"`), `{variationLabel}` on its own line below.
      **Border and hover `bgcolor` both removed, per a later request** — briefly kept a
      `border: '1px solid', borderColor: 'divider'` (removed then reverted once already, see the
      git history around this bullet) plus a `'&:hover': { bgcolor: 'action.hover' }`; both are
      gone now for good, leaving just `borderRadius`/`cursor: 'pointer'` — a plainer, borderless
      clickable area. **`{variationLabel}`'s own `Typography` is `variant="body1"`** (was
      `"caption"`), per the same request — same size as the product name beside it, not smaller.
      **`variationLabel` now joins just the attribute *values*** (`Object.values
      (line.attributes).join(' ')`, e.g. `"15in Black"`) **instead of `key: value` pairs** (was
      `Object.entries(...).map(([k, v]) => \`${k}: ${v}\`)`, e.g. `"Size: 15in, Color: Black"`) —
      per request, a plainer, shorter label now that the "Variation:" word already appears on its
      own line above it.
      Available lines only: an unavailable line has no `productSlug` at all (the backend's own
      `@JsonInclude(NON_NULL)` omission, per the available-flag contract above), so there's nothing
      to link to and that row stays a plain, unlinked block.
    - **Inline variant switching**: a line's attributes render as one borderless "Variation:" box
      (label + chevron, value below — see the two-line/no-border note above) — a Shopee-cart-style
      single unit, not one `Chip` per attribute key (that per-chip design was tried first and
      replaced; each key/value pair is joined into one `variationLabel` string instead). Clicking
      the box opens an MUI `Popover` anchored to it with a full variant picker plus **Cancel**/
      **Confirm** buttons — no separate dialog/page, and deliberately not auto-apply-on-pick (that
      was this feature's very first cut too): `VariantSelector`'s `onSelect` only ever updates a
      staged `pendingVariant` local state now, never calls `onVariantChange` directly, so a
      shopper can browse combinations risk-free and back out. **Confirm** is disabled whenever
      `pendingVariant` is null (incomplete/invalid combination) or already equal to `line.variantId`
      (nothing actually changed) — only then does it call `onVariantChange`/close the popover.
      **Cancel**, and dismissing the popover any other way (`Popover`'s own `onClose`), both route
      through the same `closeVariantMenu` (clears both `variantMenuAnchor` and `pendingVariant`),
      so a discarded in-progress selection never leaks into the next time the box is opened. The
      box's own `onClick` calls `preventDefault()`/`stopPropagation()` first, since it sits inside
      the product-detail `Link` above and would otherwise also follow that link.
      `CartApi` has no dedicated "swap variant" endpoint (only add/update/remove-by-variantId — see
      `ecommerce-service/CLAUDE.md`), so `CartPage.tsx`'s `handleVariantChange` calls
      `CartContext`'s `changeVariant` (see above) rather than raw `addItem`+`removeItem` — not
      atomic (two backend calls, not one transaction), but `addItem`'s own `HINCRBY` means
      switching to a variant already elsewhere in the cart merges into that existing line rather
      than duplicating it, same as a plain add-to-cart would. Reuses
      `components/shop/VariantSelector.tsx` (the same attribute-combo picker
      `ProductDetailPage.tsx` uses) rather than building a second variant UI — that component
      gained a new optional `initialAttributes` prop (pre-fills `selections` instead of starting
      blank) specifically for this, so the popover opens already showing the line's current
      combination (which is also why `pendingVariant` starts non-null on open — re-confirming the
      unchanged combination is exactly what the equality check above guards against). The full
      variant list isn't on `CartLine` at all (it only carries the one resolved variant's own
      fields), so `CartLineRow` lazily fetches it via the existing
      `shopApi.getBySlug(line.productSlug)` on the box's first click, not on row mount — most
      lines in a cart are never touched, so this avoids one extra request per line just to render a
      static row. The swap shares the row's existing `pending` flag (keyed by `variantId`, same as
      quantity/remove), so the quantity stepper and Remove button disable during the swap the same
      way they already do for any other mutation.
    - `CheckoutPage.tsx` — fetches `checkoutApi.preview(selectedVariantIds)` on mount; an empty
      cart (or empty selection) or an all-lines-unavailable cart (both guarded server-side, see
      `ecommerce-service/CLAUDE.md`) renders a "Can't check out right now" message with a link back
      to `/cart` instead of the form. The address form is plain `useState` + a `validate()`
      function + `useSubmitGuard`, the same shape `SignUp.tsx`/`Login.tsx` already establish — no
      Formik/react-hook-form introduced. On successful `confirm`, navigates to
      `/orders/${result.orderId}` — Epic 3's `OrderDetailPage` (below) is now the canonical
      "here's your order" view, with a real Pay Now button this page's own former inline
      confirmation view never could have. Calls `useCart().refresh()` right after a successful
      confirm to resync the NavBar badge/context with the cart's new (post-Phase-2, not
      necessarily *empty*) server-side state, rather than assuming local state.
      **`selectedVariantIds` (post-Epic-2 follow-up, Phase 3)** — read from
      `useLocation().state?.selectedVariantIds`, set by `CartPage`'s "Checkout Selected" flow
      (below); `undefined` for the ordinary "Proceed to Checkout" flow or a direct navigation to
      this page, in which case `preview`/`confirm` both fall back to the whole cart, exactly as
      before this feature existed. Threaded through to both `checkoutApi.preview` (the initial
      fetch) and `checkoutApi.confirm` (on submit) — never re-derived from `preview`'s own
      response, since `preview` already reflects whatever selection was passed to it.
    - `components/shop/VariantSelector.tsx` resolves a picked attribute combination (e.g. size=M,
      color=Black) to one exact `ProductVariant` from the product's own real `variants[]` list —
      genuinely combo-accurate, unlike `ShopPage`'s browse-time facets (which only know "some
      variant has size M" and "some variant has color Black" independently, per
      `ProductSearchView`'s own documented limitation on the backend). A product with no
      attributes at all (a single variant, e.g. "This Is Fine" decal) renders no picker — the
      component auto-selects that sole variant instead. **Gained a `layout?: 'stacked' | 'row'`
      prop** (default `'stacked'`, its original and only shape) so `ProductDetailPage`'s
      Shopee-style redesign could opt into a label-left/chips-right row per attribute (`Size  M L`)
      without disturbing `CartPage`'s own reuse of this component inside its narrow (`minWidth:
      240`) inline variant-switcher `Popover`, below — that one keeps the original `'stacked'`
      label-above-chips shape, which fits a narrow popover better than a wide label/value row
      would. Both layouts share the same chip-rendering logic (`chipsFor(key)`), just arranged
      differently, so the two never drift in what they actually render, only how. **Chips render
      with `sx={{ borderRadius: 1 }}`** — a fix per request, squaring off MUI `Chip`'s default
      fully-rounded pill shape into a rectangle with a small corner radius instead (`Chip` has no
      shape prop of its own; overriding `borderRadius` via `sx` is the supported way).
    - `api/shopApi.ts` — a separate file from `ecommerceApi.ts` (admin CRUD), mirroring the
      backend's own `ProductSearchApi`/`ProductApi` split. `getBySlug` reuses the existing `Product`
      type from Phase B/admin (same `ProductResponse` shape); `search` uses a **new**
      `ProductSearchResult` type (mirrors `ProductSearchResponse` — a genuinely different,
      denormalized shape, not reusable with `Product`).
  - **`pages/orders/OrderHistoryPage.tsx` + `OrderDetailPage.tsx` — Epic 3's shopper-facing GUI
    (US-3.3/3.5/3.6), both `PrivateRoute`-gated (`/orders`, `/orders/:id`), same audience/rule as
    Cart/Checkout above. This pass is deliberately shopper-facing only — the admin ship/deliver
    screen (US-3.7/3.8) is a follow-up, since `AdminOrderApi` only has `POST /{id}/ship`/
    `POST /{id}/deliver` today, no admin "list orders" endpoint to build a fulfillment queue
    against; that's new backend work, not just GUI work.** No named UI template/kit was used (there
    is none anywhere in this app) — `OrderHistoryPage` mirrors `CartPage`'s "`Stack` of `Paper`
    rows" shopper-facing style (discussed and chosen over a dense admin-style `Table` +
    `TablePagination`, which was the alternative considered), and `OrderDetailPage` mirrors
    `CheckoutPage`'s "stacked `Paper` sections" shape almost exactly (Order Summary/Shipping To
    sections copied near-verbatim, since both pages render the same underlying `Order`-shaped data).
    **Both pages' containers are `width: '80%'`** (were fixed `maxWidth: 800`/`700` respectively),
    per request — same change as `ProductDetailPage.tsx`/`CartPage.tsx` above, so all four
    ecommerce pages scale with viewport width instead of capping at a fixed pixel value.
    - **New `utils/format.ts` — `formatPrice`/`formatVariantLabel`** — a post-hoc cleanup, found
      by an explicit audit request once the long run of small iterative tweaks across this
      feature folder had left `formatPrice(value: number): string` (`` `$${value.toFixed(2)}` ``)
      copy-pasted verbatim into six separate files (`CartPage.tsx`, `OrderLineRow.tsx`,
      `AdminOrderListPage.tsx`, `CheckoutPage.tsx`, `OrderDetailPage.tsx`,
      `OrderHistoryPage.tsx`) and the variant-attribute-values-join one-liner duplicated in two
      (`CartPage.tsx`'s `variationLabel`, `OrderLineRow.tsx`'s `variantLabel`). All six/two now
      import from here instead. **Deliberately not folded in**: `ProductCard.tsx`'s own
      `formatPrice(min, max)` and `ProductDetailPage.tsx`'s `formatPriceRange` — both format a
      price *range*, a genuinely different shape from a single value, not just a naming
      coincidence with this file's `formatPrice`.
    - **New `components/Thumbnail.tsx`** — a second post-audit cleanup, same session as
      `utils/format.ts` above: the "show an image, or a centered fallback icon on a muted
      background if there isn't one" pattern had been implemented four separate times
      (`CartPage.tsx`'s own `CartLineThumbnail`, `OrderLineRow.tsx`, `ProductCard.tsx`, each with
      slightly different sizing/fallback-icon-size/fade behavior). `width`/`height`
      (default `64`), `borderRadius` (default `1`), `fallbackIconSize` (default `28`), and an
      opt-in `fade` prop (`CartPage`'s own on-load fade, off by default) cover all three call
      sites' variations — `ProductCard.tsx`'s storefront-grid image now passes
      `width="100%" height={180} borderRadius={0} fallbackIconSize={40}` instead of keeping its own
      inline version. **`ProductDetailPage.tsx`'s small gallery thumbnail strip (the row of clickable
      images below the main product photo) is deliberately not folded into this component** despite
      resembling it at a glance — it's a different concern entirely (an always-real image,
      click-to-select-active-image with a border highlight, no fallback-icon state at all since a
      product's own gallery images are guaranteed to exist), not a duplicate of what this component
      actually does.
    - `api/orderApi.ts` — `list(page, size)`/`getById(id)`/`cancel(id)`/`pay(id)`, mirroring
      `cartApi.ts`'s shape; `cancel`/`pay` both return the freshly-resolved `Order`, same
      "mutating endpoint returns the updated resource" contract the backend already established.
    - `types.ts` gained `OrderStatus` (the backend's full 8-value union),
      `OrderStatusHistoryEntry`, and `Order` — the latter two reuse the *existing* `OrderLine`/
      `Address` types from Epic 2 as-is, since both already field-match `OrderResponse`'s nested
      shapes exactly; nothing Epic-3-specific needed modeling for a line item or address.
    - New `utils/orderStatus.ts` — `ORDER_STATUS_LABELS`/`ORDER_STATUS_COLORS` (shared so a status
      never reads differently between the list and detail views), `isCancellable` (mirrors the
      backend's own cancellable-status set: `PENDING`/`PAYMENT_PROCESSING`/`CONFIRMED`, never
      `SHIPPED`/terminal), and `formatOrderDate`/`formatOrderDateTime`. **Gained
      `ORDER_TAB_GROUPS`** (post-Epic-3 follow-up, per request) — `{ key, label, statuses? }[]`
      driving `OrderHistoryPage`'s status tabs below; grouped Shopee-style rather than one tab per
      raw `OrderStatus` (chosen after asking): `All` (`statuses: undefined`, no filter) | `To Pay`
      (`PENDING`, `PAYMENT_PROCESSING`) | `Processing` (`CONFIRMED`) | `Shipped` (`SHIPPED`) |
      `Delivered` (`DELIVERED`) | `Cancelled` (`CANCELLED`, `EXPIRED`, `FAILED`). **Also gained
      `ORDER_HAPPY_PATH`** (per request) — the order lifecycle's one linear path (`PENDING →
      PAYMENT_PROCESSING → CONFIRMED → SHIPPED → DELIVERED`; `CANCELLED`/`EXPIRED`/`FAILED` are
      terminal branches off it, not steps on it), driving `OrderDetailPage`'s horizontal `Stepper`
      — see that page's own note below for the full activeStep/error-step logic. Also
      `ORDER_HAPPY_PATH_ICONS` (index-aligned with `ORDER_HAPPY_PATH`) — the per-step icon set
      that page's `Stepper` uses instead of MUI's default numbered circles.
    - `OrderHistoryPage.tsx` — one `Paper` card per order (placed date from
      `statusHistory[0].occurredAt`, a status `Chip`, a "View Details" button, every `OrderLine`,
      and `Order.total`); MUI `Pagination` below the list (only rendered when `totalPages > 1`)
      rather than `TablePagination` — this page has no table to attach page-size/row-count chrome
      to, unlike `ProductListPage`'s admin table. **The numeric order id is no longer shown
      anywhere on this card, per request** — "View Details" still navigates to
      `/orders/${order.id}`, only the visible `Order #{id}` text was removed (the header row now
      shows just the placed date and status/action column). **Every `OrderLine` now renders its own
      row, per request**, via the new shared `components/orders/OrderLineRow.tsx` (below) — a
      Shopee-style `<image/link> <info> <subtotal>` layout. Lines are `Divider`-separated (MUI's
      own `divider` prop on the wrapping `Stack`, same idiom `CartPage` uses for its own rows), and
      `Order.total` (`color="error.main"`, per request) renders once, right-aligned, below all of
      them — previously this page only showed an aggregate "{itemCount} items · {total}" line and
      no per-line breakdown at all.
      **Gained an MUI `Tabs` bar (post-Epic-3 follow-up, per
      request)**, one `Tab` per `ORDER_TAB_GROUPS` entry, above the order list — `tabKey` is
      component state (`'all'` default); changing tabs resets `page` to `0` (a filter change can
      invalidate whatever page number was showing under the old filter) and re-fetches via
      `orderApi.list(page, PAGE_SIZE, activeGroup.statuses)`.
      **The `Tabs` bar itself is `bgcolor: 'background.paper'`, `borderRadius: 1`** — a fix per
      request; a vertical rule between each `Tab` label was tried first (`borderRight` on every
      `Tab` but the last) and reverted immediately after, replaced with this instead — the
      semantic paper token, not a hardcoded white, so a future dark theme gets the right dark
      surface color automatically, same reasoning `ProductDetailPage`/`CartPage`'s own cards use.
      **Two distinct empty states, not
      one** — the existing full-page "No orders yet / Go to Shop" takeover now triggers only when
      the `'all'` tab itself has zero results (a genuinely empty order history); a *filtered* tab
      with zero matches instead renders the tabs bar plus an inline "No orders in this category."
      message, so the shopper can still see/switch tabs rather than being shown a "go shop" prompt
      that would be wrong (they do have orders — just none in this one category).
      `orderApi.list` gained an optional `statuses?: OrderStatus[]` parameter (sent as a repeated
      `&statuses=...` query param, matching the backend's own `@RequestParam List<OrderStatus>` —
      see `ecommerce-service/CLAUDE.md`'s matching note), inserted before the existing trailing
      `showError` param.
    - New `components/orders/OrderLineRow.tsx` — extracted from `OrderHistoryPage.tsx`'s own
      inline version once `OrderDetailPage.tsx` needed byte-identical rendering (per request:
      "same as OrderHistoryPage"), same reasoning `components/shop/VariantSelector.tsx` already
      established for a picker shared across pages. Renders `<image/link> <info> <subtotal>`: the
      shared `components/Thumbnail.tsx` (below) via `primaryImageUrl`, no `fade` (this isn't an
      editable/re-mounting row the way a cart line is, so there's no flicker to smooth over), then
      three stacked lines (product name → `Variant: {attribute values joined with a space}` (via
      shared `utils/format.ts`'s `formatVariantLabel` — same "values only, no key:" convention
      `CartPage`'s own variation label uses — omitted entirely when `attributes` is empty/null) →
      `Quantity: x{n}`), then the line's own `lineTotal` (`color="error.main"`, via shared
      `utils/format.ts`'s `formatPrice`) at the trailing edge. **Only the thumbnail and the product
      name link to `/shop/${productSlug}` — not the variant/quantity lines below the name, and not
      the price**, and only when `productSlug` is present at all (falls back to plain, non-linked
      elements when `null` — a since-deleted variant/product). This is a post-audit reconciliation,
      not this component's original shape — it used to wrap its *entire* info block (name +
      variant + quantity) in one `Link`, which an audit flagged as accidental drift from
      `CartPage`'s own cart-line `Link` fix (the identical "only thumbnail+name navigate" rule,
      settled on after a bug report about its price/variant area being unintentionally clickable).
      **Two separate `Link`s here (thumbnail, then name), not one wrapping both**, unlike
      `CartPage`'s side-by-side thumbnail+name — this layout stacks the name *above* the variant/
      quantity lines rather than beside the thumbnail, so a single `Link` can't cleanly cover both
      without also covering the non-clickable lines beneath the name; both `Link`s point at the
      same URL regardless. Depends on `OrderLineResponse`'s `attributes`/`primaryImageUrl`/
      `productSlug` fields (see `ecommerce-service/CLAUDE.md`'s matching `OrderMapper` note) — all
      nullable on `types.ts`'s `OrderLine`.
    - `OrderDetailPage.tsx` — status `Chip` in the header, Items/Shipping-To sections (structurally
      identical to `CheckoutPage`'s own, since both render an order's lines/address). **The Items
      section now renders each line via the shared `OrderLineRow` (per request — same layout as
      `OrderHistoryPage`, including the click-through to the product page) instead of a plain
      "name × qty — price" row**, `Divider`-separated between lines (was a bare `Stack spacing={1}`
      with no separators). **Subtotal/Shipping/Total values are all `color="error.main"`, per
      request** (only the values — the "Subtotal"/"Shipping"/"Total" labels stay their existing
      color/weight). **Horizontal `Stepper` (`@mui/material` core, not `@mui/lab` — no new
      dependency) for at-a-glance order status, per request** — **its own "Order Status" `Paper`
      section, positioned right after the header row, per a follow-up "show it at the top of the
      page" request** (originally landed folded into the "Order Timeline" `Paper` further down;
      moved out to its own card once asked). The detailed "Order Timeline" list (below, unchanged)
      stays a separate section — the `Stepper` is a summary, the list is still the source of exact
      timestamps/reasons. Driven by new `ORDER_HAPPY_PATH` (`utils/orderStatus.ts`): `PENDING →
      PAYMENT_PROCESSING → CONFIRMED → SHIPPED → DELIVERED`, the order lifecycle's one linear path
      — `CANCELLED`/`EXPIRED`/`FAILED` are terminal *branches* off it, not steps along it, so they
      don't get their own step slot. For a happy-path order, `activeStep` is just
      `ORDER_HAPPY_PATH.indexOf(order.status)` (MUI's own completed/active/upcoming step styling
      does the rest — `DELIVERED` itself is additionally marked `completed` by hand, since MUI's
      default only marks steps *before* `activeStep` completed, not the active one). **For an
      off-path (terminal) order**, `activeStep` instead resolves to the happy-path step the order
      was *at* when the terminal transition happened — found by scanning `statusHistory` backward
      for the entry whose `toStatus` matches the order's current terminal status, then taking that
      entry's own `fromStatus`'s index in `ORDER_HAPPY_PATH` (always resolvable: every terminal
      transition's `fromStatus` — `PENDING` for `EXPIRED`/a `PENDING`-stage `CANCELLED`,
      `PAYMENT_PROCESSING` for `FAILED`/a `PAYMENT_PROCESSING`-stage `CANCELLED`, `CONFIRMED` for a
      `CONFIRMED`-stage `CANCELLED` — is always itself a happy-path status per the backend's own
      state machine). That step's `StepLabel` gets `error` (its icon/label turn red — see the
      custom icon note below) plus the actual terminal status label as its `optional` caption,
      instead of inventing a "Cancelled" step; steps after it stay in their plain unreached state —
      visually "the process stopped here," not a false promise that Shipped/Delivered are still
      coming. Each *reached* happy-path step's `optional` caption shows when it was reached
      (`statusHistory` entry with matching `toStatus`), same `formatOrderDateTime` the list below
      already uses. **Bigger, and each step has its own icon, per a follow-up request**: new
      `ORDER_HAPPY_PATH_ICONS` (`utils/orderStatus.ts`, index-aligned with `ORDER_HAPPY_PATH`) —
      `PendingActionsIcon`/`PaymentIcon`/`CheckCircleIcon`/`LocalShippingIcon`/`Inventory2Icon`, one
      per happy-path status — fed into a new local `HappyPathStepIcon` component (this page's own
      `StepIconComponent` override, the standard MUI pattern for a custom step icon that still
      reacts to `active`/`completed`/`error`) rendering a 48px circle (vs. MUI's default ~24px
      numbered circle) with that step's icon inside, swapped for a red `ErrorOutlineIcon` when
      `error` is set regardless of position. **Outlined, not filled, per a follow-up request** — a
      `2px solid` border plus `bgcolor: 'background.paper'`, both colored by state (`error.main`/
      `primary.main`/`text.disabled`), with the icon itself carrying that same state color, rather
      than the first cut's solid-color disc with a white icon. **Border/connector line both
      thickened further, per a follow-up request** — the icon's own border went from `2px` to
      `3px`, and the connector line between steps from MUI's default `1px` to `3px` too, so the
      thicker outline and the thicker connecting line read as one consistent stroke weight.
      **The connector line's color/positioning were then a bug: two fixes bundled into a new
      `OrderStatusConnector` (`styled(StepConnector)`, replacing the earlier plain `sx` overrides
      on `Stepper` for this).** (1) The line's default color came from MUI's own
      active/completed palette, not the exact `text.disabled`/`primary.main` tokens
      `HappyPathStepIcon` colors itself with, so the two visibly drifted — `OrderStatusConnector`
      now targets `stepConnectorClasses.line`/`.active`/`.completed` directly with
      `theme.palette.text.disabled`/`primary.main`, the identical tokens the icon uses. (2) the
      line's default horizontal offset (`calc(±50% + 20px)`) is calibrated for MUI's own smaller
      default icon, so once the icon grew to a 48px (24px-radius) circle the line visibly reached
      in under its edge instead of stopping at it — offset widened to `calc(±50% + 28px)` (past
      the 24px radius plus its 3px border, with a small gap to spare) to clear it. `StepLabel`'s own text is also bumped up
      (`sx` targeting `.MuiStepLabel-label`) and the section's `Paper` gets extra padding
      (`p: { xs: 2.5, sm: 4 }`) to give the larger icons room. `OrderStatusConnector` also sets
      `top: 24` on `stepConnectorClasses.alternativeLabel` (a fix — MUI's own default `top` is
      calibrated for its ~24px icon, so the line sat above center once the icon grew to 48px,
      before this and the color/offset fixes above were folded into one connector component).
      A hand-built **Order Timeline**
      section from `statusHistory` (a `Stack` of plain rows — no `@mui/lab` `Timeline` component,
      which would be a new dependency this app doesn't have and has no other use for). **Pay Now**
      renders only when `status === 'PENDING'`, calling
      `orderApi.pay` via `useSubmitGuard` and branching the resulting notification on the new
      status (`CONFIRMED` → success toast, `FAILED` → error toast, still `PAYMENT_PROCESSING` →
      no extra toast, the status chip already reflects it — a real gateway may not resolve
      instantly, unlike today's `NoOpPaymentGatewayPort`). **Cancel Order** renders when
      `isCancellable(status) && !cancelRequested`, routed through the existing
      `@shared/components/ConfirmDialog` (same component `ProductListPage`'s deactivate action
      already uses) rather than a new one; a `PAYMENT_PROCESSING` order with `cancelRequested`
      already `true` shows an informational banner instead of a second button, since clicking it
      again would just re-set an already-set flag.
    - **`CheckoutPage.tsx`'s successful `confirm` now `navigate`s straight to `/orders/:id`**
      instead of swapping in an inline `OrderConfirmationView` — that component (and the now-dead
      `CheckCircleOutlineIcon` import) was deleted outright. It existed only because no "get order
      by id" endpoint existed yet when Cart & Checkout were built (see that section's own note
      above); now that Epic 3 built one, showing the real page — with a working **Pay Now** button
      — is strictly better than re-deriving a read-only summary from the checkout response alone.
      `OrderConfirmation`'s own type (`lines`/`address`/`droppedLines` etc.) is untouched and still
      used for the dropped-lines warning `CheckoutPage` shows *before* confirming — only the
      post-confirm view changed.
    - `NavBar.tsx` gained an "Orders" button (`ReceiptLongIcon`, no badge — unlike Cart's
      item-count badge, there's no obviously-right count to show, e.g. "orders needing payment" vs.
      "orders in transit" would be arbitrary choices) next to Cart, same `isActive`/
      `location.pathname.startsWith` highlighting convention as every other nav entry.
    - **Verified**: `tsc --noEmit` clean (only the pre-existing `App.test.tsx`/`reportWebVitals.ts`
      dead-code errors and two unrelated `@chat` unused-import warnings, all already documented
      above) and a successful `vite build`; the dev server boots without console errors. **No
      interactive browser testing was possible in this environment** — no Docker available to run
      the backend stack (Postgres/Keycloak/`gateway`/`ecommerce-service`), so the actual
      order-history → detail → pay/cancel click-through is still unverified by a human.
  - **`pages/AdminOrderListPage.tsx` (`/admin/orders`, nested under `AdminLayout`) — the admin
    fulfillment screen (US-3.7/3.8), a follow-up built once the backend's admin list-orders
    endpoint existed.** Mirrors `ProductListPage.tsx`'s admin-`Table` template near-verbatim
    (`Table`/`TablePagination`/row action `IconButton`s under `AdminLayout`), not the shopper-facing
    `Stack`-of-cards style `OrderHistoryPage` uses above — this is the admin audience, same
    reasoning that split `OrderHistoryPage`/`OrderDetailPage` from this page in the first place.
    - New `api/adminOrderApi.ts` — `list(status, page, size)`/`ship(id)`/`deliver(id)`, a separate
      file from `orderApi.ts` (shopper-facing), mirroring the backend's own `AdminOrderApi`/
      `OrderApi` split. `ship`/`deliver` both return the freshly-resolved `Order`, same contract as
      every other mutation in this feature.
    - The status filter (`Select`, same shape as `ProductListPage`'s category/active filters)
      **defaults to `CONFIRMED`** — "ready to ship" is this queue's whole reason to exist, not an
      arbitrary first option; switching to `SHIPPED` shows "ready to mark delivered," and "All
      statuses" browses everything (using the same `ORDER_STATUS_LABELS` map `OrderHistoryPage`/
      `OrderDetailPage` already share via `utils/orderStatus.ts`).
    - Each row's Ship/Deliver `IconButton`s are disabled unless the row's own `status` is
      `CONFIRMED`/`SHIPPED` respectively (`LocalShippingIcon`/`AssignmentTurnedInIcon`) — correct
      regardless of the current filter, e.g. still correctly all-disabled if an admin picks "All
      statuses" and scrolls past a `CANCELLED` row. Both actions route through the existing
      `@shared/components/ConfirmDialog` (one shared dialog, keyed by a local
      `{ order, action: 'ship' | 'deliver' }` state) rather than acting immediately on click — a
      real shipping/delivery notification can go out to the customer as a side effect of either
      action on a real gateway integration later, so a confirm step matters here even though
      neither action is destructive the way `ProductListPage`'s deactivate is.
    - `AdminLayout.tsx` gained an "Order Fulfillment" sidebar entry (`LocalShippingIcon`) next to
      Products, same `NAV_ITEMS` shape as every other admin section.
    - **Verified** the same way as `OrderHistoryPage`/`OrderDetailPage` above: clean `tsc --noEmit`
      (no new errors) and a successful `vite build`; no interactive browser testing was possible in
      this environment (no Docker to run the backend stack).
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
  **`/admin/login` used to be visible to an already-logged-in non-admin user — fixed.**
  `AdminLogin.tsx`'s own redirect effect only ever handled "already authenticated as admin → go to
  `/admin/dashboard`"; a regular user hitting `/admin/login` directly (or getting bounced there by
  `PrivateRoute requireRole="ADMIN"` off any real `/admin/**` page) just saw the normal Admin Login
  form, unredirected — every actual admin *page* was already correctly unreachable for them
  (`PrivateRoute`), but the login page itself wasn't. Fixed by adding an `else if
  (authService.isAuthenticated())` branch to that same effect, sending a non-admin to `/dashboard`
  instead. **Deliberately not solved by wrapping the route in `GuestRoute`** (`Login.tsx`'s own
  equivalent guard): `GuestRoute` only supports one fixed redirect target for *any* authenticated
  visitor, but this page's two "already signed in" destinations genuinely differ by role — using it
  here with a single target would either send a non-admin into a redirect loop (bounced from
  `/admin/dashboard` by `PrivateRoute` straight back to `/admin/login`) or send an admin to the
  wrong dashboard, depending on which target got picked.
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

  **A third, structurally different source of the same staleness: a brand-new Google/Facebook
  login.** `identity-service`'s `UserServiceImpl.findOrCreateFromKeycloak` now auto-renames a
  brokered login's Keycloak-assigned default username (`username == email`) on first sight (see
  that method's own note in `identity-service/CLAUDE.md`) — but unlike the two cases above, there's
  no single call site that both triggers the rename and knows to refresh afterward: the rename
  happens as a side effect of whichever authenticated request happens to be the *first* one after
  login, and `AuthCallback.tsx` (the PKCE callback that completes the login itself) never talks to
  our own backend at all, only to Keycloak's token endpoint directly — it has no way to know a
  rename even happened. Since `AuthCallback.tsx` always `navigate('/dashboard', { replace: true })`s
  on success, `Dashboard.tsx`'s own mount effect (the one that already calls
  `profileApi.getCurrentUser()`) is guaranteed to be that first request, so the fix lives there
  instead: right after `setUser(me)`, it decodes the *current* access token's own
  `preferred_username` claim (`@shared/utils/jwt#decodeJwtPayload`) and compares it against
  `me.username` — the response body already reflects the just-renamed local row regardless, since
  that comes from the freshly-saved entity, not the stale claim. A mismatch means this token predates
  a server-side rename (whichever one — this check doesn't care if it was the broker case above or
  something else entirely), so it calls `authService.refreshAccessToken()` once before anything else
  in the app can fire a second authenticated request against the stale token. Deliberately a general
  "does the claim match what the server just told me" check rather than a backend-supplied "was this
  a brand-new account" flag — it's agnostic to *why* the local and token views diverged, so it would
  equally catch any future server-side rename path without needing its own dedicated signal.
  This mount effect also gained a `hasFetchedRef` guard (same idiom as `AuthCallback.tsx`/
  `AdminAuthCallback.tsx`'s `hasRun`) once it started calling `refreshAccessToken()` conditionally —
  StrictMode's dev-mode double-invoke was already firing the plain `getCurrentUser()` fetch twice
  (harmless on its own, an idempotent GET), but doubling a real token-rotation call isn't safe the
  same way: two near-simultaneous refresh grants risk the second one hitting an already-rotated-out
  refresh token and failing.

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
