package com.ttg.devknowledgeplatform.ecommerce.payment;

import com.ttg.devknowledgeplatform.ecommerce.enums.PaymentFailureCategory;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * A GoF <b>Strategy</b> (Behavioral) implementation of {@link PaymentGatewayPort} for {@code local}/
 * test use (US-4.1) — no real money movement, network call, or persisted state of its own, same
 * spirit as Epic 3's original {@code NoOpPaymentGatewayPort} placeholder (now deleted, superseded
 * by this class and {@code StripePaymentGateway}), but with one deliberate behavior difference:
 * {@link #charge} deterministically declines a specific "magic" amount ({@link
 * #MAGIC_DECLINE_AMOUNT}) instead of always succeeding, so a developer can exercise the
 * {@code FAILED}/decline path locally without a real gateway at all.
 *
 * <p>The active {@link PaymentGatewayPort} bean is chosen by {@code app.ecommerce.payment.gateway}
 * (default {@code mock}, so this class is what a fresh checkout of this reactor exercises without
 * any Stripe credentials configured) — see {@link StripePaymentGateway}'s own Javadoc for the other
 * strategy and why a property, not a Spring profile, drives the choice.
 */
@Component
@ConditionalOnProperty(prefix = "app.ecommerce.payment", name = "gateway", havingValue = "mock", matchIfMissing = true)
@Slf4j
public class MockPaymentGateway implements PaymentGatewayPort {

    /**
     * A charge for exactly this amount deterministically declines — an easy-to-remember sentinel
     * for manual/local testing (US-4.1), never a real catalog price point.
     */
    public static final BigDecimal MAGIC_DECLINE_AMOUNT = new BigDecimal("13.13");

    @Override
    public PaymentResult charge(String idempotencyKey, BigDecimal amount) {
        if (amount.compareTo(MAGIC_DECLINE_AMOUNT) == 0) {
            log.info("MockPaymentGateway declining idempotencyKey={} — amount matched the magic "
                    + "decline sentinel {}", idempotencyKey, MAGIC_DECLINE_AMOUNT);
            return PaymentResult.declined("mock-" + idempotencyKey, PaymentFailureCategory.CARD_DECLINED,
                    "Mock decline: amount matched the magic decline sentinel " + MAGIC_DECLINE_AMOUNT);
        }
        log.info("MockPaymentGateway approving idempotencyKey={} amount={} — no real payment "
                + "gateway is configured (app.ecommerce.payment.gateway=mock)", idempotencyKey, amount);
        return PaymentResult.succeeded("mock-" + idempotencyKey);
    }

    @Override
    public PaymentResult checkStatus(String idempotencyKey) {
        log.warn("MockPaymentGateway.checkStatus called for idempotencyKey={} — this gateway has no "
                + "real pending state (charge() always resolves synchronously), so reconciliation "
                + "should never actually need this in practice; returning SUCCEEDED unconditionally",
                idempotencyKey);
        return PaymentResult.succeeded("mock-checkstatus-" + idempotencyKey);
    }

    @Override
    public RefundResult refund(String gatewayReference, BigDecimal amount) {
        log.info("MockPaymentGateway refunding gatewayReference={} amount={}", gatewayReference, amount);
        return RefundResult.succeeded("mock-refund-" + gatewayReference);
    }

    @Override
    public PaymentCancellationResult cancelUnconfirmed(String gatewayReference) {
        log.warn("MockPaymentGateway.cancelUnconfirmed called for gatewayReference={} — this gateway "
                + "never actually leaves a charge unconfirmed in the first place (charge() always "
                + "resolves synchronously), so this should never be reached in practice; treating it "
                + "as a successful cancellation regardless", gatewayReference);
        return PaymentCancellationResult.cancelled();
    }
}
