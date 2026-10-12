package com.ttg.devknowledgeplatform.devpractice.sweeper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.ttg.devknowledgeplatform.devpractice.config.SubmissionSweeperProperties;
import com.ttg.devknowledgeplatform.devpractice.entity.Submission;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionStatus;
import com.ttg.devknowledgeplatform.devpractice.repository.SubmissionRepository;

/** One sweep: the right query, one processor call per id, and one bad id never stops the batch. */
class StaleSubmissionSweeperTest {

    private final SubmissionRepository repository = mock(SubmissionRepository.class);
    private final StaleSubmissionProcessor processor = mock(StaleSubmissionProcessor.class);
    private final StaleSubmissionSweeper sweeper = new StaleSubmissionSweeper(
            repository, processor, new SubmissionSweeperProperties(Duration.ofMinutes(10)));

    @Test
    void looksForInProgressSubmissionsOlderThanStaleAfterInOneBoundedBatch() {
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        when(repository.findStaleInProgressIds(eq(List.of(SubmissionStatus.PENDING, SubmissionStatus.RUNNING)),
                cutoff.capture(), page.capture())).thenReturn(List.of());

        Instant before = Instant.now();
        sweeper.sweep();

        assertThat(cutoff.getValue()).isBetween(before.minus(Duration.ofMinutes(10)).minusSeconds(1),
                Instant.now().minus(Duration.ofMinutes(10)));
        assertThat(page.getValue().getPageSize()).isEqualTo(StaleSubmissionSweeper.BATCH_SIZE);
    }

    @Test
    void aConcurrentVerdictOrAFailureOnOneIdDoesNotStopTheRest() {
        when(repository.findStaleInProgressIds(any(), any(), any())).thenReturn(List.of(1, 2, 3));
        when(processor.failIfStale(eq(1), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Submission.class, 1));
        when(processor.failIfStale(eq(2), any())).thenThrow(new IllegalStateException("boom"));
        when(processor.failIfStale(eq(3), any())).thenReturn(true);

        sweeper.sweep();

        verify(processor).failIfStale(eq(3), any());
    }
}
