package com.ttg.devknowledgeplatform.ai.service;

import com.ttg.devknowledgeplatform.ai.dto.client.ContentItemDto;
import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.ContentType;

import java.math.BigDecimal;
import java.util.List;

/**
 * This module's HTTP gateway to {@code content-service}'s internal indexing API
 * ({@code /internal/content-items/**}) — the replacement for the direct
 * {@code ContentItemRepository}/{@code QuestionAnswerRepository}/{@code ArticleRepository}
 * injections {@code ContentIndexingServiceImpl}/{@code EmbeddingIndexServiceImpl} used before
 * {@code content-service} was targeted for standalone-service extraction (see root
 * {@code CLAUDE.md}'s Long-term direction section). Every call sends the shared
 * {@code X-Internal-Api-Key} header configured via {@code ContentServiceClientProperties}.
 */
public interface ContentServiceClient {

    /**
     * Fetches a single content item for on-demand (re)indexing of one document.
     *
     * @param id content item primary key
     * @return the flattened projection
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if no content item has this id
     */
    ContentItemDto getById(Integer id);

    /**
     * Fetches every content item with the given status, unpaged — backs the bulk
     * "index all published content" operation.
     *
     * @param status status to filter by
     * @return all matching content items
     */
    List<ContentItemDto> listByStatus(ContentStatus status);

    /**
     * Fetches a paginated, optionally filtered list of content items — backs the admin
     * embedding-index screen.
     *
     * @param page       zero-based page number
     * @param size       page size
     * @param q          optional case-insensitive title search
     * @param type       optional content-type filter
     * @param status     optional status filter
     * @param ids        optional restriction to this exact set of content item ids
     * @param excludeIds optional exclusion of this exact set of content item ids (mutually
     *                   exclusive with {@code ids} in practice — callers pass one or neither)
     * @return the matching page, sorted by id descending
     */
    PagedResponse<ContentItemDto> list(
            int page, int size, String q, ContentType type, ContentStatus status,
            List<Integer> ids, List<Integer> excludeIds);

    /**
     * Persists the corpus-coherence quality score computed immediately after indexing a document.
     *
     * @param id           content item primary key
     * @param qualityScore mean cosine similarity against the corpus centroid, scale 4
     */
    void updateQualityScore(Integer id, BigDecimal qualityScore);
}
