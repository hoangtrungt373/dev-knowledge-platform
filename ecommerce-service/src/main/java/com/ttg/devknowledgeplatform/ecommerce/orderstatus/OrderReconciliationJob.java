package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentGatewayPort;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentOutcome;
import com.ttg.devknowledgeplatform.ecommerce.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * US-3.4: re-checks the actual gateway outcome for any order stuck in {@code PAYMENT_PROCESSING}
 * beyond {@link #gracePeriod} — recovers a crash between "payment succeeded" and "order confirmed"
 * by asking {@link PaymentGatewayPort#checkStatus} for the ground truth, rather than assuming
 * failure (which could abandon a sale the gateway actually completed) or assuming success (which
 * could confirm a sale that never happened).
 *
 * <p>No separate {@code @Transactional} processor bean is needed here the way
 * {@code outbox.OutboxEventProcessor}/{@code OrderReservationExpiryProcessor} split from their own
 * pollers: this job's own per-order work
 * ({@link PaymentGatewayPort#checkStatus} then {@link PaymentHandoffService#resolvePayment}) never
 * calls a {@code @Transactional} method on <i>this</i> bean — {@link PaymentHandoffService} is a
 * different bean, so calling it already goes through Spring's proxy correctly. A self-invocation
 * split would only be needed if this class itself had a {@code @Transactional} method being called
 * from another method on the same instance.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderReconciliationJob {

    private static final int BATCH_SIZE = 50;

    private final OrderRepository orderRepository;
    private final PaymentGatewayPort paymentGatewayPort;
    private final PaymentHandoffService paymentHandoffService;

    @Value("${app.ecommerce.order.reconciliation.grace-period:PT2M}")
    private Duration gracePeriod;

    @Scheduled(fixedDelayString = "${app.ecommerce.order.reconciliation.poll-interval:PT1M}")
    public void reconcileStuckPayments() {
        Instant cutoff = Instant.now().minus(gracePeriod);
        List<Integer> stuckIds = orderRepository.findIdsByStatusAndPaymentProcessingStartedAtBefore(
                OrderStatus.PAYMENT_PROCESSING, cutoff, PageRequest.of(0, BATCH_SIZE));
        for (Integer id : stuckIds) {
            reconcileOne(id);
        }
    }

    private void reconcileOne(Integer orderId) {
        try {
            Order order = orderRepository.findById(orderId).orElse(null);
            // A defensive guard against a stale poll-batch id (e.g. it already resolved via the
            // synchronous initiatePayment flow moments ago) — same reasoning as
            // OrderReservationExpiryProcessor's own re-check, not a distributed-concurrency
            // mechanism (this reactor runs one instance per service today).
            if (order == null || order.getStatus() != OrderStatus.PAYMENT_PROCESSING) {
                return;
            }
            PaymentOutcome outcome = paymentGatewayPort.checkStatus(order.getIdempotencyKey());
            paymentHandoffService.resolvePayment(orderId, outcome);
            log.info("Reconciled order id={} idempotencyKey={} outcome={}", orderId, order.getIdempotencyKey(), outcome);
        } catch (Exception e) {
            // One poison order must not stop the rest of the batch from reconciling — log and move
            // on; it stays PAYMENT_PROCESSING and will be retried on the next poll tick.
            log.warn("Reconciliation failed for order id={}: {}", orderId, e.getMessage());
        }
    }
}
