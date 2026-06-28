package com.ttg.devknowledgeplatform.ai.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for content chunking, embedding storage, and corpus centroid maintenance.
 *
 * <p>Bound from the {@code app.ai.indexing} prefix. Override via environment variables:
 * {@code EMBEDDING_CHUNK_SIZE}, {@code EMBEDDING_CHUNK_OVERLAP},
 * {@code CENTROID_REFRESH_INTERVAL}, {@code INDEXING_COHERENCE_THRESHOLD}.
 */
@ConfigurationProperties(prefix = "app.ai.indexing")
@Validated
@Getter
@Setter
public class IndexingConfig {

    /** Target token count per chunk. 1 token ≈ 4 characters. */
    @Positive
    private int chunkSize = 512;

    /** Token overlap between consecutive chunks to preserve context at boundaries. */
    @Positive
    private int chunkOverlap = 100;

    /**
     * How often {@code CorpusStatisticsService} recomputes and persists corpus centroids.
     * Expressed as an ISO-8601 duration string (e.g. {@code PT6H} = every 6 hours).
     * The default of 6 hours balances freshness against DB cost for curated, infrequently
     * changing content.
     */
    private String centroidRefreshInterval = "PT6H";

    /**
     * Minimum mean cosine similarity between a document's chunk embeddings and the corpus
     * centroid for the document to be considered good quality at indexing time.
     *
     * <p>Computed by {@code IndexingQualityServiceImpl} after all chunks are embedded.
     * Documents below this threshold have their {@code ContentItem.qualityScore} recorded
     * for admin review — they are not silently discarded. Set to {@code 0.0} to record
     * scores without flagging.
     */
    @DecimalMin("0.0") @DecimalMax("1.0")
    private float indexingCoherenceThreshold = 0.35f;
}
