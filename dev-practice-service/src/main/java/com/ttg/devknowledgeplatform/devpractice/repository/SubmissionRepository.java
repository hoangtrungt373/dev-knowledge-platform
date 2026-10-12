package com.ttg.devknowledgeplatform.devpractice.repository;

import java.time.Instant;
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

    /**
     * Ranks one accepted run against other users' best accepted runs of the same problem, in the same
     * language, judged against the same test data — the data behind "Beats 87%".
     *
     * <p>How it works: {@code other_users} keeps each other user's best runtime and best memory
     * (one row per user, so someone who submitted 50 times counts once; the caller's own runs are
     * excluded). {@code pool} adds the caller's run as one more row, flagged {@code mine}.
     * {@code PERCENT_RANK()} over the pool sorted <b>slowest first</b> gives {@code (rank - 1) / (n - 1)}:
     * {@code rank - 1} is the number of rows strictly slower than the caller (equal values share a rank,
     * so a tie isn't counted as beaten), and {@code n - 1} is the number of other users — exactly the
     * "beats" fraction. Memory is ranked the same way in a second window. A window function rather than
     * two {@code COUNT}s: one pass over the pool, and the ranking logic reads as what it is.
     *
     * <p>Native SQL because JPQL has no window functions. Aliases are quoted so Postgres keeps their
     * case and they match {@link RankingRow}'s getters. Verified against H2 in PostgreSQL mode (ties,
     * other languages/versions/kinds/statuses ignored, nobody to compare with) — not yet against a
     * live Postgres.
     *
     * @return one row for the caller's run; {@code compared} = 0 when nobody else has solved it
     */
    @Query(nativeQuery = true, value = """
            WITH other_users AS (
                SELECT MIN(s.RUNTIME_MS) AS runtime_ms, MIN(s.MEMORY_KB) AS memory_kb
                FROM dev_practice.SUBMISSION s
                WHERE s.PROBLEM_ID = :problemId
                  AND s.LANGUAGE = :language
                  AND s.CONTRACT_VERSION = :contractVersion
                  AND s.KIND = 'USER'
                  AND s.STATUS = 'ACCEPTED'
                  AND s.RUNTIME_MS IS NOT NULL
                  AND s.MEMORY_KB IS NOT NULL
                  AND s.USER_UUID <> :userUuid
                GROUP BY s.USER_UUID
            ),
            pool AS (
                SELECT runtime_ms, memory_kb, FALSE AS mine FROM other_users
                UNION ALL
                SELECT CAST(:runtimeMs AS INTEGER), CAST(:memoryKb AS INTEGER), TRUE
            ),
            ranked AS (
                SELECT mine,
                       PERCENT_RANK() OVER (ORDER BY runtime_ms DESC) AS runtime_beats,
                       PERCENT_RANK() OVER (ORDER BY memory_kb DESC) AS memory_beats,
                       COUNT(*) OVER () - 1 AS compared
                FROM pool
            )
            SELECT runtime_beats AS "runtimeBeats", memory_beats AS "memoryBeats", compared AS "compared"
            FROM ranked
            WHERE mine
            """)
    RankingRow rankAgainstOtherUsers(
            @Param("problemId") Integer problemId,
            @Param("language") String language,
            @Param("contractVersion") Integer contractVersion,
            @Param("userUuid") String userUuid,
            @Param("runtimeMs") Integer runtimeMs,
            @Param("memoryKb") Integer memoryKb);

    /** Projection for {@link #rankAgainstOtherUsers}: fractions 0–1, and the number of other users. */
    interface RankingRow {
        Double getRuntimeBeats();

        Double getMemoryBeats();

        Long getCompared();
    }

    /** Spring Data interface projection for {@link #summarizeByProblem} — aliases map to getters. */
    interface ProblemAttemptSummary {
        Integer getProblemId();

        Long getAcceptedCount();
    }

    /**
     * Ids of submissions still in progress and not updated since {@code cutoff}, oldest first — the
     * candidates {@code StaleSubmissionSweepJob} fails. Ids only: each is reloaded in its own transaction.
     * Pass exactly (PENDING, RUNNING) — that matches the partial index {@code IDX_SUBMISSION_IN_PROGRESS}'s
     * own predicate (DKP-0059), which Postgres needs to see to use it.
     */
    @Query("""
            select s.id from Submission s
            where s.status in :statuses and s.dteLastModification < :cutoff
            order by s.dteLastModification
            """)
    List<Integer> findStaleInProgressIds(
            @Param("statuses") List<SubmissionStatus> statuses, @Param("cutoff") Instant cutoff, Pageable pageable);

    /** Removes a problem's reference runs before the problem itself is deleted (no FK cascade). */
    long deleteByProblem_IdAndKind(Integer problemId, SubmissionKind kind);
}
