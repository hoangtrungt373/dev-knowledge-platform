package com.ttg.devknowledgeplatform.ecommerce.enums;

/**
 * Lifecycle status of an {@link com.ttg.devknowledgeplatform.ecommerce.entity.Order}, per the state
 * machine in {@code docs/user-stories/03-order-lifecycle-inventory.md}:
 *
 * <pre>
 *   —                                          → PENDING              (checkout confirmed; stock reserved)
 *   PENDING                                    → PAYMENT_PROCESSING   (payment attempt starts)
 *   PENDING                                    → EXPIRED              (reservation timeout, no payment attempted)
 *   PAYMENT_PROCESSING                         → CONFIRMED            (payment succeeded)
 *   PAYMENT_PROCESSING                         → FAILED               (payment declined)
 *   PENDING / PAYMENT_PROCESSING* / CONFIRMED  → CANCELLED            (shopper cancels)
 *   CONFIRMED                                  → SHIPPED              (admin ships)
 *   SHIPPED                                    → DELIVERED            (delivery confirmed; terminal)
 * </pre>
 *
 * <p>*Cancelling mid-{@code PAYMENT_PROCESSING} doesn't transition immediately — it queues via
 * {@link com.ttg.devknowledgeplatform.ecommerce.entity.Order#getCancelRequested()} and applies once
 * the in-flight payment attempt resolves, since an order can't jump to {@code CANCELLED} while a
 * gateway call is literally in progress.
 *
 * <p>All eight values were added in one pass for Epic 3, unlike {@code OutboxAggregateType}'s
 * "one value per epic, added only once actually needed" growth pattern — this state machine is
 * fully specified by a single epic's user stories (US-3.1–3.8), not something that grows
 * incrementally over the app's whole lifetime the way the outbox's aggregate-root set does.
 */
public enum OrderStatus {
    PENDING,
    PAYMENT_PROCESSING,
    CONFIRMED,
    EXPIRED,
    FAILED,
    CANCELLED,
    SHIPPED,
    DELIVERED
}
