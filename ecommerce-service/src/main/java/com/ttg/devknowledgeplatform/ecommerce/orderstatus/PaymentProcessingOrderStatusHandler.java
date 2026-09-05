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
 * <b>Refunding this particular race synchronously is still not wired up</b> — Epic 4 Phase 6
 * (US-4.6) gave {@code ConfirmedOrderStatusHandler#cancel}'s own, far more common path a real
 * <i>synchronous</i> refund, but US-4.6's own acceptance criterion is a shopper cancelling an
 * already-{@code CONFIRMED} order, not this narrower race (a cancel queued while payment is still
 * processing, that then loses the race to a gateway success moments later); wiring a synchronous
 * refund into this path would need {@code orderstatus.PaymentHandoffService#resolvePayment} (this
 * path's own caller) to take on the identical "resolve the transition, refund outside any
 * transaction, apply the result" restructuring {@code service.impl.OrderServiceImpl#cancel}
 * already got, which nothing has asked for. A code-quality-audit follow-up closed the actual money
 * gap a different way instead, without that restructuring: {@link RefundReconciliationJob} polls
 * for exactly this end state (a {@code Payment} row {@code SUCCEEDED} on an order that reached
 * {@code CANCELLED}) and applies the missed refund asynchronously — not instant, but the money no
 * longer sits unrefunded forever. One that resolves through {@link #failPayment} needed the exact
 * same {@code release} regardless, so a queued cancel there only changes the final status label
 * from {@code FAILED} to {@code CANCELLED}.
 *
 * <p><b>Follow-up: {@link #expire} closes the "shopper simply abandoned the payment dialog"
 * gap</b> {@link #cancel}'s own Javadoc used to flag as accepted/unrecovered — no card decline, no
 * webhook, no explicit cancel click, just a still-open Stripe PaymentIntent nobody ever finishes.
 * {@code OrderReconciliationJob} now actively cancels that intent at the gateway once an order has
 * sat past a much longer "abandonment" window (folded into that same job rather than a new
 * poller — see its own Javadoc), and dispatches here. Mirrors {@link #failPayment}'s own
 * release-and-respect-{@code cancelRequested} shape (nothing was ever sold at this stage — only
 * {@link #confirmPayment} calls {@code confirmSale} — so releasing the reservation, not
 * restocking, is the correct compensating action either way), but lands on {@code EXPIRED} for the
 * ordinary case, not {@code FAILED}: {@code EXPIRED} already means "the system gave up because
 * nobody finished in time" ({@link PendingOrderStatusHandler#expire}'s own pre-payment
 * counterpart), while {@code FAILED} specifically means the gateway declined a charge — nothing
 * was ever declined here, the shopper simply walked away. Still checks {@code cancelRequested}
 * first, for the rare case where an explicit cancel was already queued but its own
 * {@code gatewayCancellationNeeded} follow-up hadn't run yet (e.g. a transient gateway outage) by
 * the time this job's own, much longer window elapsed — that's still "the shopper asked," so it
 * still lands on {@code CANCELLED}, same as {@link #failPayment}'s own equivalent branch.
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
    public void expire(Order order) {
        OrderStatusTransitions.releaseReservations(order, productVariantRepository);
        if (Boolean.TRUE.equals(order.getCancelRequested())) {
            OrderStatusTransitions.transitionTo(order, OrderStatus.CANCELLED,
                    "Cancelled while payment was processing — the shopper never completed the payment attempt");
        } else {
            OrderStatusTransitions.transitionTo(order, OrderStatus.EXPIRED,
                    "Payment was never completed — the checkout appears to have been abandoned");
        }
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
