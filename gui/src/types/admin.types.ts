// PagedResponse now lives in common.types.ts (not admin-specific — friend-graph endpoints
// paginate too); re-exported here so existing `from '../types/admin.types'` imports keep working.
export type { PagedResponse } from './common.types';

export type MetricsPeriod = 'LAST_24H' | 'LAST_7_DAYS' | 'LAST_30_DAYS';

export interface TokenUsageSummary {
  prompt: number;
  completion: number;
  embedding: number;
}

export interface PipelineMetricsSummary {
  period: string;
  totalRequests: number;
  abortedRequests: number;
  /** Null when no LLM calls ran in the window. */
  estimatedCostUsd: number | null;
  /** Null when no requests were recorded. */
  latencyP50Ms: number | null;
  latencyP95Ms: number | null;
  tokenUsage: TokenUsageSummary;
}

export type TagStatus = 'ACTIVE' | 'INACTIVE';

export interface Tag {
  id: number;
  name: string;
  slug: string;
  status: TagStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTagPayload {
  name: string;
  status: TagStatus;
}

export interface UpdateTagPayload {
  name: string;
  status: TagStatus;
}

// ── Categories ──────────────────────────────────────────────────────────────

export interface Category {
  id: number;
  name: string;
  slug: string;
  parentId: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface CategoryTreeNode {
  id: number;
  name: string;
  slug: string;
  parentId: number | null;
  children: CategoryTreeNode[];
}

export interface CreateCategoryPayload {
  name: string;
  parentId?: number | null;
}

export interface UpdateCategoryPayload {
  name: string;
  parentId: number | null;
}

// ── Question & Answer Content ───────────────────────────────────────────────
// General dev-knowledge Q&A, not only interview prep — difficulty/isCommon are
// interview-specific metadata, populated only when a question genuinely has that framing.

export type Difficulty = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';
export type ContentStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

export interface QuestionAnswer {
  id: number;
  contentItemId: number;
  title: string;
  slug: string;
  difficulty: Difficulty | null;
  questionBody: string;
  shortAnswer: string | null;
  detailedAnswer: string | null;
  isCommon: boolean | null;
  status: ContentStatus;
  categoryId: number | null;
  tagIds: number[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateQuestionAnswerPayload {
  title: string;
  difficulty?: Difficulty | null;
  questionBody: string;
  shortAnswer?: string | null;
  detailedAnswer?: string | null;
  isCommon?: boolean | null;
  status?: ContentStatus;
  categoryId?: number | null;
  tagIds?: number[];
}

export interface UpdateQuestionAnswerPayload {
  title: string;
  difficulty?: Difficulty | null;
  questionBody: string;
  shortAnswer?: string | null;
  detailedAnswer?: string | null;
  isCommon?: boolean | null;
  status?: ContentStatus;
  categoryId?: number | null;
  tagIds?: number[] | null;
}

// ── Embeddings ───────────────────────────────────────────────────────────────

export type EmbeddingContentType = 'ARTICLE' | 'BLOG_POST' | 'QUESTION_ANSWER';

export interface EmbeddingIndexItem {
  contentItemId: number;
  title: string;
  contentType: EmbeddingContentType;
  contentStatus: ContentStatus;
  qualityScore: number | null;
  chunkCount: number;
  totalTokens: number;
  modelName: string | null;
  lastIndexedAt: string | null;
  indexed: boolean;
}

export interface EmbeddingListParams {
  page?: number;
  size?: number;
  q?: string;
  contentType?: string;
  contentStatus?: string;
  indexed?: boolean;
}
