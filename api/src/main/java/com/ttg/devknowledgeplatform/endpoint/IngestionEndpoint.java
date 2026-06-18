package com.ttg.devknowledgeplatform.endpoint;

import com.ttg.devknowledgeplatform.service.ContentIndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/indexing")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class IngestionEndpoint {

    private final ContentIndexingService contentIndexingService;

    /** Index (or re-index) a single content item. */
    @PostMapping("/content/{contentItemId}")
    public ResponseEntity<Void> index(@PathVariable Integer contentItemId) {
        log.info("Manual index requested for content item id={}", contentItemId);
        contentIndexingService.reindex(contentItemId);
        return ResponseEntity.noContent().build();
    }

    /** Bulk-index all published content. Long-running — call asynchronously in production. */
    @PostMapping("/content/all")
    public ResponseEntity<Void> indexAll() {
        log.info("Bulk index-all requested");
        contentIndexingService.indexAll();
        return ResponseEntity.noContent().build();
    }

    /** Remove all embeddings for a content item. */
    @DeleteMapping("/content/{contentItemId}")
    public ResponseEntity<Void> deleteIndex(@PathVariable Integer contentItemId) {
        log.info("Delete index requested for content item id={}", contentItemId);
        contentIndexingService.deleteIndex(contentItemId);
        return ResponseEntity.noContent().build();
    }
}
