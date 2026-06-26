package com.ttg.devknowledgeplatform.ai.service;

import com.ttg.devknowledgeplatform.ai.dto.RagFilter;

import java.util.Optional;

/**
 * Manages corpus centroid vectors used for anomaly detection in the RAG pipeline.
 *
 * <p>A centroid is the average embedding vector across all {@code ContentEmbedding} rows
 * for a given content type (or all types combined). At query time, {@code QueryAnomalyStage}
 * compares the query embedding against the relevant centroid: queries far from the centroid
 * are outside the platform's knowledge domain.
 *
 * <p>Centroids are computed via a single SQL {@code avg()} call on the {@code content_embedding}
 * table, persisted to {@code SYS_PARAM} for durability across restarts, and cached in memory
 * for zero-latency access on the hot path.
 */
public interface CorpusStatisticsService {

    /**
     * Returns the in-memory cached centroid most relevant to the given filter.
     *
     * <p>Resolution rules:
     * <ul>
     *   <li>Filter targets exactly one {@code ContentType} → that type's centroid</li>
     *   <li>Filter is mixed or unset → global {@code CENTROID_ALL}</li>
     * </ul>
     *
     * <p>Returns empty if no centroid has been computed yet (e.g. the knowledge base is empty
     * or the first scheduled refresh has not run yet).
     *
     * @param filter the active retrieval filter from {@code RagPipelineContext}
     * @return the matching centroid vector, or empty if unavailable
     */
    Optional<float[]> getCentroidFor(RagFilter filter);

    /**
     * Recomputes all corpus centroids, persists them to {@code SYS_PARAM}, and refreshes
     * the in-memory cache.
     *
     * <p>Called automatically on a configurable schedule ({@code app.ai.embedding.centroid-refresh-interval}).
     * May also be called manually after a large content import to avoid waiting for the next
     * scheduled run.
     *
     * <p>Content types with no indexed embeddings are silently skipped — their cached
     * centroid remains {@code null} until at least one chunk is indexed.
     */
    void refresh();
}
