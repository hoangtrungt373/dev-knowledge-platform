package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

/**
 * Marker interface for this module's {@code @Scheduled} batch-reconciliation jobs
 * ({@link OrderReservationExpiryJob}, {@link OrderReconciliationJob}, {@link RefundReconciliationJob}
 * today). It has no methods — its sole purpose is discoverability, the same shape as
 * {@code infra.event.ApplicationEventHandler}/{@code infra.service.seed.Seeder}/
 * {@code outbox.OutboxEventHandler}:
 * <ul>
 *   <li>In an IDE: <em>Find Implementations</em> on this interface lists every reconciliation job
 *       in one view, rather than relying on grepping for {@code @Scheduled} across the module.</li>
 *   <li>In code review or grep: {@code implements ReconciliationJob} is a clearer signal than
 *       {@code @Scheduled} hidden inside an otherwise-plain {@code @Component}.</li>
 * </ul>
 *
 * <p>Concrete jobs whose shape fits — poll a batch of ids, then process each one tolerating
 * individual failures without stopping the batch — should extend {@link AbstractReconciliationJob}
 * rather than implementing this directly, same as {@code AsyncEventHandler} sits between
 * {@code ApplicationEventHandler} and its own concrete listeners. {@link OrderReservationExpiryJob}
 * implements this directly instead: its own per-item work is delegated to a separate
 * {@code @Transactional} processor bean rather than wrapped in an inline try/catch, so it doesn't
 * fit that template — see {@link AbstractReconciliationJob}'s own Javadoc for why.
 *
 * @see AbstractReconciliationJob
 */
public interface ReconciliationJob {
}
