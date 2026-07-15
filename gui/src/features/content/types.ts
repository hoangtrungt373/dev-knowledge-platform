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
// Not this feature's own concern (the embedding index is ai-service's), but the content-type
// enum it's keyed by belongs here alongside ContentStatus — @ai imports both from this file,
// mirroring the backend's own ai-service -> content-service dependency direction.

export type EmbeddingContentType = 'ARTICLE' | 'BLOG_POST' | 'QUESTION_ANSWER';
