package com.ttg.devknowledgeplatform.dto.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Admin-facing projection of a content item combined with its embedding index statistics.
 *
 * <p>Returned by {@code GET /api/v1/admin/embeddings}. Merges content-item metadata
 * (title, type, status, quality score) with aggregate embedding data (chunk count,
 * token count, model name, last indexed timestamp) computed by a single batch JPQL query
 * to avoid N+1 round-trips.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmbeddingIndexItemResponse {

    /** Primary key of the content item. */
    private Integer contentItemId;

    /** Content item title. */
    private String title;

    /** Content type: {@code ARTICLE}, {@code BLOG_POST}, or {@code INTERVIEW_QUESTION}. */
    private String contentType;

    /** Publication status: {@code DRAFT}, {@code PUBLISHED}, or {@code ARCHIVED}. */
    private String contentStatus;

    /**
     * Mean cosine similarity between this document's chunks and the corpus centroid.
     * {@code null} when the document has not been assessed yet.
     */
    private Double qualityScore;

    /** Number of embedding chunks stored for this content item; {@code 0} if not indexed. */
    private long chunkCount;

    /** Sum of token counts across all chunks; {@code 0} if not indexed or no counts recorded. */
    private long totalTokens;

    /** Name of the embedding model used; {@code null} if not indexed. */
    private String modelName;

    /** Timestamp of the most recently modified chunk; {@code null} if not indexed. */
    private Instant lastIndexedAt;

    /** {@code true} when at least one embedding chunk exists for this content item. */
    private boolean indexed;
}
