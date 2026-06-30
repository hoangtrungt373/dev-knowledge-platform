package com.ttg.devknowledgeplatform.ai.dto;

import java.time.Instant;

/**
 * Spring Data interface projection carrying per-content-item embedding aggregate statistics.
 *
 * <p>Returned by
 * {@link com.ttg.devknowledgeplatform.ai.repository.ContentEmbeddingRepository#findStatsByContentItemIds}
 * and used by the admin embedding-index list endpoint to avoid a separate
 * N+1 query per page of content items.
 */
public interface EmbeddingStatsProjection {

    /** Primary key of the content item these stats belong to. */
    Integer getContentItemId();

    /** Number of stored embedding chunks for this content item. */
    Long getChunkCount();

    /** Sum of token counts across all chunks; {@code 0} when no chunk has a token count recorded. */
    Long getTotalTokens();

    /** Embedding model name used for the most recently modified chunk. */
    String getModelName();

    /** Timestamp of the most recently modified chunk, i.e. the last indexed-at time. */
    Instant getLastIndexedAt();
}
