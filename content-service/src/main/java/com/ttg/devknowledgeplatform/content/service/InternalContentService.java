package com.ttg.devknowledgeplatform.content.service;

import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.ContentType;
import com.ttg.devknowledgeplatform.content.dto.internal.InternalContentItemResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * Server-to-server read/write surface {@code ai-service}'s indexing pipeline calls over HTTP
 * instead of reaching into this module's {@code ContentItemRepository}/{@code QuestionAnswerRepository}/
 * {@code ArticleRepository} directly. See {@code InternalContentApi} for the HTTP contract.
 */
public interface InternalContentService {

    /**
     * Returns a single content item (with its question-answer/article subtype fields flattened in)
     * for on-demand (re)indexing of one document.
     *
     * @param id content item primary key
     * @return the flattened projection
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if no content item has this id
     */
    InternalContentItemResponse getById(Integer id);

    /**
     * Returns every content item with the given status, unpaged — backs {@code ai-service}'s bulk
     * "index all published content" operation.
     *
     * @param status status to filter by
     * @return all matching content items
     */
    List<InternalContentItemResponse> listByStatus(ContentStatus status);

    /**
     * Returns a paginated, optionally filtered list of content items — backs {@code ai-service}'s
     * admin embedding-index screen.
     *
     * @param page   zero-based page number
     * @param size   page size
     * @param q      optional case-insensitive title search
     * @param type   optional content-type filter
     * @param status optional status filter
     * @param ids        optional restriction to this exact set of content item ids (used by
     *                   {@code ai-service} to intersect this page with the set of content item ids
     *                   it already knows has been embedded, since that fact now lives in a
     *                   different service's database)
     * @param excludeIds optional exclusion of this exact set of content item ids (the inverse of
     *                   {@code ids} — used by {@code ai-service} for its "not yet indexed" filter,
     *                   so the NOT IN runs as part of this query instead of an in-memory scan over
     *                   every matching row)
     * @return the matching page, sorted by id descending
     */
    PagedResponse<InternalContentItemResponse> list(
            int page, int size, String q, ContentType type, ContentStatus status,
            List<Integer> ids, List<Integer> excludeIds);

    /**
     * Persists the corpus-coherence quality score {@code ai-service} computed immediately after
     * indexing a document.
     *
     * @param id           content item primary key
     * @param qualityScore mean cosine similarity against the corpus centroid, scale 4
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if no content item has this id
     */
    void updateQualityScore(Integer id, BigDecimal qualityScore);
}
