package com.ttg.devknowledgeplatform.devpractice.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ttg.devknowledgeplatform.common.annotation.CurrentUserId;
import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.ReferenceSubmissionRequest;
import com.ttg.devknowledgeplatform.devpractice.dto.SubmissionResponse;

import jakarta.validation.Valid;

/**
 * Admin reference submissions — how an admin proves a problem is solvable before publishing it.
 * {@code ROLE_ADMIN} only (the {@code /api/v1/admin/**} rule in {@code SecurityConfig}); already
 * reachable through {@code gateway}'s existing {@code /api/v1/admin/problems/**} route. Judged
 * exactly like a user submission, asynchronously — poll {@link #getById} until the status is final.
 */
@RequestMapping("/api/v1/admin/problems/{problemId}/reference-submissions")
public interface ProblemReferenceSubmissionApi {

    /**
     * Submits a reference solution — allowed whatever the problem's status, since verifying a draft
     * is the point.
     *
     * @param adminUuid the calling admin's Keycloak subject id
     * @param problemId the problem to verify
     * @param request   language and source code
     * @return {@code 201} with the submission, {@code PENDING} until judged
     */
    @PostMapping
    ResponseEntity<SubmissionResponse> create(
            @CurrentUserId String adminUuid, @PathVariable Integer problemId,
            @Valid @RequestBody ReferenceSubmissionRequest request);

    /**
     * One reference submission — the panel polls this while judging runs.
     *
     * @return {@code 200}; {@code 404 SUBMISSION_NOT_FOUND} if it isn't a reference run of this problem
     */
    @GetMapping("/{submissionId}")
    ResponseEntity<SubmissionResponse> getById(@PathVariable Integer problemId, @PathVariable Integer submissionId);

    /**
     * Every admin's reference runs for the problem, newest first. Each carries the
     * {@code contractVersion} it was judged at — only an {@code ACCEPTED} one at the problem's
     * current version verifies it.
     */
    @GetMapping
    ResponseEntity<PagedResponse<SubmissionResponse>> list(
            @PathVariable Integer problemId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size);
}
