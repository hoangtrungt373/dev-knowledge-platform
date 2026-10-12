package com.ttg.devknowledgeplatform.devpractice.sweeper;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.devpractice.config.SubmissionSweeperProperties;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionStatus;
import com.ttg.devknowledgeplatform.devpractice.repository.SubmissionRepository;
import com.ttg.devknowledgeplatform.infra.scheduling.AbstractBatchJob;

import lombok.RequiredArgsConstructor;

/**
 * Closes the known gap in {@code SubmissionJudgeEventListener}: if the process dies mid-judging (or
 * the event is lost before judging starts, or saving the verdict itself fails), the row would stay
 * {@code RUNNING}/{@code PENDING} forever and the GUI would poll it until it gives up. Every minute
 * this marks such rows {@code JUDGE_ERROR} once they've gone {@code app.submissions.sweeper.stale-after}
 * without any update — the submitter then sees "please resubmit" instead of an eternal spinner.
 *
 * <p>Built on infra's {@link AbstractBatchJob}: this class only says which ids to look at and hands
 * each to {@link StaleSubmissionProcessor}; the base class owns the batch loop, failure isolation,
 * optimistic-lock handling, and the per-run summary log.
 *
 * <p>"Without any update" is {@code dteLastModification}, not {@code dteCreation}: marking a row
 * {@code RUNNING} touches it, so a submission that waited long in the queue and then started judging
 * gets the full window from the moment it started. The query is backed by the partial index
 * {@code IDX_SUBMISSION_IN_PROGRESS} (DKP-0059), which only ever holds in-flight rows.
 * {@code @EnableScheduling} lives on {@code DevPracticeServiceApplication}.
 */
@Component
@RequiredArgsConstructor
public class StaleSubmissionSweepJob extends AbstractBatchJob<Integer> {

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
        runBatch();
    }

    @Override
    protected List<Integer> pollBatch(int batchSize) {
        Instant cutoff = Instant.now().minus(properties.staleAfter());
        return submissionRepository.findStaleInProgressIds(IN_PROGRESS, cutoff, PageRequest.of(0, batchSize));
    }

    @Override
    protected boolean processOne(Integer submissionId) {
        return processor.process(submissionId);
    }
}
