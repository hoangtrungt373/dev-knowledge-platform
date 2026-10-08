package com.ttg.devknowledgeplatform.devpractice.service;

import java.util.List;

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

    /**
     * The caller's standing on every problem they've submitted to: {@code SOLVED} once any submission
     * was accepted, {@code ATTEMPTED} otherwise. Problems never submitted to are absent. Counts
     * {@code USER} submissions only — an admin's reference runs never mark a problem solved for them.
     */
    List<ProblemProgress> listProgress(String userUuid);

    /** One of the caller's own {@code USER} submissions — a reference submission is "not found" here. */
    Submission getSubmission(String userUuid, Integer id);

    /** The caller's own {@code USER} submissions, optionally for one problem. */
    Page<Submission> listSubmissions(String userUuid, Integer problemId, Pageable pageable);

    /**
     * An admin's reference run — allowed in any problem status (verifying a draft is the point).
     * Once judged {@code ACCEPTED}, it verifies the problem at the contract version it was judged
     * against.
     *
     * @param publishOnAccept also publish the problem if this run is ACCEPTED and the problem is
     *                        still a DRAFT at the judged contract version (see
     *                        {@code ProblemService#publishIfVerified})
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException {@code PROBLEM_NOT_FOUND}
     */
    Submission createReference(String adminUuid, SubmissionCommands.Create command, boolean publishOnAccept);

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
