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
 * <p><b>Refunding the payment itself deliberately isn't done here</b> — this handler only ever
 * runs inside {@code orderstatus.PaymentHandoffService#applyCancellation}'s own transaction, and a
 * real gateway call must never happen inside an open DB transaction (see that method's own
 * Javadoc). {@code service.impl.OrderServiceImpl#cancel} is what actually issues the refund
 * (Epic 4 Phase 6, US-4.6), afterward and outside any transaction, once
 * {@code PaymentHandoffService#applyCancellation} reports one is owed — this handler's own job
 * stays scoped to the inventory/status side only, same as before that phase existed.
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
                "Cancelled by shopper after payment was confirmed — a refund is issued by the caller "
                        + "once this transaction commits (US-4.6)");
    }

    @Override
    public void ship(Order order) {
        OrderStatusTransitions.transitionTo(order, OrderStatus.SHIPPED, null);
    }
}
