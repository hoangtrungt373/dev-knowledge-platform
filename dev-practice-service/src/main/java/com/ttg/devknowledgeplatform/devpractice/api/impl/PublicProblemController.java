package com.ttg.devknowledgeplatform.devpractice.api.impl;

import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.devpractice.api.PublicProblemApi;
import com.ttg.devknowledgeplatform.devpractice.dto.ProblemResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.ProblemSummaryResponse;
import com.ttg.devknowledgeplatform.devpractice.enums.Difficulty;
import com.ttg.devknowledgeplatform.devpractice.mapper.ProblemMapper;
import com.ttg.devknowledgeplatform.devpractice.service.ProblemService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class PublicProblemController implements PublicProblemApi {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "dteCreation");

    private final ProblemService problemService;
    private final ProblemMapper problemMapper;

    @Override
    public ResponseEntity<PagedResponse<ProblemSummaryResponse>> list(
            int page, int size, String sortBy, String sortDir, Difficulty difficulty, String q) {
        Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir));
        Page<ProblemSummaryResponse> responses =
                problemService.list(pageable, difficulty, ContentStatus.PUBLISHED, q)
                        .map(problemMapper::toSummaryResponse);
        return ResponseEntity.ok(PagedResponse.from(responses));
    }

    @Override
    public ResponseEntity<ProblemResponse> getBySlug(String slug) {
        return ResponseEntity.ok(problemMapper.toPublicResponse(problemService.getPublishedBySlug(slug)));
    }

    private Sort buildSort(String sortBy, String sortDir) {
        String field = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "id";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }
}
