import { httpClient } from '@shared/api/httpClient';
import { PagedResponse } from '@shared/types';
import { buildQueryString } from '@shared/utils/queryString';
import {
  Difficulty,
  Problem,
  ProblemProgress,
  ProblemSummary,
  ProblemTagSummary,
  ProgrammingLanguage,
  RunResult,
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
    const query = buildQueryString({
      page: params.page ?? 0,
      size: params.size ?? 20,
      // Oldest first: a stable, curriculum-like order (the backend allows sorting by id only).
      sortBy: 'id',
      sortDir: 'asc',
      difficulty: params.difficulty,
      q: params.q,
      tagIds: params.tagIds,
    });
    return httpClient.get(`/api/v1/public/problems${query}`, showError);
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

  /**
   * Runs code without saving: no customInputs = the sample cases (answers checked); otherwise those
   * JSON argument arrays (an input identical to a sample is still checked). Synchronous — resolves
   * once every input is judged. Called without showError by the workspace, which shows a bad input's
   * message inline in its console.
   */
  run(problemId: number, language: ProgrammingLanguage, sourceCode: string, customInputs?: string[], showError?: ShowError): Promise<RunResult> {
    return httpClient.post('/api/v1/submissions/run', { problemId, language, sourceCode, customInputs }, showError);
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
    const query = buildQueryString({ problemId, page: 0, size, sortBy: 'id', sortDir: 'desc' });
    return httpClient.get(`/api/v1/submissions${query}`, showError);
  },
};
