package com.ttg.devknowledgeplatform.content.api.impl;

import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.ContentType;
import com.ttg.devknowledgeplatform.content.api.InternalContentApi;
import com.ttg.devknowledgeplatform.content.dto.internal.InternalContentItemResponse;
import com.ttg.devknowledgeplatform.content.dto.internal.UpdateQualityScoreRequest;
import com.ttg.devknowledgeplatform.content.service.InternalContentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Implementation of {@link InternalContentApi}.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class InternalContentController implements InternalContentApi {

    private final InternalContentService internalContentService;

    @Override
    public ResponseEntity<InternalContentItemResponse> getById(Integer id) {
        return ResponseEntity.ok(internalContentService.getById(id));
    }

    @Override
    public ResponseEntity<List<InternalContentItemResponse>> listByStatus(ContentStatus status) {
        return ResponseEntity.ok(internalContentService.listByStatus(status));
    }

    @Override
    public ResponseEntity<PagedResponse<InternalContentItemResponse>> list(
            int page, int size, String q, ContentType type, ContentStatus status,
            List<Integer> ids, List<Integer> excludeIds) {
        return ResponseEntity.ok(internalContentService.list(page, size, q, type, status, ids, excludeIds));
    }

    @Override
    public ResponseEntity<Void> updateQualityScore(Integer id, UpdateQualityScoreRequest request) {
        internalContentService.updateQualityScore(id, request.getQualityScore());
        return ResponseEntity.noContent().build();
    }
}
