package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.ecommerce.entity.Payment;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.enums.PaymentStatus;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentGatewayPort;
import com.ttg.devknowledgeplatform.ecommerce.payment.RefundResult;
import com.ttg.devknowledgeplatform.ecommerce.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Closes a known, previously-accepted money gap (a code-quality-audit follow-up, per request):
 * {@code orderstatus.PaymentHandoffService}'s own Javadoc documents that a queued cancel racing a
 * gateway success can leave a {@code Payment} row {@code SUCCEEDED} on an order that already
 * reached {@code CANCELLED} — {@code PaymentProcessingOrderStatusHandler#confirmPayment} restocks
 * and cancels the order, but nothing had ever automatically refunded the money that really was
 * captured. Epic 4 Phase 6 (US-4.6) never asked for automatic recovery here (its own literal
 * acceptance criterion is a shopper explicitly cancelling an already-{@code CONFIRMED} order, not
 * this rarer race), so this was previously left as a documented, manually-recoverable gap.
 *
 * <p>Extends {@link AbstractReconciliationJob} — the same poll-a-batch/process-each-one-tolerating-
 * failure Template Method {@link OrderReconciliationJob} uses, extracted as a follow-up once this
 * class made it the second, byte-identical instance of that shape (see that base class's own
 * Javadoc). {@link #pollBatch} queries {@link PaymentRepository#findIdsByStatusAndOrderStatus} for
 * exactly this {@code SUCCEEDED}-Payment-on-a-{@code CANCELLED}-order combination (the query's own
 * Javadoc explains why that combination can only ever mean this race — never a normal,
 * already-refunded cancel), then {@link #reconcileOne} calls
 * {@code payment.PaymentGatewayPort#refund} outside any transaction (a real network call must
 * never happen inside an open DB transaction — see this module's own established convention)
 * before applying the result via {@link PaymentCancellationService#applyRefundResult}, a
 * <i>different</i> bean's own {@code @Transactional} method. Safe to retry indefinitely if the
 * gateway call itself fails (caught and logged by {@link AbstractReconciliationJob#reconcileBatch}):
 * {@code StripePaymentGateway#refund}'s own idempotency key is deterministic (derived from
 * {@code gatewayReference}, not a fresh key per call), so a retried refund can never double-refund
 * at the gateway, and a successful {@code applyRefundResult} call turns the {@code Payment} row
 * {@code REFUNDED} — no longer matching this job's own poll query, so it's never reprocessed once
 * genuinely resolved.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RefundReconciliationJob extends AbstractReconciliationJob {

    private final PaymentRepository paymentRepository;
    private final PaymentGatewayPort paymentGatewayPort;
    private final PaymentCancellationService paymentCancellationService;

    @Scheduled(fixedDelayString = "${app.ecommerce.order.refund-reconciliation.poll-interval:PT5M}")
    public void reconcileMissedRefunds() {
        reconcileBatch();
    }

    @Override
    protected List<Integer> pollBatch(int batchSize) {
        return paymentRepository.findIdsByStatusAndOrderStatus(
                PaymentStatus.SUCCEEDED, OrderStatus.CANCELLED, PageRequest.of(0, batchSize));
    }

    @Override
    protected void reconcileOne(Integer paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        // A defensive guard against a stale poll-batch id (e.g. a concurrent
        // service.impl.OrderServiceImpl#cancel refund already resolved it moments ago) — same
        // reasoning as OrderReconciliationJob's own re-check, not a distributed-concurrency
        // mechanism (this reactor runs one instance per service today).
        if (payment == null || payment.getStatus() != PaymentStatus.SUCCEEDED) {
            return;
        }
        RefundResult result = paymentGatewayPort.refund(payment.getGatewayReference(), payment.getAmount());
        paymentCancellationService.applyRefundResult(paymentId, result);
        log.info("Refund-reconciled paymentId={} orderId={} outcome={}",
                paymentId, payment.getOrder().getId(), result.outcome());
    }
}
