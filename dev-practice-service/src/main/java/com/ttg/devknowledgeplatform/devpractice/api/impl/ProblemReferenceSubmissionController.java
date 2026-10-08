package com.ttg.devknowledgeplatform.devpractice.api.impl;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.devpractice.api.ProblemReferenceSubmissionApi;
import com.ttg.devknowledgeplatform.devpractice.dto.ReferenceSubmissionRequest;
import com.ttg.devknowledgeplatform.devpractice.dto.SubmissionResponse;
import com.ttg.devknowledgeplatform.devpractice.mapper.SubmissionMapper;
import com.ttg.devknowledgeplatform.devpractice.service.SubmissionCommands;
import com.ttg.devknowledgeplatform.devpractice.service.SubmissionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ProblemReferenceSubmissionController implements ProblemReferenceSubmissionApi {

    private final SubmissionService submissionService;
    private final SubmissionMapper submissionMapper;

    @Override
    public ResponseEntity<SubmissionResponse> create(String adminUuid, Integer problemId, ReferenceSubmissionRequest request) {
        SubmissionCommands.Create command =
                new SubmissionCommands.Create(problemId, request.getLanguage(), request.getSourceCode());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(submissionMapper.toResponse(submissionService.createReference(adminUuid, command, request.isPublishOnAccept())));
    }

    @Override
    public ResponseEntity<SubmissionResponse> getById(Integer problemId, Integer submissionId) {
        return ResponseEntity.ok(submissionMapper.toResponse(submissionService.getReferenceSubmission(problemId, submissionId)));
    }

    @Override
    public ResponseEntity<PagedResponse<SubmissionResponse>> list(Integer problemId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return ResponseEntity.ok(PagedResponse.from(
                submissionService.listReferenceSubmissions(problemId, pageable).map(submissionMapper::toResponse)));
    }
}
