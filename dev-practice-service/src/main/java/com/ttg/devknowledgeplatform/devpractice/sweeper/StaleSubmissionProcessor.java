package com.ttg.devknowledgeplatform.devpractice.sweeper;

import java.time.Instant;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.devpractice.config.SubmissionSweeperProperties;
import com.ttg.devknowledgeplatform.devpractice.entity.Submission;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionStatus;
import com.ttg.devknowledgeplatform.devpractice.repository.SubmissionRepository;
import com.ttg.devknowledgeplatform.infra.scheduling.AbstractGuardedUpdateProcessor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fails one stuck submission — the per-row half of {@link StaleSubmissionSweepJob}, on infra's
 * {@link AbstractGuardedUpdateProcessor} (load, re-check, change, save in one transaction). Its own
 * bean, not a method on the sweeper: Spring's {@code @Transactional} is proxy-based, so the sweeper
 * calling its own method would run with no transaction — same split as {@code ecommerce-service}'s
 * {@code OrderReservationExpiryJob}/{@code OrderReservationExpiryProcessor}.
 *
 * <p><b>Concurrency with the judge.</b> The sweeper's id list is a snapshot; by the time this runs, the
 * judge listener may have finished. So the row is re-checked inside the transaction (still in
 * progress, still stale), and the entity's {@code @Version} column guards the remaining window: if
 * the listener saves its verdict between this load and this commit, the commit fails with an
 * optimistic-locking error and the real verdict stands. The opposite order is fine too — a verdict
 * that lands <i>after</i> the sweep overwrites {@code JUDGE_ERROR}, since the listener reloads the row
 * before saving: a late real verdict is more useful than an error.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StaleSubmissionProcessor extends AbstractGuardedUpdateProcessor<Submission, Integer> {

    /** Shown to the submitter; not a verdict on their code, same as the listener's own JUDGE_ERROR. */
    static final String MESSAGE =
            "Judging was interrupted before it could finish, so this submission was not judged. Please resubmit.";

    private final SubmissionRepository submissionRepository;
    private final SubmissionSweeperProperties properties;

    @Override
    protected CrudRepository<Submission, Integer> repository() {
        return submissionRepository;
    }

    /**
     * Still in progress and untouched for {@code stale-after}. The cutoff is recomputed here rather
     * than passed in from the poll: it is a moment later, so a row that qualified then still does.
     */
    @Override
    protected boolean isStillEligible(Submission submission) {
        Instant cutoff = Instant.now().minus(properties.staleAfter());
        return isInProgress(submission.getStatus()) && submission.getDteLastModification().isBefore(cutoff);
    }

    @Override
    protected void apply(Submission submission) {
        log.warn("Submission {} was stuck {} since {} — marking JUDGE_ERROR", submission.getId(),
                submission.getStatus(), submission.getDteLastModification());
        submission.setStatus(SubmissionStatus.JUDGE_ERROR);
        // A partial count isn't a score — same rule as the listener's own JUDGE_ERROR outcome.
        submission.setPassedTestCases(null);
        submission.setErrorMessage(MESSAGE);
    }

    static boolean isInProgress(SubmissionStatus status) {
        return status == SubmissionStatus.PENDING || status == SubmissionStatus.RUNNING;
    }
}
