package com.ttg.devknowledgeplatform.ecommerce.enums;

/**
 * The aggregate root an {@code OutboxEvent} row is about.
 *
 * <p>Unlike {@code OutboxEvent.eventType} (kept as a plain string — see that entity's Javadoc),
 * this is a good enum candidate: the set of aggregate roots in this module grows slowly (one
 * value per major entity, added maybe once per epic) rather than once per business event, so the
 * "every future epic edits this shared file" cost is negligible, and a DB {@code CHECK} on it
 * only needs widening a handful of times over this module's whole lifetime.
 *
 * <p>Only {@link #PRODUCT} exists today (Epic 1). Add a new case here — and widen the
 * corresponding {@code CKC_OUTBOX_EVENT_AGGREGATE_TYPE} check constraint in a new migration —
 * only once a later epic (orders, payments, reviews) actually introduces one; don't pre-declare
 * values nothing produces yet.
 */
public enum OutboxAggregateType {
    PRODUCT
}
