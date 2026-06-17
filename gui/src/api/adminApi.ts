import { httpClient } from './httpClient';
import {
  Tag, CreateTagPayload, UpdateTagPayload,
  Category, CategoryTreeNode, CreateCategoryPayload, UpdateCategoryPayload,
  InterviewQuestion, CreateInterviewQuestionPayload, UpdateInterviewQuestionPayload,
  PagedResponse,
} from '../types/admin.types';

type ShowError = (msg: string) => void;

function buildQuery(params: Record<string, string | number | undefined>): string {
  const q = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== '') q.set(k, String(v));
  });
  const s = q.toString();
  return s ? `?${s}` : '';
}

export interface InterviewQuestionListParams {
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: string;
  difficulty?: string;
  status?: string;
  isCommon?: boolean;
  q?: string;
}

export interface CategoryListParams {
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: string;
  parentId?: number;
  rootOnly?: boolean;
  q?: string;
}

export interface TagListParams {
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: string;
  status?: string;
  q?: string;
}

export const adminApi = {
  // ── Tags ──────────────────────────────────────────────────────────────────

  listTags(params: TagListParams, showError?: ShowError): Promise<PagedResponse<Tag>> {
    return httpClient.get(
      `/api/v1/admin/tags${buildQuery(params as Record<string, string | number | undefined>)}`,
      showError,
    );
  },

  createTag(payload: CreateTagPayload, showError?: ShowError): Promise<Tag> {
    return httpClient.post('/api/v1/admin/tags', payload, showError);
  },

  updateTag(id: number, payload: UpdateTagPayload, showError?: ShowError): Promise<Tag> {
    return httpClient.put(`/api/v1/admin/tags/${id}`, payload, showError);
  },

  deleteTag(id: number, showError?: ShowError): Promise<void> {
    return httpClient.delete(`/api/v1/admin/tags/${id}`, showError);
  },

  // ── Categories ─────────────────────────────────────────────────────────────

  listCategories(params: CategoryListParams, showError?: ShowError): Promise<PagedResponse<Category>> {
    return httpClient.get(
      `/api/v1/admin/categories${buildQuery(params as Record<string, string | number | undefined>)}`,
      showError,
    );
  },

  getCategoryTree(showError?: ShowError): Promise<CategoryTreeNode[]> {
    return httpClient.get('/api/v1/admin/categories/tree', showError);
  },

  createCategory(payload: CreateCategoryPayload, showError?: ShowError): Promise<Category> {
    return httpClient.post('/api/v1/admin/categories', payload, showError);
  },

  updateCategory(id: number, payload: UpdateCategoryPayload, showError?: ShowError): Promise<Category> {
    return httpClient.put(`/api/v1/admin/categories/${id}`, payload, showError);
  },

  deleteCategory(id: number, showError?: ShowError): Promise<void> {
    return httpClient.delete(`/api/v1/admin/categories/${id}`, showError);
  },

  // ── Interview Questions ────────────────────────────────────────────────────

  listInterviewQuestions(params: InterviewQuestionListParams, showError?: ShowError): Promise<PagedResponse<InterviewQuestion>> {
    const query: Record<string, string | number | undefined> = {
      page: params.page,
      size: params.size,
      sortBy: params.sortBy,
      sortDir: params.sortDir,
      difficulty: params.difficulty,
      status: params.status,
      q: params.q,
    };
    if (params.isCommon !== undefined) query.isCommon = String(params.isCommon);
    return httpClient.get(`/api/v1/admin/interview-questions${buildQuery(query)}`, showError);
  },

  getInterviewQuestion(id: number, showError?: ShowError): Promise<InterviewQuestion> {
    return httpClient.get(`/api/v1/admin/interview-questions/${id}`, showError);
  },

  createInterviewQuestion(payload: CreateInterviewQuestionPayload, showError?: ShowError): Promise<InterviewQuestion> {
    return httpClient.post('/api/v1/admin/interview-questions', payload, showError);
  },

  updateInterviewQuestion(id: number, payload: UpdateInterviewQuestionPayload, showError?: ShowError): Promise<InterviewQuestion> {
    return httpClient.put(`/api/v1/admin/interview-questions/${id}`, payload, showError);
  },

  deleteInterviewQuestion(id: number, showError?: ShowError): Promise<void> {
    return httpClient.delete(`/api/v1/admin/interview-questions/${id}`, showError);
  },
};
