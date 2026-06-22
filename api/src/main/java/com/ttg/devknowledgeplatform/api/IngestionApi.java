package com.ttg.devknowledgeplatform.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * HTTP contract for the admin content-indexing (ingestion) API.
 *
 * <p>Exposes manual re-index and bulk-index operations for RAG pipeline maintenance.
 * All endpoints are restricted to users with the {@code ADMIN} role. The implementation
 * ({@link com.ttg.devknowledgeplatform.api.impl.IngestionController}) carries no HTTP annotations.
 */
@RequestMapping("/api/v1/admin/indexing")
@PreAuthorize("hasRole('ADMIN')")
public interface IngestionApi {

    /**
     * Index (or re-index) a single content item.
     *
     * @param contentItemId primary key of the content item to index
     * @return {@code 204 No Content}
     */
    @PostMapping("/content/{contentItemId}")
    ResponseEntity<Void> index(@PathVariable Integer contentItemId);

    /**
     * Bulk-index all published content. Long-running — call asynchronously in production.
     *
     * @return {@code 204 No Content}
     */
    @PostMapping("/content/all")
    ResponseEntity<Void> indexAll();

    /**
     * Remove all embeddings for a content item.
     *
     * @param contentItemId primary key of the content item whose index should be removed
     * @return {@code 204 No Content}
     */
    @DeleteMapping("/content/{contentItemId}")
    ResponseEntity<Void> deleteIndex(@PathVariable Integer contentItemId);
}
