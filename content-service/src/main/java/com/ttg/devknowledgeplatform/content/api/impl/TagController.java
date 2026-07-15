package com.ttg.devknowledgeplatform.content.api.impl;

import com.ttg.devknowledgeplatform.content.api.TagApi;
import com.ttg.devknowledgeplatform.content.entity.Tag;
import com.ttg.devknowledgeplatform.content.enums.TagStatus;
import com.ttg.devknowledgeplatform.content.service.TagService;
import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.content.dto.CreateTagRequest;
import com.ttg.devknowledgeplatform.content.dto.TagResponse;
import com.ttg.devknowledgeplatform.content.dto.UpdateTagRequest;
import com.ttg.devknowledgeplatform.content.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
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
    private final TagMapper tagMapper;

    @Override
    public ResponseEntity<TagResponse> create(CreateTagRequest request) {
        Tag tag = tagService.create(request.getName(), request.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(tagMapper.toResponse(tag));
    }

    @Override
    public ResponseEntity<TagResponse> update(Integer id, UpdateTagRequest request) {
        Tag tag = tagService.update(id, request.getName(), request.getStatus());
        return ResponseEntity.ok(tagMapper.toResponse(tag));
    }

    @Override
    public ResponseEntity<Void> delete(Integer id) {
        tagService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<TagResponse> getById(Integer id) {
        return ResponseEntity.ok(tagMapper.toResponse(tagService.getById(id)));
    }

    @Override
    public ResponseEntity<PagedResponse<TagResponse>> list(
            int page, int size, String sortBy, String sortDir, TagStatus status, String q) {
        Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir));
        Page<TagResponse> responses = tagService.list(pageable, status, q).map(tagMapper::toResponse);
        return ResponseEntity.ok(PagedResponse.from(responses));
    }

    private Sort buildSort(String sortBy, String sortDir) {
        String field = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "id";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }
}
