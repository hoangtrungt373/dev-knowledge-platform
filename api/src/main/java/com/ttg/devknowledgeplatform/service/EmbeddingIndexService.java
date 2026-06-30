package com.ttg.devknowledgeplatform.service;

import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.admin.EmbeddingIndexItemResponse;

/**
 * Provides a paginated, filterable view of content items merged with their embedding index statistics.
 *
 * <p>Designed for the admin embedding-index management page. Each page of content items is
 * fetched with a JPA Specification query; embedding aggregates (chunk count, token count,
 * model name, last indexed timestamp) are retrieved in a single follow-up batch query to
 * avoid N+1 round-trips.
 */
public interface EmbeddingIndexService {

    /**
     * Returns a paginated list of content items with their embedding stats.
     *
     * @param page          zero-based page number
     * @param size          page size
     * @param q             optional case-insensitive title substring filter
     * @param contentType   optional {@link com.ttg.devknowledgeplatform.common.enums.ContentType} name filter
     * @param contentStatus optional {@link com.ttg.devknowledgeplatform.common.enums.ContentStatus} name filter
     * @param indexed       when non-null, restricts results to indexed ({@code true}) or non-indexed ({@code false}) items
     * @return paged list of embedding index items
     */
    PagedResponse<EmbeddingIndexItemResponse> list(
            int page, int size, String q, String contentType, String contentStatus, Boolean indexed);
}
