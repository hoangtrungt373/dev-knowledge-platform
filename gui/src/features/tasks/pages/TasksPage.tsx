import { useCallback, useEffect, useState } from 'react';
import { Box, CircularProgress, IconButton, Stack, Typography } from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import TasksSidebar from '../components/TasksSidebar';
import TaskQuickAdd from '../components/TaskQuickAdd';
import TaskRow from '../components/TaskRow';
import TaskDetailPanel from '../components/TaskDetailPanel';
import { taskApi } from '../api/taskApi';
import { useNotification } from '@shared/contexts/NotificationContext';
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
  const buckets = bucketTasks(tasks);

  return (
    <Box sx={{ display: 'flex', height: 'calc(100vh - 48px)', overflow: 'hidden' }}>
      <TasksSidebar
        projects={projects}
        filter={filter}
        onFilterChange={handleFilterChange}
        onProjectsChanged={fetchProjects}
      />

      <Box sx={{ flex: 2, display: 'flex', flexDirection: 'column', overflowY: 'auto', p: 2.5, borderRight: 1, borderColor: 'divider' }}>
        <TaskQuickAdd projectId={quickAddProjectId} onAdded={refreshTasks} />

        {loading ? (
          <Box sx={{ py: 6, display: 'flex', justifyContent: 'center' }}>
            <CircularProgress size={28} />
          </Box>
        ) : filter === 'all' && tasks.length === 0 ? (
          <Typography variant="body2" color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>
            No tasks yet. Add your first one above.
          </Typography>
        ) : filter === 'all' ? (
          BUCKET_ORDER.map(key => {
            const bucketTasksForKey = buckets[key];
            if (bucketTasksForKey.length === 0) return null;
            const collapsed = collapsedBuckets.has(key);
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
                {!collapsed && bucketTasksForKey.map(task => (
                  <TaskRow
                    key={task.id}
                    task={task}
                    onSelect={setSelectedTask}
                    onChanged={refreshTasks}
                    selected={selectedTask?.id === task.id}
                  />
                ))}
              </Box>
            );
          })
        ) : tasks.length === 0 ? (
          <Typography variant="body2" color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>
            No tasks here.
          </Typography>
        ) : (
          tasks.map(task => (
            <TaskRow
              key={task.id}
              task={task}
              onSelect={setSelectedTask}
              onChanged={refreshTasks}
              selected={selectedTask?.id === task.id}
            />
          ))
        )}
      </Box>

      <TaskDetailPanel
        task={selectedTask}
        projects={projects}
        onClose={() => setSelectedTask(null)}
        onChanged={refreshTasks}
      />
    </Box>
  );
}
