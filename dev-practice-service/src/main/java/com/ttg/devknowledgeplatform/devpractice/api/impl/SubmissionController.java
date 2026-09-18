package com.ttg.devknowledgeplatform.devpractice.api.impl;

import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.devpractice.api.SubmissionApi;
import com.ttg.devknowledgeplatform.devpractice.dto.CreateSubmissionRequest;
import com.ttg.devknowledgeplatform.devpractice.dto.SubmissionResponse;
import com.ttg.devknowledgeplatform.devpractice.entity.Submission;
import com.ttg.devknowledgeplatform.devpractice.mapper.SubmissionMapper;
import com.ttg.devknowledgeplatform.devpractice.service.SubmissionCommands;
import com.ttg.devknowledgeplatform.devpractice.service.SubmissionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class SubmissionController implements SubmissionApi {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "dteCreation");

    private final SubmissionService submissionService;
    private final SubmissionMapper submissionMapper;

    @Override
    public ResponseEntity<SubmissionResponse> create(String userUuid, CreateSubmissionRequest request) {
        SubmissionCommands.Create command = new SubmissionCommands.Create(
                request.getProblemId(), request.getLanguage(), request.getSourceCode());
        Submission created = submissionService.create(userUuid, command);
        return ResponseEntity.status(HttpStatus.CREATED).body(submissionMapper.toResponse(created));
    }

    @Override
    public ResponseEntity<SubmissionResponse> getById(String userUuid, Integer id) {
        return ResponseEntity.ok(submissionMapper.toResponse(submissionService.getSubmission(userUuid, id)));
    }

    @Override
    public ResponseEntity<PagedResponse<SubmissionResponse>> list(
            String userUuid, int page, int size, String sortBy, String sortDir, Integer problemId) {
        Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir));
        Page<SubmissionResponse> responses = submissionService.listSubmissions(userUuid, problemId, pageable)
                .map(submissionMapper::toResponse);
        return ResponseEntity.ok(PagedResponse.from(responses));
    }

    private Sort buildSort(String sortBy, String sortDir) {
        String field = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "id";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }
}
