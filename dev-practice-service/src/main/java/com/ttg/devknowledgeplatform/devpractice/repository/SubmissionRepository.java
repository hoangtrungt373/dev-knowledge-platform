package com.ttg.devknowledgeplatform.devpractice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * The newest submission matching the same predicate — called with {@code REFERENCE}/{@code ACCEPTED}
     * at the problem's current contract version, it's the reference solution Run uses to compute the
     * expected answer of a custom input. Same partial index as the {@code exists} check above.
     */
    Optional<Submission> findFirstByProblem_IdAndKindAndStatusAndContractVersionOrderByIdDesc(
            Integer problemId, SubmissionKind kind, SubmissionStatus status, Integer contractVersion);

    /**
     * One row per problem the user has submitted to, with how many of those submissions were
     * accepted — the raw material for "solved / attempted" markers. Aggregated in the database
     * (one {@code GROUP BY}, backed by {@code IDX_SUBMISSION_USER}) rather than loading every
     * submission row: a learner can have hundreds of attempts but only one status per problem.
     */
    @Query("""
            select s.problem.id as problemId,
                   sum(case when s.status = :accepted then 1 else 0 end) as acceptedCount
            from Submission s
            where s.userUuid = :userUuid and s.kind = :kind
            group by s.problem.id
            """)
    List<ProblemAttemptSummary> summarizeByProblem(
            @Param("userUuid") String userUuid,
            @Param("kind") SubmissionKind kind,
            @Param("accepted") SubmissionStatus accepted);

    /** Spring Data interface projection for {@link #summarizeByProblem} — aliases map to getters. */
    interface ProblemAttemptSummary {
        Integer getProblemId();

        Long getAcceptedCount();
    }

    /** Removes a problem's reference runs before the problem itself is deleted (no FK cascade). */
    long deleteByProblem_IdAndKind(Integer problemId, SubmissionKind kind);
}
