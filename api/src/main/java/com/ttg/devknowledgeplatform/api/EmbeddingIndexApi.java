package com.ttg.devknowledgeplatform.api;

import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.admin.EmbeddingIndexItemResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * HTTP contract for the admin content-embedding index listing API.
 *
 * <p>Provides a paginated, filterable view of all content items combined with their
 * RAG embedding statistics. Secured via {@code SecurityConfig} — all
 * {@code /api/v1/admin/**} paths require the {@code ADMIN} role.
 *
 * <p>The implementation
 * ({@link com.ttg.devknowledgeplatform.api.impl.EmbeddingIndexController})
 * carries no HTTP annotations.
 */
@RequestMapping("/api/v1/admin/embeddings")
public interface EmbeddingIndexApi {

    /**
     * Returns a paginated list of content items with their embedding index statistics.
     *
     * @param page          zero-based page number (default {@code 0})
     * @param size          page size (default {@code 20})
     * @param q             optional case-insensitive title substring filter
     * @param contentType   optional content type filter: {@code ARTICLE}, {@code BLOG_POST},
     *                      or {@code QUESTION_ANSWER}
     * @param contentStatus optional content status filter: {@code DRAFT}, {@code PUBLISHED},
     *                      or {@code ARCHIVED}
     * @param indexed       optional indexed status filter: {@code true} for indexed items only,
     *                      {@code false} for non-indexed items only; omit to return all
     * @return {@code 200} with a paged list of embedding index items
     */
    @GetMapping
    ResponseEntity<PagedResponse<EmbeddingIndexItemResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String contentType,
            @RequestParam(required = false) String contentStatus,
            @RequestParam(required = false) Boolean indexed);
}
