package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.entity.OrderLine;
import com.ttg.devknowledgeplatform.ecommerce.entity.OrderStatusHistory;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductVariantRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Shared static helpers for {@link OrderStatusHandler} implementations — the inventory
 * side-effects and the {@link OrderStatusHistory} bookkeeping every real transition needs, factored
 * out once rather than duplicated across {@code Pending}/{@code Confirmed}/etc. handlers.
 *
 * <p>Plain static methods, not a shared abstract base class: none of this logic needs instance
 * state of its own, and forcing every handler to extend a common base (accepting a
 * {@link ProductVariantRepository} it might not even need — {@code ShippedOrderStatusHandler}
 * doesn't, since {@code deliver} is a pure status transition with no inventory side effect) would
 * be exactly the unnecessary-dependency smell this module's own conventions warn against. Each
 * concrete handler instead declares only the collaborators it actually uses. (As of Epic 3 Phase 4,
 * {@code ShippedOrderStatusHandler} is the only one left without this dependency —
 * {@code PaymentProcessingOrderStatusHandler} didn't need it either back in Phase 3, before
 * {@code confirmPayment}/{@code failPayment} were added; the reasoning for keeping this a static
 * utility rather than an abstract base still holds regardless of how many handlers currently need
 * the repository, since a future handler with genuinely no inventory action is still possible.)
 */
@Slf4j
public final class OrderStatusTransitions {

    private OrderStatusTransitions() {
    }

    /**
     * Releases every line's reservation without touching {@code stockQuantity} (US-3.2/3.6) — the
     * compensating action for a reservation that never became a sale.
     */
    public static void releaseReservations(Order order, ProductVariantRepository productVariantRepository) {
        for (OrderLine line : order.getLines()) {
            int updated = productVariantRepository.release(line.getProductVariantId(), line.getQuantity());
            logIfNoRowsAffected(updated, "release", line, order);
        }
    }

    /**
     * Converts every line's reservation into a real sale (US-3.3's payment-confirmed path) —
     * decrements {@code stockQuantity} and {@code reservedQuantity} together, per the two-column
     * reservation model.
     */
    public static void confirmSaleForLines(Order order, ProductVariantRepository productVariantRepository) {
        for (OrderLine line : order.getLines()) {
            int updated = productVariantRepository.confirmSale(line.getProductVariantId(), line.getQuantity());
            logIfNoRowsAffected(updated, "confirmSale", line, order);
        }
    }

    /**
     * Restocks every line's already-sold quantity (US-3.6's {@code CONFIRMED -> CANCELLED} path) —
     * the compensating action for a reservation that *did* become a real sale via
     * {@link ProductVariantRepository#confirmSale} and now needs undoing.
     */
    public static void restockSoldLines(Order order, ProductVariantRepository productVariantRepository) {
        for (OrderLine line : order.getLines()) {
            int updated = productVariantRepository.restock(line.getProductVariantId(), line.getQuantity());
            logIfNoRowsAffected(updated, "restock", line, order);
        }
    }

    /**
     * {@code release}/{@code confirmSale}/{@code restock} all target a plain
     * {@code v.id = :variantId} column with no availability re-check the way {@code reserve} has
     * (see {@link ProductVariantRepository#reserve}'s own Javadoc) — so 0 rows affected here can
     * only mean the variant no longer exists at all ({@code OrderLine.productVariantId} is
     * deliberately not a real foreign key; {@code ProductServiceImpl.removeVariant} can hard-delete
     * one out from under an already-placed order). A DB {@code CHECK} constraint on
     * {@code reservedQuantity}'s range is real defense in depth against this class of bug going
     * further, but a stuck/mismatched reservation should still be visible somewhere instead of
     * silently no-op'ing.
     */
    private static void logIfNoRowsAffected(int rowsAffected, String operation, OrderLine line, Order order) {
        if (rowsAffected == 0) {
            log.warn("{} affected 0 rows for order id={}, productVariantId={} (variant likely deleted)",
                    operation, order.getId(), line.getProductVariantId());
        }
    }

    /**
     * Moves {@code order} to {@code to} and appends the {@link OrderStatusHistory} row documenting
     * it (US-3.5) — every real transition in this package goes through this one method so the
     * "write history alongside every status change" rule can't be forgotten on a new handler.
     * {@code reason} may be {@code null} where the transition is self-explanatory from the status
     * alone (see {@link OrderStatusHistory}'s own Javadoc).
     */
    public static void transitionTo(Order order, OrderStatus to, String reason) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setFromStatus(order.getStatus());
        history.setToStatus(to);
        history.setReason(reason);
        order.getStatusHistory().add(history);
        order.setStatus(to);
    }
}
