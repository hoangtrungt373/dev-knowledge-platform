package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductVariantRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

/**
 * {@link OrderStatus#PAYMENT_PROCESSING}'s transitions (US-3.3/3.4, Epic 3 Phase 4).
 *
 * <p>{@link #cancel} still doesn't transition — a gateway call is literally in flight, so this
 * epic's state machine queues it via {@link Order#getCancelRequested()} instead (see
 * {@link OrderStatusHandler}'s own Javadoc). {@link #confirmPayment}/{@link #failPayment} resolve
 * that in-flight attempt, and both check the queued flag: if a cancel was requested while payment
 * was processing, it wins over whatever the gateway answered — "the cancellation queues... to
 * apply once the in-flight payment resolves" (this epic's state-machine doc) means the
 * cancellation is what actually takes effect, not that it gets silently dropped just because the
 * gateway happened to answer first. A queued cancel that resolves through {@link #confirmPayment}
 * still restocks (the sale did happen, if only for a moment, and money was actually captured).
 * <b>Refunding this particular race is deliberately still not wired up</b>, even after Epic 4
 * Phase 6 (US-4.6) gave {@code ConfirmedOrderStatusHandler#cancel}'s own, far more common path a
 * real refund: US-4.6's own acceptance criterion is a shopper cancelling an already-
 * {@code CONFIRMED} order, not this narrower race (a cancel queued while payment is still
 * processing, that then loses the race to a gateway success moments later) — revisit if this gap
 * ever turns out to matter in practice, since {@code orderstatus.PaymentHandoffService
 * #resolvePayment} (this path's own caller) would need the identical "resolve the transition,
 * refund outside any transaction, apply the result" restructuring
 * {@code service.impl.OrderServiceImpl#cancel} already got. One that resolves through
 * {@link #failPayment} needed the exact same {@code release}
 * regardless, so a queued cancel there only changes the final status label from {@code FAILED} to
 * {@code CANCELLED}.
 */
@Component
@RequiredArgsConstructor
public class PaymentProcessingOrderStatusHandler implements OrderStatusHandler {

    private final ProductVariantRepository productVariantRepository;

    @Override
    public OrderStatus status() {
        return OrderStatus.PAYMENT_PROCESSING;
    }

    @Override
    public void cancel(Order order) {
        order.setCancelRequested(true);
    }

    @Override
    public void confirmPayment(Order order) {
        OrderStatusTransitions.confirmSaleForLines(order, productVariantRepository);
        if (Boolean.TRUE.equals(order.getCancelRequested())) {
            OrderStatusTransitions.restockSoldLines(order, productVariantRepository);
            OrderStatusTransitions.transitionTo(order, OrderStatus.CANCELLED,
                    "Payment succeeded while a cancellation was pending — this race isn't wired to "
                            + "an automatic refund yet (see this handler's own Javadoc)");
        } else {
            OrderStatusTransitions.transitionTo(order, OrderStatus.CONFIRMED, null);
        }
    }

    @Override
    public void failPayment(Order order) {
        OrderStatusTransitions.releaseReservations(order, productVariantRepository);
        if (Boolean.TRUE.equals(order.getCancelRequested())) {
            OrderStatusTransitions.transitionTo(order, OrderStatus.CANCELLED,
                    "Cancelled while payment was processing — the payment attempt also failed");
        } else {
            OrderStatusTransitions.transitionTo(order, OrderStatus.FAILED, "Payment declined by the gateway");
        }
    }
}
