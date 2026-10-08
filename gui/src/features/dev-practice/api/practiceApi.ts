import { httpClient } from '@shared/api/httpClient';
import { PagedResponse } from '@shared/types';
import {
  Difficulty,
  Problem,
  ProblemProgress,
  ProblemSummary,
  ProblemTagSummary,
  ProgrammingLanguage,
  StarterCode,
  Submission,
} from '../types';

type ShowError = (msg: string) => void;

export interface PracticeProblemListParams {
  page?: number;
  size?: number;
  difficulty?: Difficulty;
  q?: string;
  /** Problems tagged with *any* of these — sent as a repeated param, like the admin list. */
  tagIds?: number[];
}

/**
 * The learner-facing half of `dev-practice-service`, kept apart from the admin `devPracticeApi`
 * the same way `@ecommerce` splits `shopApi` from `ecommerceApi`: different audience, different
 * endpoints. Problem browsing is public (`/api/v1/public/**`, no login); submissions need a login
 * and only ever return the caller's own `USER` submissions.
 */
export const practiceApi = {
  // ── Public catalog (/api/v1/public/problems, no auth) ─────────────────────────

  listProblems(params: PracticeProblemListParams, showError?: ShowError): Promise<PagedResponse<ProblemSummary>> {
    const q = new URLSearchParams();
    q.set('page', String(params.page ?? 0));
    q.set('size', String(params.size ?? 20));
    // Oldest first: a stable, curriculum-like order (the backend allows sorting by id only).
    q.set('sortBy', 'id');
    q.set('sortDir', 'asc');
    if (params.difficulty) q.set('difficulty', params.difficulty);
    if (params.q) q.set('q', params.q);
    params.tagIds?.forEach(id => q.append('tagIds', String(id)));
    return httpClient.get(`/api/v1/public/problems?${q}`, showError);
  },

  /** Every topic, sorted by name — for the list's tag filter. */
  listTags(showError?: ShowError): Promise<ProblemTagSummary[]> {
    return httpClient.get('/api/v1/public/problem-tags', showError);
  },

  /** A published problem, with its *sample* test cases only — hidden ones never leave the server. */
  getProblem(slug: string, showError?: ShowError): Promise<Problem> {
    return httpClient.get(`/api/v1/public/problems/${encodeURIComponent(slug)}`, showError);
  },

  getStarterCode(slug: string, language: ProgrammingLanguage, showError?: ShowError): Promise<StarterCode> {
    return httpClient.get(
      `/api/v1/public/problems/${encodeURIComponent(slug)}/starter-code?language=${language}`,
      showError,
    );
  },

  // ── Own submissions (/api/v1/submissions, login required) ────────────────────
  // Judged asynchronously: submit returns PENDING; poll getSubmission until the status is final.

  submit(problemId: number, language: ProgrammingLanguage, sourceCode: string, showError?: ShowError): Promise<Submission> {
    return httpClient.post('/api/v1/submissions', { problemId, language, sourceCode }, showError);
  },

  /** Solved/attempted per problem the caller has submitted to (absent = never submitted). */
  getProgress(showError?: ShowError): Promise<ProblemProgress[]> {
    return httpClient.get('/api/v1/submissions/progress', showError);
  },

  getSubmission(id: number, showError?: ShowError): Promise<Submission> {
    return httpClient.get(`/api/v1/submissions/${id}`, showError);
  },

  /** The caller's own submissions for one problem, newest first. */
  listSubmissions(problemId: number, size: number, showError?: ShowError): Promise<PagedResponse<Submission>> {
    return httpClient.get(
      `/api/v1/submissions?problemId=${problemId}&page=0&size=${size}&sortBy=id&sortDir=desc`,
      showError,
    );
  },
};
