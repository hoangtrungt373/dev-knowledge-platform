package com.ttg.devknowledgeplatform.devpractice.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.ttg.devknowledgeplatform.devpractice.entity.Submission;

/**
 * User submissions against published problems, plus admin reference submissions that verify a
 * problem before it can be published (see {@code enums.SubmissionKind}). Both are judged the same
 * way, asynchronously, after the creating transaction commits.
 */
public interface SubmissionService {

    /** A user's attempt — only against a {@code PUBLISHED} problem (any other is "not found"). */
    Submission create(String userUuid, SubmissionCommands.Create command);

    /** One of the caller's own {@code USER} submissions — a reference submission is "not found" here. */
    Submission getSubmission(String userUuid, Integer id);

    /** The caller's own {@code USER} submissions, optionally for one problem. */
    Page<Submission> listSubmissions(String userUuid, Integer problemId, Pageable pageable);

    /**
     * An admin's reference run — allowed in any problem status (verifying a draft is the point).
     * Once judged {@code ACCEPTED}, it verifies the problem at the contract version it was judged
     * against.
     *
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException {@code PROBLEM_NOT_FOUND}
     */
    Submission createReference(String adminUuid, SubmissionCommands.Create command);

    /** Every admin's reference runs for one problem, for the verification panel. */
    Page<Submission> listReferenceSubmissions(Integer problemId, Pageable pageable);

    /**
     * One reference run, checked to belong to {@code problemId} — the admin panel polls this while
     * judging runs.
     *
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException {@code SUBMISSION_NOT_FOUND}
     */
    Submission getReferenceSubmission(Integer problemId, Integer submissionId);
}
