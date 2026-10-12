package com.ttg.devknowledgeplatform.infra.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

/** The batch skeleton: bounded poll, every id attempted, outcomes counted, failures isolated. */
class AbstractBatchJobTest {

    @Test
    void pollsWithTheBatchSizeAndCountsEachOutcome() {
        TestJob job = new TestJob(List.of(1, 2, 3, 4), Map.of(
                1, () -> true,
                2, () -> false,
                3, () -> { throw new OptimisticLockingFailureException("changed"); },
                4, () -> { throw new IllegalStateException("boom"); }));

        BatchRunSummary summary = job.run();

        assertThat(job.requestedBatchSize).isEqualTo(AbstractBatchJob.DEFAULT_BATCH_SIZE);
        assertThat(summary).isEqualTo(new BatchRunSummary(4, 1, 2, 1));
        assertThat(job.completed).isEqualTo(summary);
    }

    @Test
    void aFailingIdDoesNotStopTheRestOfTheBatch() {
        TestJob job = new TestJob(List.of(1, 2, 3), Map.of(
                1, () -> { throw new IllegalStateException("boom"); },
                2, () -> true,
                3, () -> true));

        job.run();

        assertThat(job.processed).containsExactly(1, 2, 3);
    }

    @Test
    void anEmptyPollIsAQuietRun() {
        BatchRunSummary summary = new TestJob(List.of(), Map.of()).run();

        assertThat(summary).isEqualTo(new BatchRunSummary(0, 0, 0, 0));
        assertThat(summary.isNoteworthy()).isFalse();
    }

    @Test
    void onlyActionsOrFailuresAreNoteworthy() {
        assertThat(new BatchRunSummary(3, 0, 3, 0).isNoteworthy()).as("all skipped").isFalse();
        assertThat(new BatchRunSummary(1, 1, 0, 0).isNoteworthy()).isTrue();
        assertThat(new BatchRunSummary(1, 0, 0, 1).isNoteworthy()).isTrue();
    }

    /** A job whose per-id behaviour is scripted, recording what the template asked of it. */
    private static final class TestJob extends AbstractBatchJob<Integer> {

        private final List<Integer> ids;
        private final Map<Integer, BooleanSupplier> behaviour;
        private final List<Integer> processed = new ArrayList<>();
        private int requestedBatchSize;
        private BatchRunSummary completed;

        TestJob(List<Integer> ids, Map<Integer, BooleanSupplier> behaviour) {
            this.ids = ids;
            this.behaviour = behaviour;
        }

        BatchRunSummary run() {
            return runBatch();
        }

        @Override
        protected List<Integer> pollBatch(int batchSize) {
            requestedBatchSize = batchSize;
            return ids;
        }

        @Override
        protected boolean processOne(Integer id) {
            processed.add(id);
            return behaviour.get(id).getAsBoolean();
        }

        @Override
        protected void onBatchCompleted(BatchRunSummary summary) {
            completed = summary;
        }
    }
}
