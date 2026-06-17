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

export interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
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

// ── Interview Questions ─────────────────────────────────────────────────────

export type Difficulty = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';
export type ContentStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

export interface InterviewQuestion {
  id: number;
  contentItemId: number;
  title: string;
  slug: string;
  difficulty: Difficulty;
  questionBody: string;
  shortAnswer: string | null;
  detailedAnswer: string | null;
  isCommon: boolean;
  status: ContentStatus;
  categoryId: number | null;
  tagIds: number[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateInterviewQuestionPayload {
  title: string;
  difficulty: Difficulty;
  questionBody: string;
  shortAnswer?: string | null;
  detailedAnswer?: string | null;
  isCommon?: boolean;
  status?: ContentStatus;
  categoryId?: number | null;
  tagIds?: number[];
}

export interface UpdateInterviewQuestionPayload {
  title: string;
  difficulty: Difficulty;
  questionBody: string;
  shortAnswer?: string | null;
  detailedAnswer?: string | null;
  isCommon?: boolean;
  status?: ContentStatus;
  categoryId?: number | null;
  tagIds?: number[] | null;
}
