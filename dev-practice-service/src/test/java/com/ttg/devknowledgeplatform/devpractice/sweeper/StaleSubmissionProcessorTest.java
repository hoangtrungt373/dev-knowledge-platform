package com.ttg.devknowledgeplatform.devpractice.sweeper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.ttg.devknowledgeplatform.devpractice.entity.Submission;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionStatus;
import com.ttg.devknowledgeplatform.devpractice.repository.SubmissionRepository;

/** Fails a stuck submission — but only after re-checking it is still stuck inside its own transaction. */
class StaleSubmissionProcessorTest {

    private static final Instant CUTOFF = Instant.parse("2026-10-12T10:00:00Z");

    private final SubmissionRepository repository = mock(SubmissionRepository.class);
    private final StaleSubmissionProcessor processor = new StaleSubmissionProcessor(repository);

    @ParameterizedTest
    @EnumSource(value = SubmissionStatus.class, names = {"PENDING", "RUNNING"})
    void aStuckInProgressSubmissionBecomesAJudgeErrorWithNoPartialScore(SubmissionStatus stuckIn) {
        Submission stuck = submission(stuckIn, CUTOFF.minusSeconds(60));
        stuck.setPassedTestCases(3);
        when(repository.findById(7)).thenReturn(Optional.of(stuck));

        assertThat(processor.failIfStale(7, CUTOFF)).isTrue();

        assertThat(stuck.getStatus()).isEqualTo(SubmissionStatus.JUDGE_ERROR);
        assertThat(stuck.getPassedTestCases()).as("a partial run isn't a score").isNull();
        assertThat(stuck.getErrorMessage()).contains("resubmit");
        verify(repository).save(stuck);
    }

    @Test
    void aSubmissionJudgedSinceTheSweepQueryIsLeftAlone() {
        Submission judged = submission(SubmissionStatus.ACCEPTED, CUTOFF.minusSeconds(60));
        when(repository.findById(7)).thenReturn(Optional.of(judged));

        assertThat(processor.failIfStale(7, CUTOFF)).isFalse();

        assertThat(judged.getStatus()).isEqualTo(SubmissionStatus.ACCEPTED);
        verify(repository, never()).save(any());
    }

    @Test
    void aSubmissionTouchedSinceTheSweepQueryIsLeftAlone() {
        // e.g. it was PENDING when queried, and the listener has just marked it RUNNING.
        Submission justStarted = submission(SubmissionStatus.RUNNING, CUTOFF.plusSeconds(5));
        when(repository.findById(7)).thenReturn(Optional.of(justStarted));

        assertThat(processor.failIfStale(7, CUTOFF)).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void aDeletedSubmissionIsSkipped() {
        when(repository.findById(7)).thenReturn(Optional.empty());

        assertThat(processor.failIfStale(7, CUTOFF)).isFalse();
    }

    private static Submission submission(SubmissionStatus status, Instant lastModified) {
        Submission submission = Submission.builder().status(status).build();
        submission.setId(7);
        submission.setDteLastModification(lastModified);
        return submission;
    }
}
