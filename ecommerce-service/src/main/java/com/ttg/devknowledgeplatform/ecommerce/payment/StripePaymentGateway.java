package com.ttg.devknowledgeplatform.ecommerce.payment;

import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.StripeError;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;

import com.ttg.devknowledgeplatform.ecommerce.entity.Payment;
import com.ttg.devknowledgeplatform.ecommerce.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A GoF <b>Strategy</b> (Behavioral) implementation of {@link PaymentGatewayPort} against Stripe's
 * real test-mode API, and this seam's <b>Adapter</b> (Structural) half — translating Stripe's own
 * SDK types ({@link PaymentIntent}, {@link Refund}, {@link StripeException}) into this codebase's
 * {@link PaymentResult}/{@link RefundResult} vocabulary (US-4.1).
 *
 * <p>The active {@link PaymentGatewayPort} bean is chosen by {@code app.ecommerce.payment.gateway}
 * (default {@code mock}, see {@link MockPaymentGateway}) — set it to {@code stripe} and provide
 * {@code app.ecommerce.payment.stripe.secret-key} (env var {@code STRIPE_SECRET_KEY}, a real
 * Stripe <b>test-mode</b> secret key, {@code sk_test_...}) to activate this class instead. A
 * property, not a Spring profile, drives the choice — this reactor's existing {@code local}/
 * {@code docker} profiles identify a deployment environment, not "which payment gateway is
 * configured," and either environment might reasonably run with or without real Stripe test
 * credentials on hand.
 *
 * <p><b>No real checkout UI collects a card in this reactor yet</b> — {@link #testPaymentMethodId}
 * (default {@code pm_card_visa}, one of Stripe's own built-in test-mode PaymentMethod ids that
 * always succeeds) stands in for whatever a real Stripe.js/Elements integration would eventually
 * attach. Point it at a different built-in test id (e.g. {@code pm_card_chargeDeclined}) to
 * exercise the decline path against the real API instead of {@link MockPaymentGateway}'s own
 * magic-amount sentinel.
 *
 * <p>Every call builds its own {@link RequestOptions} carrying the secret key, rather than
 * mutating the SDK's global, static {@code Stripe.apiKey} field — keeps this bean free of shared
 * mutable static state, which matters once more than one Spring context could plausibly exist in
 * the same JVM (tests, in particular).
 *
 * <p><b>{@link #checkStatus} has no native Stripe endpoint to call</b> — Stripe exposes no "look up
 * by idempotency key" query. Instead, this method replays the <i>exact same</i> {@link #charge}
 * request (same amount, currency, payment method, confirm flag) under the same
 * {@code Idempotency-Key} header; Stripe recognizes the identical fingerprint and returns its
 * original cached response rather than performing the operation again — the standard, documented
 * way to achieve an idempotent "what did you decide" query against Stripe's own API. This is why
 * this class (unlike {@link MockPaymentGateway}) needs {@link PaymentRepository}: {@code
 * checkStatus} only receives an {@code idempotencyKey}, so it looks the original {@code amount} up
 * from the {@code Payment} row Epic 4 Phase 1 guarantees was written before the original charge.
 */
@Component
@ConditionalOnProperty(prefix = "app.ecommerce.payment", name = "gateway", havingValue = "stripe")
@RequiredArgsConstructor
@Slf4j
public class StripePaymentGateway implements PaymentGatewayPort {

    private final PaymentRepository paymentRepository;

    @Value("${app.ecommerce.payment.stripe.secret-key:}")
    private String secretKey;

    @Value("${app.ecommerce.payment.stripe.currency:usd}")
    private String currency;

    @Value("${app.ecommerce.payment.stripe.test-payment-method-id:pm_card_visa}")
    private String testPaymentMethodId;

    @Override
    public PaymentResult charge(String idempotencyKey, BigDecimal amount) {
        RequestOptions options = requestOptions(idempotencyKey);
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(toSmallestCurrencyUnit(amount))
                .setCurrency(currency)
                .setPaymentMethod(testPaymentMethodId)
                .addPaymentMethodType("card")
                .setConfirm(true)
                .setOffSession(true)
                .build();
        try {
            PaymentIntent intent = PaymentIntent.create(params, options);
            return resultFromIntent(intent);
        } catch (CardException e) {
            // The synchronous-decline path: Stripe rejects the confirm attempt outright and the SDK
            // surfaces it as a thrown exception rather than a "declined" status on a returned object.
            StripeError error = e.getStripeError();
            PaymentIntent errorIntent = error != null ? error.getPaymentIntent() : null;
            String gatewayReference = errorIntent != null ? errorIntent.getId() : null;
            log.info("Stripe declined charge idempotencyKey={}: declineCode={} message={}",
                    idempotencyKey, e.getDeclineCode(), e.getMessage());
            return PaymentResult.declined(gatewayReference, StripeFailureCategoryMapper.categorize(error), e.getMessage());
        } catch (StripeException e) {
            throw new PaymentGatewayException("Stripe charge failed for idempotencyKey=" + idempotencyKey, e);
        }
    }

    @Override
    public PaymentResult checkStatus(String idempotencyKey) {
        Payment payment = paymentRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new PaymentGatewayException(
                        "No Payment row found for idempotencyKey=" + idempotencyKey));
        // Deliberately re-runs charge() with the same key/amount — see this class's own Javadoc for
        // why that's the correct way to ask Stripe "what did you already decide for this attempt."
        return charge(idempotencyKey, payment.getAmount());
    }

    @Override
    public RefundResult refund(String gatewayReference, BigDecimal amount) {
        RequestOptions options = requestOptions("refund:" + gatewayReference);
        RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(gatewayReference)
                .setAmount(toSmallestCurrencyUnit(amount))
                .build();
        try {
            Refund refund = Refund.create(params, options);
            return switch (refund.getStatus()) {
                case "succeeded" -> RefundResult.succeeded(refund.getId());
                case "pending" -> new RefundResult(RefundOutcome.PENDING, refund.getId(), null);
                default -> RefundResult.failed(refund.getId(), refund.getFailureReason());
            };
        } catch (StripeException e) {
            throw new PaymentGatewayException("Stripe refund failed for gatewayReference=" + gatewayReference, e);
        }
    }

    private RequestOptions requestOptions(String idempotencyKey) {
        return RequestOptions.builder()
                .setApiKey(secretKey)
                .setIdempotencyKey(idempotencyKey)
                .build();
    }

    private PaymentResult resultFromIntent(PaymentIntent intent) {
        return switch (intent.getStatus()) {
            case "succeeded" -> PaymentResult.succeeded(intent.getId());
            case "processing", "requires_action", "requires_confirmation", "requires_capture" ->
                    PaymentResult.pending(intent.getId());
            // Reached only if Stripe returns a non-throwing "requires_payment_method" status after
            // confirm — the CardException branch in charge() is the normal decline path; this is a
            // defensive fallback for the same outcome surfacing without an exception.
            default -> PaymentResult.declined(intent.getId(),
                    StripeFailureCategoryMapper.categorize(intent.getLastPaymentError()),
                    intent.getLastPaymentError() != null ? intent.getLastPaymentError().getMessage() : null);
        };
    }

    /**
     * Converts a {@code precision=12, scale=2} amount (this reactor's own convention for every
     * monetary column) into the smallest-unit integer Stripe's API expects (e.g. cents for
     * {@code usd}) — correct only for currencies with exactly 2 minor-unit digits, which is every
     * currency this reactor uses today.
     */
    private long toSmallestCurrencyUnit(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
