package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductVariantRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

/**
 * {@link OrderStatus#CONFIRMED}'s transitions: admin ship-out (US-3.7) and a shopper cancelling
 * after payment has already succeeded (US-3.6). The latter's compensation genuinely differs from
 * {@link PendingOrderStatusHandler#cancel}'s: stock here was already converted from a reservation
 * into a real sale (via {@code ProductVariantRepository.confirmSale}, in Epic 3 Phase 4's
 * payment-success path), so undoing it means restocking, not releasing a reservation that no
 * longer exists — see {@link OrderStatusTransitions#restockSoldLines}.
 *
 * <p><b>Refunding the payment itself is deliberately not done here</b> — there is no payment
 * gateway to call yet (Epic 4). The inventory side of "cancel after confirmation" is real and built
 * now; the money side is only recorded as a note on the {@code OrderStatusHistory} row, ready to be
 * wired to an actual refund call once Epic 4's gateway integration exists.
 */
@Component
@RequiredArgsConstructor
public class ConfirmedOrderStatusHandler implements OrderStatusHandler {

    private final ProductVariantRepository productVariantRepository;

    @Override
    public OrderStatus status() {
        return OrderStatus.CONFIRMED;
    }

    @Override
    public void cancel(Order order) {
        OrderStatusTransitions.restockSoldLines(order, productVariantRepository);
        OrderStatusTransitions.transitionTo(order, OrderStatus.CANCELLED,
                "Cancelled by shopper after payment was confirmed — refund handling is deferred "
                        + "to Epic 4's payment-gateway integration");
    }

    @Override
    public void ship(Order order) {
        OrderStatusTransitions.transitionTo(order, OrderStatus.SHIPPED, null);
    }
}
