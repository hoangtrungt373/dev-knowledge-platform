package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.ecommerce.config.OrderJobProperties;
import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.enums.PaymentFailureCategory;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentGatewayPort;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentOutcome;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentResult;
import com.ttg.devknowledgeplatform.ecommerce.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * US-3.4: re-checks the actual gateway outcome for any order stuck in {@code PAYMENT_PROCESSING}
 * beyond {@code orderJobProperties.reconciliation().gracePeriod()} — recovers a crash between
 * "payment succeeded" and "order confirmed" by asking {@link PaymentGatewayPort#checkStatus} for
 * the ground truth, rather than assuming failure (which could abandon a sale the gateway actually
 * completed) or assuming success (which could confirm a sale that never happened).
 *
 * <p>No separate {@code @Transactional} processor bean is needed here the way
 * {@code outbox.OutboxEventProcessor}/{@code OrderReservationExpiryProcessor} split from their own
 * pollers: this job's own per-order work
 * ({@link PaymentGatewayPort#checkStatus} then {@link PaymentHandoffService#resolvePayment}) never
 * calls a {@code @Transactional} method on <i>this</i> bean — {@link PaymentHandoffService} is a
 * different bean, so calling it already goes through Spring's proxy correctly. A self-invocation
 * split would only be needed if this class itself had a {@code @Transactional} method being called
 * from another method on the same instance. That's exactly the shape {@link AbstractReconciliationJob}
 * (a code-quality-audit follow-up) now generalizes — see that class's own Javadoc for why
 * {@link RefundReconciliationJob} shares it too, and why {@code OrderReservationExpiryJob} doesn't.
 *
 * <p><b>Follow-up: {@link #reconcileOne} now also closes the one real gap in this whole
 * reconciliation story — a charge attempt that crashes before ever reaching Stripe.</b>
 * {@code payment.StripePaymentGateway#checkStatus}'s own Javadoc documents that a {@code Payment}
 * row with no {@code gatewayReference} at all (e.g. {@code payment.PaymentGatewayPort#charge}
 * threw before Stripe ever created a {@code PaymentIntent}) always reports back
 * {@link PaymentOutcome#PENDING} with a {@code null} {@code gatewayReference} — and, before this
 * fix, that meant this job polled it forever with no terminal exit: {@code resolvePayment}'s own
 * {@code PENDING} branch is always a no-op, and even an explicit shopper cancel couldn't escape it
 * either (see {@code orderstatus.PaymentCancellationService#applyCancellation}'s own
 * {@code gatewayReference != null} guard — a null one reports {@code gatewayCancellationNeeded()
 * == false}, so the cancel just queues silently with no visible effect). A
 * {@link PaymentOutcome#PENDING} result with a {@code null} {@code gatewayReference} is uniquely
 * produced by exactly this "never reached the gateway" case — every other still-processing
 * outcome (a real, live {@code PaymentIntent} Stripe is still working through) always carries a
 * real, non-{@code null} {@code gatewayReference} — so {@link #reconcileOne} can safely tell the
 * two apart. Once this job has already waited a full grace period and still sees no
 * {@code gatewayReference}, there is nothing left to ever retrieve at Stripe (the attempt never
 * got that far), so it finalizes with a synthetic {@link PaymentResult#declined} instead of
 * polling forever — the shopper can then simply reorder, and if they'd already queued a cancel,
 * {@code PaymentProcessingOrderStatusHandler#failPayment}'s own {@code cancelRequested}-wins rule
 * correctly lands the order {@code CANCELLED} instead of {@code FAILED}, exactly as they asked.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderReconciliationJob extends AbstractReconciliationJob {

    private final OrderRepository orderRepository;
    private final PaymentGatewayPort paymentGatewayPort;
    private final PaymentHandoffService paymentHandoffService;
    private final OrderJobProperties orderJobProperties;

    @Scheduled(fixedDelayString = "${app.ecommerce.order.reconciliation.poll-interval:PT1M}")
    public void reconcileStuckPayments() {
        reconcileBatch();
    }

    @Override
    protected List<Integer> pollBatch(int batchSize) {
        Instant cutoff = Instant.now().minus(orderJobProperties.reconciliation().gracePeriod());
        return orderRepository.findIdsByStatusAndPaymentProcessingStartedAtBefore(
                OrderStatus.PAYMENT_PROCESSING, cutoff, PageRequest.of(0, batchSize));
    }

    @Override
    protected void reconcileOne(Integer orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        // A defensive guard against a stale poll-batch id (e.g. it already resolved via the
        // synchronous initiatePayment flow moments ago) — same reasoning as
        // OrderReservationExpiryProcessor's own re-check, not a distributed-concurrency
        // mechanism (this reactor runs one instance per service today).
        if (order == null || order.getStatus() != OrderStatus.PAYMENT_PROCESSING) {
            return;
        }
        PaymentResult result = paymentGatewayPort.checkStatus(order.getIdempotencyKey());
        if (result.outcome() == PaymentOutcome.PENDING && result.gatewayReference() == null) {
            // The charge attempt never reached the gateway at all (see this class's own Javadoc
            // for the full incident) — there's nothing left to ever retrieve, so leaving this
            // PENDING would poll forever with no terminal exit. Past the grace period already
            // (this method only runs for orders stuck that long), give up and finalize instead.
            log.warn("Order id={} idempotencyKey={} stuck PAYMENT_PROCESSING past the grace period with no "
                    + "gatewayReference ever recorded — finalizing as a synthetic decline",
                    orderId, order.getIdempotencyKey());
            result = PaymentResult.declined(null, PaymentFailureCategory.GATEWAY_ERROR,
                    "Payment attempt never reached the payment gateway");
        }
        paymentHandoffService.resolvePayment(orderId, result);
        log.info("Reconciled order id={} idempotencyKey={} outcome={}",
                orderId, order.getIdempotencyKey(), result.outcome());
    }
}
