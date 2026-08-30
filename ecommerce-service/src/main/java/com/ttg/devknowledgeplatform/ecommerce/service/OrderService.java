package com.ttg.devknowledgeplatform.ecommerce.service;

import com.ttg.devknowledgeplatform.ecommerce.entity.Order;

/**
 * Order lifecycle actions available to a shopper or admin (Epic 3, US-3.6–3.8) — thin wrappers
 * around {@code orderstatus.OrderStatusHandlerRegistry} that add the "find the order (and, for
 * {@link #cancel}, confirm the caller owns it)" step around each transition. US-3.2's reservation
 * expiry has no method here since nothing but the scheduled job (not a caller) ever triggers it —
 * see {@code orderstatus.OrderReservationExpiryJob}.
 */
public interface OrderService {

    /**
     * Cancels {@code orderId} on the caller's behalf (US-3.6) — the compensating action (release
     * vs. restock, or a queued cancel) is decided by whichever {@code OrderStatusHandler} owns the
     * order's current status.
     *
     * @param orderId    the order to cancel
     * @param callerUuid the caller's Keycloak UUID — the order must belong to this caller
     * @return the updated order
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if the order
     *         doesn't exist or doesn't belong to {@code callerUuid} (the two are indistinguishable
     *         to the caller — see {@code OrderServiceImpl}'s Javadoc)
     * @throws com.ttg.devknowledgeplatform.common.exception.BusinessException if the order's
     *         current status doesn't allow cancelling (e.g. already {@code SHIPPED})
     */
    Order cancel(Integer orderId, String callerUuid);

    /**
     * Marks {@code orderId} as shipped (US-3.7) — admin-only, enforced at the REST layer.
     *
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if not found
     * @throws com.ttg.devknowledgeplatform.common.exception.BusinessException if not currently
     *         {@code CONFIRMED}
     */
    Order ship(Integer orderId);

    /**
     * Marks {@code orderId} as delivered (US-3.8) — admin-only, enforced at the REST layer.
     *
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if not found
     * @throws com.ttg.devknowledgeplatform.common.exception.BusinessException if not currently
     *         {@code SHIPPED}
     */
    Order deliver(Integer orderId);

    /**
     * Runs the full payment handoff for {@code orderId} on the caller's behalf (US-3.3): commits
     * {@code PENDING -> PAYMENT_PROCESSING} durably, calls the payment gateway, then commits the
     * outcome (US-3.3's idempotent-handoff design — see {@code orderstatus.PaymentHandoffService}'s
     * own Javadoc for why those are two separate transactions, not one). If the gateway call itself
     * throws, the order is deliberately left {@code PAYMENT_PROCESSING} rather than force-failed —
     * that's exactly the state {@code orderstatus.OrderReconciliationJob} (US-3.4) exists to
     * recover later, not an error to swallow here.
     *
     * @param orderId    the order to attempt payment for
     * @param callerUuid the caller's Keycloak UUID — the order must belong to this caller
     * @return the order after the gateway's verdict has been applied
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if the order
     *         doesn't exist or doesn't belong to {@code callerUuid}
     * @throws com.ttg.devknowledgeplatform.common.exception.BusinessException if the order isn't
     *         currently {@code PENDING}
     */
    Order initiatePayment(Integer orderId, String callerUuid);
}
