package com.ttg.devknowledgeplatform.infra.scheduling;

import java.util.List;

import org.springframework.dao.OptimisticLockingFailureException;

import lombok.extern.slf4j.Slf4j;

/**
 * GoF <b>Template Method</b> (Behavioral) for a scheduled "poll a batch of ids, process each one"
 * job — the shape of {@code ecommerce-service}'s order/refund reconciliation and reservation-expiry
 * jobs and {@code dev-practice-service}'s stale-submission sweeper. {@link #runBatch()} is the fixed
 * skeleton; a subclass fills in two steps:
 * <ul>
 *   <li>{@link #pollBatch(int)} — which ids to look at this run (its own repository query);</li>
 *   <li>{@link #processOne(Object)} — what to do with one id, returning whether it acted.</li>
 * </ul>
 * The skeleton owns what every such job needs and used to repeat: a bounded batch, <b>failure
 * isolation</b> (one bad id is logged and skipped, never stops the batch — it's simply picked up again
 * next run), treating an optimistic-locking conflict as "someone else changed it first" rather than an
 * error, and a per-run {@link BatchRunSummary} (logged when noteworthy; override
 * {@link #onBatchCompleted} to, e.g., record a metric).
 *
 * <p><b>Transactions:</b> {@link #runBatch()} runs with none, and {@link #processOne} must not be
 * {@code @Transactional} on this class — a call from {@code runBatch} to a method of the same object
 * bypasses Spring's proxy, so the annotation would silently do nothing. Put per-item transactional
 * work in a separate bean (see {@link AbstractGuardedUpdateProcessor}) and call it from
 * {@code processOne}; that call goes through the other bean's proxy. Each id then commits or rolls
 * back on its own.
 *
 * <p><b>Scheduling stays on the subclass:</b> each job has its own interval property, which an
 * annotation on this shared class couldn't name — a subclass's {@code @Scheduled} method is just a
 * one-line call to {@link #runBatch()}. One instance per service is assumed (no distributed lock);
 * a processor that re-checks its row inside its transaction keeps a second instance harmless anyway.
 *
 * @param <ID> the id type the poll query returns
 */
@Slf4j
public abstract class AbstractBatchJob<ID> implements BatchJob {

    /** Ids per run unless {@link #batchSize()} says otherwise; the rest wait for the next run. */
    public static final int DEFAULT_BATCH_SIZE = 50;

    /**
     * One run: polls a batch and processes each id in turn. Final, so every job keeps the same
     * failure isolation — call it from your own {@code @Scheduled} method.
     *
     * @return what happened, for logging and tests
     */
    protected final BatchRunSummary runBatch() {
        List<ID> ids = pollBatch(batchSize());
        int processed = 0;
        int skipped = 0;
        int failed = 0;
        for (ID id : ids) {
            try {
                if (processOne(id)) {
                    processed++;
                } else {
                    skipped++;
                }
            } catch (OptimisticLockingFailureException e) {
                // Another writer changed the row between our load and our commit — its change stands.
                skipped++;
                log.info("{}: id={} was changed concurrently, left as is", jobName(), id);
            } catch (RuntimeException e) {
                failed++;
                log.warn("{}: id={} failed, will retry next run", jobName(), id, e);
            }
        }
        BatchRunSummary summary = new BatchRunSummary(ids.size(), processed, skipped, failed);
        onBatchCompleted(summary);
        return summary;
    }

    /** How many ids one run looks at. */
    protected int batchSize() {
        return DEFAULT_BATCH_SIZE;
    }

    /** The ids to look at this run — typically a repository query with a cutoff and a page limit. */
    protected abstract List<ID> pollBatch(int batchSize);

    /**
     * Handles one id. An exception is caught and logged by {@link #runBatch()} — no try/catch needed.
     *
     * @return {@code true} if it acted, {@code false} if the id needed nothing after all (e.g. it had
     *         moved on since the poll)
     */
    protected abstract boolean processOne(ID id);

    /** Hook after each run. The default logs a run that acted on or failed something. */
    protected void onBatchCompleted(BatchRunSummary summary) {
        if (summary.isNoteworthy()) {
            log.info("{}: {}", jobName(), summary);
        }
    }

    /** The name used in log lines — the concrete class's simple name. */
    protected String jobName() {
        return getClass().getSimpleName();
    }
}
