package com.ttg.devknowledgeplatform.ecommerce.enums;

/**
 * Lifecycle state of an {@code OutboxEvent} row, as seen by the (not-yet-built) relay that polls
 * and dispatches them.
 *
 * <p>{@link #PENDING} is the only state the relay's poll query selects on — {@link #PROCESSING}
 * exists specifically to let a relay instance "claim" a row (e.g. via an atomic
 * {@code UPDATE ... SET status = 'PROCESSING' WHERE status = 'PENDING'}) so two relay instances
 * can never dispatch the same event twice. {@link #FAILED} is terminal and deliberately distinct
 * from {@link #PENDING}: without it, a permanently broken event (a poison message — bad payload,
 * a bug in its handler) would look identical to one that simply hasn't been picked up yet, and
 * would retry forever with no way to query "which events actually need attention."
 */
public enum OutboxEventStatus {
    PENDING,
    PROCESSING,
    PROCESSED,
    FAILED
}
