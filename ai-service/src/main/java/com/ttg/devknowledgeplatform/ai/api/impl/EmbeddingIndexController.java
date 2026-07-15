package com.ttg.devknowledgeplatform.ai.api.impl;

import com.ttg.devknowledgeplatform.ai.api.EmbeddingIndexApi;
import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.ai.dto.admin.EmbeddingIndexItemResponse;
import com.ttg.devknowledgeplatform.ai.service.EmbeddingIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implementation of {@link EmbeddingIndexApi}.
 */
@RestController
@RequiredArgsConstructor
public class EmbeddingIndexController implements EmbeddingIndexApi {

    private final EmbeddingIndexService embeddingIndexService;

    @Override
    public ResponseEntity<PagedResponse<EmbeddingIndexItemResponse>> list(
            int page, int size, String q, String contentType, String contentStatus, Boolean indexed) {
        return ResponseEntity.ok(
                embeddingIndexService.list(page, size, q, contentType, contentStatus, indexed));
    }
}
