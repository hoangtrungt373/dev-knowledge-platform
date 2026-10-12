package com.ttg.devknowledgeplatform.devpractice.sweeper;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.devpractice.config.SubmissionSweeperProperties;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionStatus;
import com.ttg.devknowledgeplatform.devpractice.repository.SubmissionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Closes the known gap in {@code SubmissionJudgeEventListener}: if the process dies mid-judging (or
 * the event is lost before judging starts, or saving the verdict itself fails), the row would stay
 * {@code RUNNING}/{@code PENDING} forever and the GUI would poll it until it gives up. Every minute
 * this marks such rows {@code JUDGE_ERROR} once they've gone {@code app.submissions.sweeper.stale-after}
 * without any update — the submitter then sees "please resubmit" instead of an eternal spinner.
 *
 * <p>"Without any update" is {@code dteLastModification}, not {@code dteCreation}: marking a row
 * {@code RUNNING} touches it, so a submission that waited long in the queue and then started judging
 * gets the full window from the moment it started. The query is backed by the partial index
 * {@code IDX_SUBMISSION_IN_PROGRESS} (DKP-0059), which only ever holds in-flight rows.
 *
 * <p>One instance is assumed (as everywhere in this reactor), so there's no distributed lock
 * (ShedLock); running two would still be safe — the optimistic locking in
 * {@link StaleSubmissionProcessor} makes a second sweeper's write fail instead of doubling up.
 * {@code @EnableScheduling} lives on {@code DevPracticeServiceApplication}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StaleSubmissionSweeper {

    /** Rows per tick; anything beyond is picked up on the next tick a minute later. */
    static final int BATCH_SIZE = 50;

    private static final List<SubmissionStatus> IN_PROGRESS = List.of(SubmissionStatus.PENDING, SubmissionStatus.RUNNING);

    private final SubmissionRepository submissionRepository;
    private final StaleSubmissionProcessor processor;
    private final SubmissionSweeperProperties properties;

    /**
     * One sweep. {@code fixedDelay}, not {@code fixedRate}: the next sweep starts a full interval
     * after this one ends, so sweeps never overlap.
     */
    @Scheduled(initialDelayString = "${app.submissions.sweeper.initial-delay:PT1M}",
            fixedDelayString = "${app.submissions.sweeper.poll-interval:PT1M}")
    public void sweep() {
        Instant cutoff = Instant.now().minus(properties.staleAfter());
        List<Integer> ids = submissionRepository.findStaleInProgressIds(IN_PROGRESS, cutoff, PageRequest.of(0, BATCH_SIZE));
        int failed = 0;
        for (Integer id : ids) {
            try {
                if (processor.failIfStale(id, cutoff)) {
                    failed++;
                }
            } catch (OptimisticLockingFailureException e) {
                // The judge saved its verdict between our load and our commit — its result stands.
                log.info("Submission {} was judged while being swept — left as judged", id);
            } catch (RuntimeException e) {
                // One bad row must not stop the rest of the batch; the next tick retries it.
                log.error("Could not sweep submission {}", id, e);
            }
        }
        if (failed > 0) {
            log.warn("Sweeper marked {} stuck submission(s) JUDGE_ERROR (stale after {})", failed, properties.staleAfter());
        }
    }
}
