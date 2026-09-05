package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.ecommerce.config.OrderJobProperties;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * US-3.4: polls for any order stuck in {@code PAYMENT_PROCESSING} beyond
 * {@code orderJobProperties.reconciliation().gracePeriod()} and reconciles each one via
 * {@link PaymentReconciliationService#reconcileNow} — recovers a crash between "payment succeeded"
 * and "order confirmed" by asking the gateway for the ground truth, rather than assuming failure
 * (which could abandon a sale the gateway actually completed) or assuming success (which could
 * confirm a sale that never happened). Also, once an order has sat that long with a genuinely
 * still-open PaymentIntent, actively cancels it as abandoned (see
 * {@link PaymentReconciliationService}'s own Javadoc for the full "auto-expire" mechanism — that
 * class now owns all of the actual per-order logic; this class only owns the poll-batch query and
 * the scheduled trigger).
 *
 * <p>No separate {@code @Transactional} processor bean is needed here the way
 * {@code outbox.OutboxEventProcessor}/{@code OrderReservationExpiryProcessor} split from their own
 * pollers: this job's own per-order work ({@link PaymentReconciliationService#reconcileNow}) never
 * calls a {@code @Transactional} method on <i>this</i> bean — {@link PaymentReconciliationService}
 * is a different bean, so calling it already goes through Spring's proxy correctly. That's exactly
 * the shape {@link AbstractReconciliationJob} (a code-quality-audit follow-up) generalizes — see
 * that class's own Javadoc for why {@link RefundReconciliationJob} shares it too, and why
 * {@code OrderReservationExpiryJob} doesn't.
 *
 * <p><b>Follow-up: {@link #reconcileOne}'s own logic — the synthetic-decline/abandonment-cancel/
 * normal-resolve branching, and every incident that shaped it — moved to
 * {@link PaymentReconciliationService}</b> once {@code service.impl.OrderServiceImpl
 * #reconcilePayment} (the GUI's on-demand "don't make the shopper wait for the next poll tick"
 * endpoint, driven by its own live countdown) needed the identical logic. See that class's own
 * Javadoc for the full detail — this class is now just the poll-batch query plus the scheduled
 * trigger, with `AbstractReconciliationJob`'s own shared per-id try/catch tolerating a failure on
 * one order without blocking the rest of the batch.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderReconciliationJob extends AbstractReconciliationJob {

    private final OrderRepository orderRepository;
    private final PaymentReconciliationService paymentReconciliationService;
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
        paymentReconciliationService.reconcileNow(orderId);
    }
}
