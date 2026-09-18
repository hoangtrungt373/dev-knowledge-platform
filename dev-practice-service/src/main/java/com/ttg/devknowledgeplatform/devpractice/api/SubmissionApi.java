package com.ttg.devknowledgeplatform.devpractice.api;

import com.ttg.devknowledgeplatform.common.annotation.CurrentUserId;
import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.CreateSubmissionRequest;
import com.ttg.devknowledgeplatform.devpractice.dto.SubmissionResponse;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * HTTP contract for code submissions — requires authentication only (no admin role); ownership,
 * not role, scopes every caller to their own submissions (see
 * {@link com.ttg.devknowledgeplatform.devpractice.service.impl.SubmissionServiceImpl}).
 *
 * <p><b>Phase 1 scope:</b> {@code create} persists a submission as {@code PENDING} — there is no
 * judging pipeline behind this endpoint yet. See {@code Submission}'s Javadoc and this module's
 * own {@code CLAUDE.md} for the planned follow-up phase.
 */
@RequestMapping("/api/v1/submissions")
public interface SubmissionApi {

    /**
     * Submits code against a published problem.
     *
     * @param userUuid the authenticated caller's Keycloak subject id
     * @param request  validated submission payload
     * @return {@code 201} with the created (pending) submission
     */
    @PostMapping
    ResponseEntity<SubmissionResponse> create(
            @CurrentUserId String userUuid, @Valid @RequestBody CreateSubmissionRequest request);

    /**
     * Returns one of the caller's own submissions by its primary key.
     *
     * @param userUuid the authenticated caller's Keycloak subject id
     * @param id       submission primary key
     * @return {@code 200} with the submission
     */
    @GetMapping("/{id}")
    ResponseEntity<SubmissionResponse> getById(@CurrentUserId String userUuid, @PathVariable Integer id);

    /**
     * Returns a paginated list of the caller's own submissions, optionally filtered to one problem.
     *
     * @param userUuid  the authenticated caller's Keycloak subject id
     * @param page      zero-based page number (default 0)
     * @param size      page size (default 20)
     * @param sortBy    field to sort by; allowed values: {@code id}, {@code dteCreation} (default {@code id})
     * @param sortDir   sort direction: {@code asc} or {@code desc} (default {@code desc})
     * @param problemId optional filter to one problem's submissions
     * @return {@code 200} with a paged list of submissions
     */
    @GetMapping
    ResponseEntity<PagedResponse<SubmissionResponse>> list(
            @CurrentUserId String userUuid,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) Integer problemId);
}
