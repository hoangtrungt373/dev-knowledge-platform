package com.ttg.devknowledgeplatform.ecommerce.payment;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;

import com.ttg.devknowledgeplatform.ecommerce.config.PaymentProperties;
import com.ttg.devknowledgeplatform.ecommerce.entity.Payment;
import com.ttg.devknowledgeplatform.ecommerce.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
 * <p><b>Option A (Stripe Elements, client-side confirmation) — {@link #charge} creates an
 * unconfirmed {@link PaymentIntent} and hands its {@code client_secret} back via
 * {@link PaymentResult#clientSecret()}; it never confirms the charge itself.</b> The GUI mounts a
 * {@code PaymentElement} against that secret and calls {@code stripe.confirmPayment} client-side —
 * the shopper's real card never reaches this backend at all (see root {@code CLAUDE.md}'s Stripe
 * discussion for why {@code off_session}/a hardcoded test PaymentMethod, this class's own original
 * shape, isn't a real payment flow: no card was ever collected for it to charge). Once the shopper
 * confirms, Stripe resolves the {@code PaymentIntent} asynchronously and calls
 * {@code webhook.StripeWebhookService} — the actual source of truth for
 * {@code SUCCEEDED}/{@code DECLINED}, not this method's own return value, which is
 * {@link PaymentOutcome#PENDING} for every fresh, not-yet-confirmed charge (see
 * {@link #resultFromIntent}). {@link #charge} is safe to call more than once for the same order —
 * it's always keyed by the same {@code idempotencyKey}, so Stripe's own idempotency-key mechanism
 * returns the identical cached {@link PaymentIntent} (same id, same client secret) rather than
 * creating a second one — this is what lets {@code service.impl.OrderServiceImpl#initiatePayment}
 * be re-entrant while an order sits {@code PAYMENT_PROCESSING} (e.g. the shopper reopens the
 * payment dialog before completing it), with no new column/state needed to remember the first
 * attempt's own client secret.
 *
 * <p>Every call builds its own {@link RequestOptions} carrying the secret key, rather than
 * mutating the SDK's global, static {@code Stripe.apiKey} field — keeps this bean free of shared
 * mutable static state, which matters once more than one Spring context could plausibly exist in
 * the same JVM (tests, in particular).
 *
 * <p><b>{@link #checkStatus} retrieves the {@link PaymentIntent} by id — it does <i>not</i> replay
 * {@link #charge}.</b> An earlier revision of this class did replay {@code charge()} under the same
 * {@code Idempotency-Key}, on the (then-correct) theory that Stripe's idempotency mechanism would
 * hand back "what did you decide." That relied on {@link #charge}'s own original response already
 * being terminal, which was true back when {@code charge()} called {@code setConfirm(true)}
 * synchronously — it stopped being true once {@code charge()} was rebuilt around Option A's
 * client-side confirmation (see above): Stripe's idempotent replay returns the exact response
 * <i>captured at the original {@code create()} call</i>, never a live re-fetch, so replaying a
 * since-confirmed intent's original {@code create()} call would keep returning its permanently
 * frozen {@code requires_payment_method} snapshot forever, regardless of what the shopper's own
 * {@code stripe.confirmPayment()} call already resolved on Stripe's side. A plain
 * {@code PaymentIntent.retrieve(id)} (a GET, not a replayed POST) is what actually answers "what is
 * this intent's status right now." This is why this class (unlike {@link MockPaymentGateway}) needs
 * {@link PaymentRepository}: {@code checkStatus} only receives an {@code idempotencyKey}, so it
 * looks the original charge's {@code gatewayReference} (the PaymentIntent id) up from the
 * {@code Payment} row Epic 4 Phase 1 guarantees was written before the original charge.
 *
 * <p><b>{@link #cancelUnconfirmed} closes the gap Option A opened for an explicit shopper cancel.</b>
 * Before this method existed, cancelling an order while its PaymentIntent still awaited the
 * shopper's own {@code stripe.confirmPayment()} call only ever queued {@code Order.cancelRequested}
 * — nothing would resolve it afterward, since no webhook is coming (the shopper never confirmed)
 * and every reconciliation poll just kept re-reporting {@link PaymentOutcome#PENDING} forever. This
 * method retrieves the intent and calls its own {@code cancel}, voiding it at Stripe outright; if
 * Stripe rejects the cancel because the intent already reached a real terminal state (the shopper
 * confirmed on another tab a moment earlier), a second retrieve reports that real outcome instead of
 * masking it as a cancellation that never actually happened.
 */
@Component
@ConditionalOnProperty(prefix = "app.ecommerce.payment", name = "gateway", havingValue = "stripe")
@RequiredArgsConstructor
@Slf4j
public class StripePaymentGateway implements PaymentGatewayPort {

    private final PaymentRepository paymentRepository;
    private final PaymentProperties paymentProperties;

    @Override
    public PaymentResult charge(String idempotencyKey, BigDecimal amount) {
        RequestOptions options = requestOptions(idempotencyKey);
        // No setConfirm/setPaymentMethod/setOffSession — Option A leaves confirmation to the
        // shopper's own browser via stripe.confirmPayment against this intent's client_secret (see
        // class Javadoc). automaticPaymentMethods lets Stripe itself decide which payment methods
        // (card, wallets, etc.) to surface on the PaymentElement, rather than this backend hardcoding
        // "card" the way the old off_session flow did.
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(toSmallestCurrencyUnit(amount))
                .setCurrency(paymentProperties.stripe().currency())
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build())
                .build();
        try {
            PaymentIntent intent = PaymentIntent.create(params, options);
            return resultFromIntent(intent);
        } catch (StripeException e) {
            throw new PaymentGatewayException("Stripe charge failed for idempotencyKey=" + idempotencyKey, e);
        }
    }

    @Override
    public PaymentResult checkStatus(String idempotencyKey) {
        Payment payment = paymentRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new PaymentGatewayException(
                        "No Payment row found for idempotencyKey=" + idempotencyKey));
        if (payment.getGatewayReference() == null) {
            // charge() never even reached Stripe for this attempt (e.g. crashed before the create
            // call returned) — nothing to look up yet; leave it PENDING for the next poll.
            // orderstatus.OrderReconciliationJob#reconcileOne is what eventually gives up on this
            // exact case (a PENDING result with a null gatewayReference is uniquely this scenario —
            // every other still-processing outcome always carries a real one) and finalizes with a
            // synthetic decline instead of polling forever — see that method's own Javadoc.
            return PaymentResult.pending(null, null);
        }
        try {
            // A live retrieve by PaymentIntent id — NOT a replayed charge() call. Option A's charge()
            // no longer confirms synchronously, so Stripe's idempotency mechanism (which returns the
            // frozen response captured at the ORIGINAL create() call, never a live re-fetch) would
            // otherwise hand back the same permanently-unconfirmed "requires_payment_method" snapshot
            // forever, regardless of whatever the shopper's own stripe.confirmPayment() call already
            // resolved on Stripe's side. Retrieving the intent by id is the only way to see its
            // current, possibly-since-changed status.
            PaymentIntent intent = PaymentIntent.retrieve(
                    payment.getGatewayReference(),
                    RequestOptions.builder().setApiKey(paymentProperties.stripe().secretKey()).build());
            return resultFromIntent(intent);
        } catch (StripeException e) {
            throw new PaymentGatewayException(
                    "Stripe retrieve failed for gatewayReference=" + payment.getGatewayReference(), e);
        }
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

    @Override
    public PaymentCancellationResult cancelUnconfirmed(String gatewayReference) {
        RequestOptions options = RequestOptions.builder().setApiKey(paymentProperties.stripe().secretKey()).build();
        try {
            PaymentIntent intent = PaymentIntent.retrieve(gatewayReference, options);
            intent.cancel(options);
            return PaymentCancellationResult.cancelled();
        } catch (StripeException e) {
            // Stripe rejects cancelling an intent that's already reached a real terminal state —
            // most plausibly the shopper confirmed on another tab a moment before this call
            // arrived. Re-check the intent's actual current status instead of assuming a genuine
            // gateway failure, so a real success/decline that just beat this cancel to the punch
            // isn't discarded.
            PaymentIntent current;
            try {
                current = PaymentIntent.retrieve(gatewayReference, options);
            } catch (StripeException retrieveFailure) {
                throw new PaymentGatewayException(
                        "Stripe cancel failed for gatewayReference=" + gatewayReference, e);
            }
            PaymentResult result = resultFromIntent(current);
            if (result.outcome() == PaymentOutcome.PENDING) {
                // Still genuinely unresolved and still not cancelable either — not the benign race
                // above, so surface it as a real failure rather than silently pretending success.
                throw new PaymentGatewayException(
                        "Stripe cancel failed for gatewayReference=" + gatewayReference, e);
            }
            return PaymentCancellationResult.alreadyResolved(result);
        }
    }

    private RequestOptions requestOptions(String idempotencyKey) {
        return RequestOptions.builder()
                .setApiKey(paymentProperties.stripe().secretKey())
                .setIdempotencyKey(idempotencyKey)
                .build();
    }

    private PaymentResult resultFromIntent(PaymentIntent intent) {
        return switch (intent.getStatus()) {
            case "succeeded" -> PaymentResult.succeeded(intent.getId());
            // "requires_payment_method" is the intent's normal starting status now that charge()
            // never confirms — it means "created, waiting on the shopper's own browser to confirm
            // it," not a decline; every other non-terminal status covers a card genuinely mid-flight
            // (e.g. a 3DS challenge in progress).
            case "requires_payment_method", "requires_confirmation", "requires_action", "requires_capture",
                    "processing" -> PaymentResult.pending(intent.getId(), intent.getClientSecret());
            // "canceled" (or any other terminal-but-not-succeeded status) — a definitive decline.
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
