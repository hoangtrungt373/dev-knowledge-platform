import { httpClient } from '@shared/api/httpClient';
import { PagedResponse } from '@shared/types';
import {
  CreateProjectPayload, CreateTaskPayload, Project, Task, TaskPriority, TaskStatus,
  UpdateProjectPayload, UpdateTaskPayload,
} from '../types';

type ShowError = (msg: string) => void;

function buildQuery(params: Record<string, string | number | undefined>): string {
  const q = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== '') q.set(k, String(v));
  });
  const s = q.toString();
  return s ? `?${s}` : '';
}

export interface ProjectListParams {
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: string;
}

export interface TaskListParams {
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: string;
  projectId?: number;
  status?: TaskStatus;
  priority?: TaskPriority;
  dueBefore?: string;
  dueAfter?: string;
}

export const taskApi = {
  // ── Projects ────────────────────────────────────────────────────────────

  listProjects(params: ProjectListParams, showError?: ShowError): Promise<PagedResponse<Project>> {
    return httpClient.get(
      `/api/v1/projects${buildQuery(params as Record<string, string | number | undefined>)}`,
      showError,
    );
  },

  createProject(payload: CreateProjectPayload, showError?: ShowError): Promise<Project> {
    return httpClient.post('/api/v1/projects', payload, showError);
  },

  updateProject(id: number, payload: UpdateProjectPayload, showError?: ShowError): Promise<Project> {
    return httpClient.put(`/api/v1/projects/${id}`, payload, showError);
  },

  archiveProject(id: number, showError?: ShowError): Promise<Project> {
    return httpClient.post(`/api/v1/projects/${id}/archive`, undefined, showError);
  },

  // ── Tasks ───────────────────────────────────────────────────────────────

  listTasks(params: TaskListParams, showError?: ShowError): Promise<PagedResponse<Task>> {
    return httpClient.get(
      `/api/v1/tasks${buildQuery(params as Record<string, string | number | undefined>)}`,
      showError,
    );
  },

  listSubtasks(taskId: number, showError?: ShowError): Promise<Task[]> {
    return httpClient.get(`/api/v1/tasks/${taskId}/subtasks`, showError);
  },

  createTask(payload: CreateTaskPayload, showError?: ShowError): Promise<Task> {
    return httpClient.post('/api/v1/tasks', payload, showError);
  },

  updateTask(id: number, payload: UpdateTaskPayload, showError?: ShowError): Promise<Task> {
    return httpClient.put(`/api/v1/tasks/${id}`, payload, showError);
  },

  changeTaskStatus(id: number, status: TaskStatus, showError?: ShowError): Promise<Task> {
    return httpClient.post(`/api/v1/tasks/${id}/status`, { status }, showError);
  },

  deleteTask(id: number, showError?: ShowError): Promise<void> {
    return httpClient.delete(`/api/v1/tasks/${id}`, showError);
  },
};
