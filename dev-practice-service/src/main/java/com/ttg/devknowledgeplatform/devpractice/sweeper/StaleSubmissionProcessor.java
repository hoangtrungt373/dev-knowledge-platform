package com.ttg.devknowledgeplatform.devpractice.sweeper;

import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ttg.devknowledgeplatform.devpractice.entity.Submission;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionStatus;
import com.ttg.devknowledgeplatform.devpractice.repository.SubmissionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fails one stuck submission. Its own bean, not a method on {@link StaleSubmissionSweeper}: Spring's
 * {@code @Transactional} is proxy-based, so the sweeper calling its own method would run with no
 * transaction — same split as {@code ecommerce-service}'s {@code OrderReservationExpiryJob}/
 * {@code OrderReservationExpiryProcessor}.
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
public class StaleSubmissionProcessor {

    /** Shown to the submitter; not a verdict on their code, same as the listener's own JUDGE_ERROR. */
    static final String MESSAGE =
            "Judging was interrupted before it could finish, so this submission was not judged. Please resubmit.";

    private final SubmissionRepository submissionRepository;

    /**
     * Marks {@code submissionId} {@code JUDGE_ERROR} if it is still in progress and hasn't been
     * touched since {@code cutoff}.
     *
     * @return {@code true} if it was failed, {@code false} if it had moved on meanwhile
     * @throws org.springframework.orm.ObjectOptimisticLockingFailureException if the judge saved a
     *         verdict concurrently — the caller treats that as "moved on"
     */
    @Transactional(rollbackFor = Throwable.class)
    public boolean failIfStale(Integer submissionId, Instant cutoff) {
        Submission submission = submissionRepository.findById(submissionId).orElse(null);
        if (submission == null || !isInProgress(submission.getStatus())
                || !submission.getDteLastModification().isBefore(cutoff)) {
            return false;
        }
        SubmissionStatus was = submission.getStatus();
        submission.setStatus(SubmissionStatus.JUDGE_ERROR);
        // A partial count isn't a score — same rule as the listener's own JUDGE_ERROR outcome.
        submission.setPassedTestCases(null);
        submission.setErrorMessage(MESSAGE);
        submissionRepository.save(submission);
        log.warn("Submission {} was stuck {} since {} — marked JUDGE_ERROR", submissionId, was,
                submission.getDteLastModification());
        return true;
    }

    static boolean isInProgress(SubmissionStatus status) {
        return status == SubmissionStatus.PENDING || status == SubmissionStatus.RUNNING;
    }
}
