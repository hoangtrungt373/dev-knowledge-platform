import { useCallback, useEffect, useMemo, useState } from 'react';
import { Box, CircularProgress, IconButton, Stack, Typography } from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import InboxIcon from '@mui/icons-material/Inbox';
import TodayIcon from '@mui/icons-material/Today';
import DateRangeIcon from '@mui/icons-material/DateRange';
import FolderIcon from '@mui/icons-material/Folder';
import {
  DndContext, DragEndEvent, KeyboardSensor, PointerSensor, closestCenter, useSensor, useSensors,
} from '@dnd-kit/core';
import { SortableContext, sortableKeyboardCoordinates, verticalListSortingStrategy } from '@dnd-kit/sortable';
import { Group, Panel, useDefaultLayout } from 'react-resizable-panels';
import TasksSidebar from '../components/TasksSidebar';
import TaskQuickAdd from '../components/TaskQuickAdd';
import { TASK_ROW_ACTIONS_GUTTER_PX } from '../components/TaskRow';
import SortableTaskRow from '../components/SortableTaskRow';
import TaskDetailPanel from '../components/TaskDetailPanel';
import ResizeHandle from '../components/ResizeHandle';
import { taskApi } from '../api/taskApi';
import { useNotification } from '@shared/contexts/NotificationContext';
import { useTaskOrder } from '../hooks/useTaskOrder';
import { Project, Task, TaskFilter } from '../types';
import {
  BUCKET_LABEL, BUCKET_ORDER, TaskBucketKey, bucketTasks, endOfToday, endOfWeek, startOfToday,
} from '../utils/taskBuckets';

// No unpaginated "all tasks" endpoint exists server-side, so this is a pragmatic MVP cap for the
// dashboard/flat views — same "cap, not a completeness guarantee" pattern as PROJECT_PICKER_SIZE.
const DASHBOARD_SIZE = 200;
const PROJECT_PICKER_SIZE = 100;

