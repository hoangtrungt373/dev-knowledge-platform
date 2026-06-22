package com.ttg.devknowledgeplatform.api.impl;

import com.ttg.devknowledgeplatform.api.IngestionApi;
import com.ttg.devknowledgeplatform.service.ContentIndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implementation of {@link IngestionApi}.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class IngestionController implements IngestionApi {

    private final ContentIndexingService contentIndexingService;

    @Override
    public ResponseEntity<Void> index(Integer contentItemId) {
        log.info("Manual index requested for content item id={}", contentItemId);
        contentIndexingService.reindex(contentItemId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> indexAll() {
        log.info("Bulk index-all requested");
        contentIndexingService.indexAll();
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> deleteIndex(Integer contentItemId) {
        log.info("Delete index requested for content item id={}", contentItemId);
        contentIndexingService.deleteIndex(contentItemId);
        return ResponseEntity.noContent().build();
    }
}
