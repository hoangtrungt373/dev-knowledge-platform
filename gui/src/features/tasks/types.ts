export type ProjectStatus = 'ACTIVE' | 'ARCHIVED';
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE';
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';

// ── Projects ─────────────────────────────────────────────────────────────────

export interface Project {
  id: number;
  name: string;
  description: string | null;
  status: ProjectStatus;
  createdAt: string;
}

export interface CreateProjectPayload {
  name: string;
  description?: string | null;
}

export interface UpdateProjectPayload {
  name: string;
  description?: string | null;
}

// ── Tasks ────────────────────────────────────────────────────────────────────
// projectId/parentTaskId are flat ids, not nested objects — same convention as
// @content/types.ts's Category/QuestionAnswer. parentTaskId is capped at one level deep
// server-side (TaskErrorCode.TASK_INVALID_PARENT) — a subtask can't itself have subtasks.

export interface Task {
  id: number;
  projectId: number | null;
  title: string;
  description: string | null;
  status: TaskStatus;
  priority: TaskPriority;
  dueDate: string | null;
  parentTaskId: number | null;
  createdAt: string;
}

export interface CreateTaskPayload {
  title: string;
  description?: string | null;
  projectId?: number | null;
  priority?: TaskPriority;
  dueDate?: string | null;
  parentTaskId?: number | null;
}

export interface UpdateTaskPayload {
  title: string;
  description?: string | null;
  projectId: number | null;
  priority: TaskPriority;
  dueDate: string | null;
  parentTaskId: number | null;
}

// ── Dashboard filter (TasksPage's 3-pane layout) ──────────────────────────────
// 'all' shows the Overdue/Today/Upcoming/Completed sectioned view; every other
// value narrows the main list to a flat, unsectioned list.

export type TaskFilter = 'all' | 'today' | 'week' | { projectId: number };