export default function TasksPage(): JSX.Element {
  const { showError } = useNotification();

  const [filter, setFilter] = useState<TaskFilter>('all');
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(true);
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedTask, setSelectedTask] = useState<Task | null>(null);
  const [collapsedBuckets, setCollapsedBuckets] = useState<Set<TaskBucketKey>>(new Set(['COMPLETED']));

  const toggleBucket = (key: TaskBucketKey) => {
    setCollapsedBuckets(prev => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key); else next.add(key);
      return next;
    });
  };

  // Single source of truth for the project list — shared with TasksSidebar (filters to ACTIVE
  // itself) and the dialogs/detail panel below, instead of each fetching its own copy.
  const fetchProjects = useCallback(async () => {
    const data = await taskApi.listProjects(
      { page: 0, size: PROJECT_PICKER_SIZE, sortBy: 'name', sortDir: 'asc' },
      showError,
    );
    setProjects(data.content);
  }, [showError]);

  useEffect(() => { fetchProjects(); }, [fetchProjects]);

  // showSpinner is true only for the filter-driven load below — a mutation (edit/delete/status/
  // quick-add) refetches quietly, swapping `tasks` in place once ready instead of blanking the
  // whole list to a spinner on every action.
  const fetchTasks = useCallback(async (opts?: { showSpinner?: boolean }) => {
    const showSpinner = opts?.showSpinner ?? true;
    if (showSpinner) setLoading(true);
    try {
      const base = { page: 0, size: DASHBOARD_SIZE, sortBy: 'id', sortDir: 'desc' as const };
      const params = filter === 'today'
        ? { ...base, dueAfter: startOfToday().toISOString(), dueBefore: endOfToday().toISOString() }
        : filter === 'week'
          ? { ...base, dueAfter: startOfToday().toISOString(), dueBefore: endOfWeek().toISOString() }
          : typeof filter === 'object'
            ? { ...base, projectId: filter.projectId }
            : base;
      const data = await taskApi.listTasks(params, showError);
      // TaskController's ALLOWED_SORT_FIELDS only permits id/dteCreation, so "soonest due date
      // first" is sorted client-side here; tasks with no due date sort last.
      const sorted = [...data.content].sort((a, b) => {
        if (!a.dueDate && !b.dueDate) return 0;
        if (!a.dueDate) return 1;
        if (!b.dueDate) return -1;
        return new Date(a.dueDate).getTime() - new Date(b.dueDate).getTime();
      });
      setTasks(sorted);
    } finally {
      if (showSpinner) setLoading(false);
    }
  }, [filter, showError]);

  useEffect(() => { fetchTasks(); }, [fetchTasks]);

  const refreshTasks = useCallback(() => fetchTasks({ showSpinner: false }), [fetchTasks]);

  // Re-sync the selected task against the freshest fetch (edits/status changes/deletion) —
  // compares by content, not reference, since a fresh fetch always returns new object instances
  // even when nothing about this particular task changed. Keeping the same reference when the
  // content is unchanged avoids re-triggering TaskDetailPanel's subtask fetch on every refresh.
  useEffect(() => {
    if (!selectedTask) return;
    const fresh = tasks.find(t => t.id === selectedTask.id);
    if (fresh && JSON.stringify(fresh) !== JSON.stringify(selectedTask)) setSelectedTask(fresh);
    else if (!fresh && !loading) setSelectedTask(null);
  }, [tasks, loading, selectedTask]);

  const handleFilterChange = (next: TaskFilter) => {
    setFilter(next);
  };

  const quickAddProjectId = typeof filter === 'object' ? filter.projectId : undefined;
  // Headline above TaskQuickAdd — mirrors TasksSidebar's own icon choices for 'all'/'today'/'week'
  // (InboxIcon/TodayIcon/DateRangeIcon) so the icon here always matches whichever sidebar entry is
  // currently selected. A project filter has no icon of its own in the sidebar (ListItemButton
  // there is text-only), so FolderIcon is a new, reasonable default introduced just for this
  // headline rather than reused from anywhere else in this feature.
  const sectionLabel = filter === 'all' ? 'Inbox'
    : filter === 'today' ? 'Today'
      : filter === 'week' ? 'This week'
        : projects.find(p => p.id === filter.projectId)?.name ?? 'Project';
  const SectionIcon = filter === 'all' ? InboxIcon
    : filter === 'today' ? TodayIcon
      : filter === 'week' ? DateRangeIcon
        : FolderIcon;
  // 'all'/'today'/'week' all render as the bucketed Overdue/Today/Upcoming/Completed sections
  // below — only a project filter gets the flat, unsectioned list. For 'today'/'week' this mostly
  // narrows which of the four buckets ever have anything in them (fetchTasks already constrains
  // the fetch by dueAfter/dueBefore for those two, so e.g. 'today' can never populate Overdue or
  // Upcoming — the server-side date window rules those out already), but still separates done
  // from not-done via the Completed bucket rather than mixing them in one flat list.
  const isBucketedView = typeof filter !== 'object';
  // Memoized so each bucket array keeps a stable reference across renders that don't actually
  // touch `tasks` (selecting a row, toggling a bucket collapsed) — the per-bucket useTaskOrder
  // calls below key their reconciliation effect off that reference, and without this, a fresh
  // (but content-identical) array on every render would re-run localStorage reconciliation on
  // every unrelated re-render instead of only on a real fetch.
  const buckets = useMemo(() => bucketTasks(tasks), [tasks]);

  // Manual drag order via this flat-list hook only applies to the project-filter view now —
  // 'today'/'week' render through the same bucketed (Overdue/Today/Upcoming/Completed) path as
  // 'all' below, ordered via the per-bucket useTaskOrder calls instead. null here makes the hook a
  // no-op passthrough for every other filter, so it's always safe to call unconditionally (React's
  // rules of hooks — this can't live inside the `typeof filter === 'object'` branch below).
  const orderViewKey = typeof filter === 'object' ? `project:${filter.projectId}` : null;
  const { orderedTasks, reorder } = useTaskOrder(orderViewKey, tasks);

  // One useTaskOrder call per bucket, unrolled rather than looped over BUCKET_ORDER — React's
  // rules of hooks require a fixed number of hook calls in the same order every render, and
  // BUCKET_ORDER's 4 keys are a fixed, known set, so this is safe. Each bucket gets its own
  // manual order ("bucket:OVERDUE" etc., separate from the flat-list `project:${id}`/`today`/`week`
  // keys above) rather than sharing one — dragging within Overdue shouldn't touch Today's order.
  const overdueOrder = useTaskOrder('bucket:OVERDUE', buckets.OVERDUE);
  const todayBucketOrder = useTaskOrder('bucket:TODAY', buckets.TODAY);
  const upcomingOrder = useTaskOrder('bucket:UPCOMING', buckets.UPCOMING);
  const completedOrder = useTaskOrder('bucket:COMPLETED', buckets.COMPLETED);
  // Bucket membership itself is always recomputed from each task's actual due date/status
  // (bucketTasks, above) — dragging a task only reorders it within its current bucket, it can't
  // move a task into a different bucket. A drop targeting another bucket isn't wired up (each
  // bucket below gets its own independent DndContext, not one spanning all four), so there's
  // nothing to snap back: the drag simply has no effect outside its own bucket's list.
  const bucketOrders: Record<TaskBucketKey, { orderedTasks: Task[]; reorder: (activeId: number, overId: number) => void }> = {
    OVERDUE: overdueOrder,
    TODAY: todayBucketOrder,
    UPCOMING: upcomingOrder,
    COMPLETED: completedOrder,
  };

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    reorder(Number(active.id), Number(over.id));
  };

  // Persists panel widths to localStorage across reloads (frontend-only, same "stored client-side,
  // not sent to the backend" approach as useTaskOrder) — react-resizable-panels' own built-in
  // mechanism for this, not a hand-rolled localStorage read/write. panelIds must match each Panel's
  // own id below exactly, or the persisted layout won't reapply correctly on mount.
  const { defaultLayout, onLayoutChanged } = useDefaultLayout({
    id: 'tasks-page-layout',
    storage: window.localStorage,
    panelIds: ['tasks-sidebar', 'task-content', 'task-detail'],
  });

  return (
    <Box sx={{ height: 'calc(100vh - 48px)', overflow: 'hidden' }}>
      <Group
        orientation="horizontal"
        defaultLayout={defaultLayout}
        onLayoutChanged={onLayoutChanged}
        style={{ height: '100%' }}
      >
        <Panel id="tasks-sidebar" defaultSize="20" minSize="12" maxSize="35">
          <TasksSidebar
            projects={projects}
            filter={filter}
            onFilterChange={handleFilterChange}
            onProjectsChanged={fetchProjects}
          />
        </Panel>

        <ResizeHandle />

        {/* defaultSize matches task-detail's exactly — the two split the space remaining after
            the sidebar evenly, per the "TaskContent should be the same size as TaskDetailPanel"
            requirement this layout was built for. */}
        <Panel id="task-content" defaultSize="40" minSize="20">
          <Box
            sx={{
              height: '100%',
              display: 'flex',
              flexDirection: 'column',
              overflowY: 'auto',
              pt: 2.5,
              pb: 2.5,
              // pl/pr reserve room on each side for a TaskRow's drag handle (left) and "⋯" button
              // (right), both of which live outside the row's own flex layout (see
              // TaskRow.tsx/SortableTaskRow.tsx) so the row's actual content (title, due date)
              // doesn't have to leave a gap for either sometimes-invisible icon itself. Every row
              // in this column is drag-handled now (see isBucketedView above), so both gutters
              // apply column-wide rather than per-row.
              //
              // The `8 +` base here is a flat outer margin from the column's true edge — unrelated
              // to the icon, safe to tune freely. TASK_ROW_ACTIONS_GUTTER_PX itself is NOT a free
              // variable the same way: it has to stay >= the icon button's own footprint (~26px:
              // 18px icon + 4px padding each side) or the icon starts overlapping the row's real
              // content instead of just clearing it — shrink the icon further
              // (TaskRow.tsx/SortableTaskRow.tsx) before shrinking this constant, not the other
              // way around.
              pl: `${8 + TASK_ROW_ACTIONS_GUTTER_PX}px`,
              pr: `${8 + TASK_ROW_ACTIONS_GUTTER_PX}px`,
            }}
          >
            <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 2 }}>
              <SectionIcon color="action" />
              <Typography variant="h6" fontWeight={700}>{sectionLabel}</Typography>
            </Stack>

            <TaskQuickAdd projectId={quickAddProjectId} onAdded={refreshTasks} />

            {loading ? (
              <Box sx={{ py: 6, display: 'flex', justifyContent: 'center' }}>
                <CircularProgress size={28} />
              </Box>
            ) : isBucketedView && tasks.length === 0 ? (
              <Typography variant="body2" color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>
                {filter === 'all' ? 'No tasks yet. Add your first one above.' : 'No tasks here.'}
              </Typography>
            ) : isBucketedView ? (
              BUCKET_ORDER.map(key => {
                const { orderedTasks: bucketTasksForKey, reorder: bucketReorder } = bucketOrders[key];
                if (bucketTasksForKey.length === 0) return null;
                const collapsed = collapsedBuckets.has(key);
                const handleBucketDragEnd = (event: DragEndEvent) => {
                  const { active, over } = event;
                  if (!over || active.id === over.id) return;
                  bucketReorder(Number(active.id), Number(over.id));
                };
                return (
                  <Box key={key} sx={{ mb: 3, opacity: key === 'COMPLETED' ? 0.6 : 1 }}>
                    <Stack
                      direction="row"
                      alignItems="center"
                      spacing={0.5}
                      onClick={() => toggleBucket(key)}
                      sx={{ mb: 0.5, cursor: 'pointer', userSelect: 'none' }}
                    >
                      <IconButton size="small" sx={{ p: 0.25 }}>
                        {collapsed ? <ChevronRightIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
                      </IconButton>
                      <Typography variant="subtitle2" fontWeight={700} color="text.secondary">
                        {BUCKET_LABEL[key]} ({bucketTasksForKey.length})
                      </Typography>
                    </Stack>
                    {!collapsed && (
                      // Each bucket is its own DndContext/SortableContext, not one shared across all
                      // four — dragging only reorders within a bucket; there's no cross-bucket drop
                      // target (see the bucketOrders comment above for why that's deliberate).
                      <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleBucketDragEnd}>
                        <SortableContext items={bucketTasksForKey.map(t => t.id)} strategy={verticalListSortingStrategy}>
                          {bucketTasksForKey.map(task => (
                            <SortableTaskRow
                              key={task.id}
                              task={task}
                              onSelect={setSelectedTask}
                              onChanged={refreshTasks}
                              selected={selectedTask?.id === task.id}
                            />
                          ))}
                        </SortableContext>
                      </DndContext>
                    )}
                  </Box>
                );
              })
            ) : tasks.length === 0 ? (
              <Typography variant="body2" color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>
                No tasks here.
              </Typography>
            ) : (
              <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
                <SortableContext items={orderedTasks.map(t => t.id)} strategy={verticalListSortingStrategy}>
                  {orderedTasks.map(task => (
                    <SortableTaskRow
                      key={task.id}
                      task={task}
                      onSelect={setSelectedTask}
                      onChanged={refreshTasks}
                      selected={selectedTask?.id === task.id}
                    />
                  ))}
                </SortableContext>
              </DndContext>
            )}
          </Box>
        </Panel>

        <ResizeHandle />

        <Panel id="task-detail" defaultSize="40" minSize="20">
          <TaskDetailPanel
            task={selectedTask}
            projects={projects}
            onClose={() => setSelectedTask(null)}
            onChanged={refreshTasks}
          />
        </Panel>
      </Group>
    </Box>
  );
}
