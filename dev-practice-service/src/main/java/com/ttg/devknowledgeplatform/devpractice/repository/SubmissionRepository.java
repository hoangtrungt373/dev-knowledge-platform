package com.ttg.devknowledgeplatform.devpractice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ttg.devknowledgeplatform.devpractice.entity.Submission;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionKind;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionStatus;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Integer> {

    /** A user's own history — callers pass {@code SubmissionKind.USER} so reference runs never appear. */
    Page<Submission> findByUserUuidAndKind(String userUuid, SubmissionKind kind, Pageable pageable);

    Page<Submission> findByUserUuidAndKindAndProblem_Id(
            String userUuid, SubmissionKind kind, Integer problemId, Pageable pageable);

    /** Every admin's reference runs for one problem (the admin verification panel). */
    Page<Submission> findByProblem_IdAndKind(Integer problemId, SubmissionKind kind, Pageable pageable);

    /** Users' submissions against one problem — guards deleting a problem people have attempted. */
    long countByProblem_IdAndKind(Integer problemId, SubmissionKind kind);

    /**
     * "Is this problem verified at this contract version?" — called with {@code REFERENCE} /
     * {@code ACCEPTED}, which is exactly what {@code IDX_SUBMISSION_ACCEPTED_REFERENCE}'s partial-index
     * predicate covers (DKP-0056).
     */
    boolean existsByProblem_IdAndKindAndStatusAndContractVersion(
            Integer problemId, SubmissionKind kind, SubmissionStatus status, Integer contractVersion);

    /** Removes a problem's reference runs before the problem itself is deleted (no FK cascade). */
    long deleteByProblem_IdAndKind(Integer problemId, SubmissionKind kind);
}
