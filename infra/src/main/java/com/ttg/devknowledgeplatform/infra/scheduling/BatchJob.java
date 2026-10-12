package com.ttg.devknowledgeplatform.infra.scheduling;

/**
 * Marker interface for every {@code @Scheduled} batch job in this reactor — documentation-only, the
 * same shape as {@code infra.event.ApplicationEventHandler}/{@code infra.service.seed.Seeder}: an
 * IDE's "Find Implementations" lists every such job across every module in one view, which grepping
 * for {@code @Scheduled} can't. Concrete jobs extend {@link AbstractBatchJob} rather than
 * implementing this directly.
 *
 * <p>Replaces {@code ecommerce-service}'s former module-local {@code ReconciliationJob}, once
 * {@code dev-practice-service}'s submission sweeper became a second module with the same shape.
 */
public interface BatchJob {
}
