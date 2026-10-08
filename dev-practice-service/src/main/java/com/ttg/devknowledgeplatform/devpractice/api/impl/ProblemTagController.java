package com.ttg.devknowledgeplatform.devpractice.api.impl;

import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.devpractice.api.ProblemTagApi;
import com.ttg.devknowledgeplatform.devpractice.dto.ProblemTagRequest;
import com.ttg.devknowledgeplatform.devpractice.dto.ProblemTagResponse;
import com.ttg.devknowledgeplatform.devpractice.mapper.ProblemMapper;
import com.ttg.devknowledgeplatform.devpractice.service.ProblemTagService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ProblemTagController implements ProblemTagApi {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("name", "id", "dteCreation");

    private final ProblemTagService problemTagService;
    private final ProblemMapper problemMapper;

    @Override
    public ResponseEntity<ProblemTagResponse> create(ProblemTagRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(problemMapper.toResponse(problemTagService.create(request.getName())));
    }

    @Override
    public ResponseEntity<ProblemTagResponse> update(Integer id, ProblemTagRequest request) {
        return ResponseEntity.ok(problemMapper.toResponse(problemTagService.update(id, request.getName())));
    }

    @Override
    public ResponseEntity<Void> delete(Integer id) {
        problemTagService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<PagedResponse<ProblemTagResponse>> list(
            int page, int size, String sortBy, String sortDir, String q) {
        Pageable pageable = PageRequest.of(page, size,
                SortParams.of(sortBy, sortDir, ALLOWED_SORT_FIELDS, "name", Sort.Direction.ASC));
        return ResponseEntity.ok(PagedResponse.from(problemTagService.list(pageable, q).map(problemMapper::toResponse)));
    }

    @Override
    public ResponseEntity<List<ProblemTagResponse>> listAll() {
        return ResponseEntity.ok(problemTagService.listAll().stream().map(problemMapper::toResponse).toList());
    }
}
