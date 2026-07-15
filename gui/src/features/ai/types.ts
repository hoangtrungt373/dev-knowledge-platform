import { ContentStatus, EmbeddingContentType } from '@content/types';

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
