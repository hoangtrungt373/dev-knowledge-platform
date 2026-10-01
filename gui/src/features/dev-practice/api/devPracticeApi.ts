import { httpClient } from '@shared/api/httpClient';
import { PagedResponse } from '@shared/types';
import { AdminProblemSummary, Difficulty, Problem, ProblemPayload, ProblemStatus } from '../types';

type ShowError = (msg: string) => void;

function buildQuery(params: Record<string, string | number | undefined>): string {
  const q = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== '') q.set(k, String(v));
  });
  const s = q.toString();
  return s ? `?${s}` : '';
}

export interface ProblemListParams {
  page?: number;
  size?: number;
  /** `ProblemController` only allows `id`/`dteCreation`. */
  sortBy?: 'id' | 'dteCreation';
  sortDir?: 'asc' | 'desc';
  difficulty?: Difficulty;
  status?: ProblemStatus;
  q?: string;
}

export const devPracticeApi = {
  // ── Admin problem catalog (/api/v1/admin/problems, ROLE_ADMIN) ──────────────

  listProblems(params: ProblemListParams, showError?: ShowError): Promise<PagedResponse<AdminProblemSummary>> {
    return httpClient.get(
      `/api/v1/admin/problems${buildQuery(params as Record<string, string | number | undefined>)}`,
      showError,
    );
  },

  getProblem(id: number, showError?: ShowError): Promise<Problem> {
    return httpClient.get(`/api/v1/admin/problems/${id}`, showError);
  },

  createProblem(payload: ProblemPayload, showError?: ShowError): Promise<Problem> {
    return httpClient.post('/api/v1/admin/problems', payload, showError);
  },

  updateProblem(id: number, payload: ProblemPayload, showError?: ShowError): Promise<Problem> {
    return httpClient.put(`/api/v1/admin/problems/${id}`, payload, showError);
  },

  deleteProblem(id: number, showError?: ShowError): Promise<void> {
    return httpClient.delete(`/api/v1/admin/problems/${id}`, showError);
  },
};
