package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * GoF <b>Template Method</b> (Behavioral): the poll-a-batch/process-each-one-tolerating-failure
 * shape shared by every {@code @Scheduled} job in this module whose own per-item work is a plain
 * (non-{@code @Transactional}) method calling out to a <i>different</i> bean's own
 * {@code @Transactional} method — {@link OrderReconciliationJob} and {@link RefundReconciliationJob}
 * today, both previously carrying a byte-identical {@code BATCH_SIZE} constant and poll-loop/
 * try-catch-and-log shape around otherwise-different domain logic (a code-quality-audit follow-up).
 * {@link #reconcileBatch} is the template method: {@link #pollBatch} supplies the batch of ids
 * (each subclass's own repository query, whatever cutoff/status combination it needs), and
 * {@link #reconcileOne} does the actual per-id work — a poison id's exception is caught and logged
 * here, once, rather than duplicated in every subclass, so one bad row can never stop the rest of
 * the batch from reconciling.
 *
 * <p>{@code @Scheduled} itself deliberately stays on each subclass's own public method rather than
 * moving here — every job's own poll interval is a distinct property key (e.g.
 * {@code app.ecommerce.order.reconciliation.poll-interval} vs.
 * {@code app.ecommerce.order.refund-reconciliation.poll-interval}), so there's no single annotation
 * value this base class could carry for all of them; each subclass's scheduled method is just a
 * one-line call to {@link #reconcileBatch}.
 *
 * <p>{@link OrderReservationExpiryJob} deliberately does <b>not</b> extend this class, even though
 * it's also a poller: its own per-item work must be genuinely {@code @Transactional} (it directly
 * transitions an {@code Order}'s own status), so it delegates each id to a separate
 * {@code @Transactional} processor bean ({@code OrderReservationExpiryProcessor}) instead of an
 * inline try/catch — forcing it through this same template would mean either losing that
 * transactional boundary or duplicating it awkwardly around an abstract method that isn't itself
 * proxied. It implements {@link ReconciliationJob} directly instead, for the discoverability half
 * of this split without the template half.
 */
@Slf4j
public abstract class AbstractReconciliationJob implements ReconciliationJob {

    protected static final int BATCH_SIZE = 50;

    /**
     * Polls for and processes one batch of stale/drifted ids — call this from your own
     * {@code @Scheduled} method (each job's own poll interval differs, so the annotation itself
     * can't live here; see this class's own Javadoc).
     */
    protected final void reconcileBatch() {
        for (Integer id : pollBatch(BATCH_SIZE)) {
            try {
                reconcileOne(id);
            } catch (Exception e) {
                // One poison id must not stop the rest of the batch from reconciling — log and
                // move on; it stays in whatever state it was and will be retried on the next poll.
                log.warn("{} failed for id={}: {}", getClass().getSimpleName(), id, e.getMessage());
            }
        }
    }

    /** The batch of ids to reconcile this tick — implement with your own repository query. */
    protected abstract List<Integer> pollBatch(int batchSize);

    /**
     * Reconciles one id. Any exception thrown here is caught and logged by
     * {@link #reconcileBatch}, never propagated — implementations don't need their own try/catch.
     */
    protected abstract void reconcileOne(Integer id);
}
