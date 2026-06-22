package com.ttg.devknowledgeplatform.api.impl;

import com.ttg.devknowledgeplatform.api.TagApi;
import com.ttg.devknowledgeplatform.common.enums.TagStatus;
import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.admin.CreateTagRequest;
import com.ttg.devknowledgeplatform.dto.admin.TagResponse;
import com.ttg.devknowledgeplatform.dto.admin.UpdateTagRequest;
import com.ttg.devknowledgeplatform.service.TagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * Implementation of {@link TagApi}.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class TagController implements TagApi {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "name", "status", "dteCreation");

    private final TagService tagService;

    @Override
    public ResponseEntity<TagResponse> create(CreateTagRequest request) {
        TagResponse response = tagService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<TagResponse> update(Integer id, UpdateTagRequest request) {
        TagResponse response = tagService.update(id, request);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> delete(Integer id) {
        tagService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<TagResponse> getById(Integer id) {
        TagResponse response = tagService.getById(id);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PagedResponse<TagResponse>> list(
            int page, int size, String sortBy, String sortDir, TagStatus status, String q) {
        Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir));
        PagedResponse<TagResponse> response = tagService.list(pageable, status, q);
        return ResponseEntity.ok(response);
    }

    private Sort buildSort(String sortBy, String sortDir) {
        String field = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "id";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }
}
