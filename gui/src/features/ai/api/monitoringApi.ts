import { httpClient } from '@shared/api/httpClient';
import { PagedResponse } from '@shared/types';
import {
  MetricsPeriod,
  PipelineMetricsSummary,
  EmbeddingIndexItem,
  EmbeddingListParams,
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

export const monitoringApi = {
  // ── Pipeline Metrics ────────────────────────────────────────────────────────

  getPipelineMetricsSummary(period: MetricsPeriod, showError?: ShowError): Promise<PipelineMetricsSummary> {
    return httpClient.get(`/api/v1/admin/pipeline-metrics/summary?period=${period}`, showError);
  },

  // ── Embeddings ────────────────────────────────────────────────────────────

  listEmbeddings(params: EmbeddingListParams, showError?: ShowError): Promise<PagedResponse<EmbeddingIndexItem>> {
    const query: Record<string, string | number | undefined> = {
      page: params.page,
      size: params.size,
      q: params.q,
      contentType: params.contentType,
      contentStatus: params.contentStatus,
    };
    if (params.indexed !== undefined) query.indexed = String(params.indexed);
    return httpClient.get(`/api/v1/admin/embeddings${buildQuery(query)}`, showError);
  },

  reindexContent(contentItemId: number, showError?: ShowError): Promise<void> {
    return httpClient.post(`/api/v1/admin/indexing/content/${contentItemId}`, undefined, showError);
  },

  deleteContentIndex(contentItemId: number, showError?: ShowError): Promise<void> {
    return httpClient.delete(`/api/v1/admin/indexing/content/${contentItemId}`, showError);
  },

  indexAll(showError?: ShowError): Promise<void> {
    return httpClient.post('/api/v1/admin/indexing/content/all', undefined, showError);
  },

  refreshCorpus(showError?: ShowError): Promise<void> {
    return httpClient.post('/api/v1/admin/indexing/corpus/refresh', undefined, showError);
  },
};
