import { httpClient } from '@shared/api/httpClient';
import { PagedResponse } from '@shared/types';
import { buildQueryString, QueryParams } from '@shared/utils/queryString';
import {
  AdminProblemSummary,
  Difficulty,
  ParsedSignature,
  Problem,
  ProblemPayload,
  ProblemStatus,
  ProblemTag,
  ProgrammingLanguage,
  StarterCode,
  Submission,
} from '../types';

type ShowError = (msg: string) => void;

export interface ProblemListParams {
  page?: number;
  size?: number;
  /** `ProblemController` only allows `id`/`dteCreation`. */
  sortBy?: 'id' | 'dteCreation';
  sortDir?: 'asc' | 'desc';
  difficulty?: Difficulty;
  status?: ProblemStatus;
  q?: string;
  /** Problems tagged with *any* of these. Sent as a repeated param (`tagIds=1&tagIds=2`) — the
   * backend binds `Set<Integer> tagIds` from repeats, not a comma-joined value. */
  tagIds?: number[];
}

export interface ProblemTagListParams {
  page?: number;
  size?: number;
  sortBy?: 'name' | 'id' | 'dteCreation';
  sortDir?: 'asc' | 'desc';
  q?: string;
}

export const devPracticeApi = {
  // ── Admin problem catalog (/api/v1/admin/problems, ROLE_ADMIN) ──────────────

  listProblems(params: ProblemListParams, showError?: ShowError): Promise<PagedResponse<AdminProblemSummary>> {
    return httpClient.get(`/api/v1/admin/problems${buildQueryString(params as QueryParams)}`, showError);
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

  /** Reads method name / return type / parameters out of a code template. Nothing is saved. Called
   * without `showError` by the template importer, which shows the error inline instead. */
  parseTemplate(language: ProgrammingLanguage, code: string, showError?: ShowError): Promise<ParsedSignature> {
    return httpClient.post('/api/v1/admin/problems/parse-template', { language, code }, showError);
  },

  /** Starter code for any status (the public endpoint only serves published problems), rendered
   * from the problem's *saved* signature. */
  getStarterCode(problemId: number, language: ProgrammingLanguage, showError?: ShowError): Promise<StarterCode> {
    return httpClient.get(`/api/v1/admin/problems/${problemId}/starter-code?language=${language}`, showError);
  },

  // ── Reference submissions (/api/v1/admin/problems/{id}/reference-submissions) ──
  // An admin's proof that a problem is solvable. Judged asynchronously like a user submission —
  // `create` returns it PENDING; poll `getReferenceSubmission` until the status is final.

  createReferenceSubmission(
    problemId: number,
    language: ProgrammingLanguage,
    sourceCode: string,
    publishOnAccept: boolean,
    showError?: ShowError,
  ): Promise<Submission> {
    return httpClient.post(
      `/api/v1/admin/problems/${problemId}/reference-submissions`,
      { language, sourceCode, publishOnAccept },
      showError,
    );
  },

  getReferenceSubmission(problemId: number, submissionId: number, showError?: ShowError): Promise<Submission> {
    return httpClient.get(`/api/v1/admin/problems/${problemId}/reference-submissions/${submissionId}`, showError);
  },

  /** Newest first. */
  listReferenceSubmissions(problemId: number, size: number, showError?: ShowError): Promise<PagedResponse<Submission>> {
    return httpClient.get(`/api/v1/admin/problems/${problemId}/reference-submissions?page=0&size=${size}`, showError);
  },

  // ── Admin tag catalog (/api/v1/admin/problem-tags, ROLE_ADMIN) ──────────────

  listProblemTags(params: ProblemTagListParams, showError?: ShowError): Promise<PagedResponse<ProblemTag>> {
    return httpClient.get(`/api/v1/admin/problem-tags${buildQueryString(params as QueryParams)}`, showError);
  },

  /** Every tag, sorted by name — for the problem form's picker and the list's tag filter. */
  listAllProblemTags(showError?: ShowError): Promise<ProblemTag[]> {
    return httpClient.get('/api/v1/admin/problem-tags/all', showError);
  },

  createProblemTag(name: string, showError?: ShowError): Promise<ProblemTag> {
    return httpClient.post('/api/v1/admin/problem-tags', { name }, showError);
  },

  updateProblemTag(id: number, name: string, showError?: ShowError): Promise<ProblemTag> {
    return httpClient.put(`/api/v1/admin/problem-tags/${id}`, { name }, showError);
  },

  deleteProblemTag(id: number, showError?: ShowError): Promise<void> {
    return httpClient.delete(`/api/v1/admin/problem-tags/${id}`, showError);
  },
};
