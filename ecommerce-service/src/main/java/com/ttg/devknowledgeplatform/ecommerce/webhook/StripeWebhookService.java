package com.ttg.devknowledgeplatform.ecommerce.webhook;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeError;
import com.stripe.net.Webhook;

import com.ttg.devknowledgeplatform.ecommerce.config.PaymentProperties;
import com.ttg.devknowledgeplatform.ecommerce.entity.Payment;
import com.ttg.devknowledgeplatform.ecommerce.entity.StripeWebhookEvent;
import com.ttg.devknowledgeplatform.ecommerce.enums.PaymentFailureCategory;
import com.ttg.devknowledgeplatform.ecommerce.enums.PaymentStatus;
import com.ttg.devknowledgeplatform.ecommerce.orderstatus.PaymentHandoffService;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentResult;
import com.ttg.devknowledgeplatform.ecommerce.payment.StripeFailureCategoryMapper;
import com.ttg.devknowledgeplatform.ecommerce.repository.PaymentRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.StripeWebhookEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Receives and processes Stripe webhook events (US-4.5) — {@code payment_intent.succeeded}/
 * {@code payment_intent.payment_failed}/{@code payment_intent.canceled}, so a payment outcome that
 * never resolved synchronously (US-4.3) still gets recorded correctly. Feeds Epic 3's
 * reconciliation directly: webhook-derived {@code Payment} state is ground truth, independent of
 * whether the synchronous {@code service.impl.OrderServiceImpl#initiatePayment} call ever got a
 * response at all.
 *
 * <p><b>Code-quality-audit follow-up: {@code payment_intent.canceled} closes the "cap retries, then
 * finalize" gap by reusing Stripe's own confirmation-limit — never a custom retry counter.</b>
 * Stripe's own {@code confirm} endpoint documents "a variable upper limit on how many times a
 * PaymentIntent can be confirmed. After this limit is reached, any further calls... transition the
 * PaymentIntent to the {@code canceled} state" — this is Stripe's own anti-card-testing posture,
 * already enforced server-side; this reactor doesn't need (and shouldn't build) a second,
 * possibly-disagreeing counter to duplicate it. {@link #applyPaymentIntentEvent} now treats this
 * event as a genuine final decline (the exhausted PaymentIntent is over, not retryable) —
 * previously, {@code payment.StripePaymentGateway#resultFromIntent}'s own {@code checkStatus}
 * poll already classified a {@code "canceled"} intent as a decline (its {@code default} switch
 * arm), so {@code OrderReconciliationJob} would eventually catch this on its next poll tick
 * regardless; this webhook handler just reacts to it immediately instead of waiting out the
 * grace-period/poll-interval.
 *
 * <p><b>Guard against misclassifying our own explicit cancel as a decline.</b>
 * {@code service.impl.OrderServiceImpl#cancel}'s own {@code payment.PaymentGatewayPort
 * #cancelUnconfirmed} call (a shopper explicitly cancelling while a PaymentIntent is still
 * unconfirmed) also transitions the intent to {@code canceled} and fires this identical webhook
 * event as a side effect — but that flow already resolves the {@code Payment} row itself, correctly,
 * via {@code orderstatus.PaymentCancellationService#applyGatewayCancellation} (marking it
 * {@code CANCELLED}, not {@code DECLINED}), synchronously within the same request. Stripe's own
 * {@code cancellation_reason} field could in principle distinguish the two triggers, but this
 * reactor doesn't lean on that (unverified for this exact case, and a second, indirect signal to
 * trust). Instead, {@link #applyPaymentIntentEvent} only treats {@code payment_intent.canceled} as
 * a final decline when the {@code Payment} row is still {@code PENDING} — if it's already
 * {@code CANCELLED} (or resolved any other way), this event is a safe no-op, since something else
 * already handled it. This fully closes the common case; a narrow race remains if this webhook
 * event is delivered and processed in the brief window before {@code applyGatewayCancellation}'s
 * own transaction commits — the order still correctly ends {@code CANCELLED} either way (via
 * {@code PaymentProcessingOrderStatusHandler#failPayment}'s own {@code cancelRequested}-wins rule,
 * since the shopper's cancel request is what's driving this in the first place), but the
 * {@code Payment} row could read {@code DECLINED} instead of {@code CANCELLED} in that specific
 * rare window — a known, accepted imprecision in the audit trail, not a functional bug.
 *
 * <p><b>Signature verification, dedup, and the actual update all happen inside one transaction</b>
 * — {@link #handleWebhook} is the single {@code @Transactional} entry point. It calls
 * {@link PaymentHandoffService#resolvePayment}, a <i>different</i> bean's own {@code @Transactional}
 * method — Spring's default {@code REQUIRED} propagation makes that call join this method's
 * already-open transaction rather than start a new one, so the dedup-ledger insert (see
 * {@link #applyPaymentIntentEvent}), the {@code Payment}/{@code Order} update, and the
 * {@code PAYMENT_SUCCEEDED}/{@code PAYMENT_FAILED} outbox publish (US-4.4) all commit or roll back
 * together — exactly the "write the outbox event + update Payment/Order atomically inside the
 * webhook handler" shape US-4.5 calls for. This only works because it's a call to a genuinely
 * different bean's proxy, not {@code this.foo()} self-invocation — see
 * {@code outbox.OutboxEventProcessor}'s own Javadoc for why that distinction matters in this
 * reactor.
 *
 * <p>{@link #applyPaymentIntentEvent} is deliberately package-private and takes plain Java
 * primitives/enums rather than Stripe's own {@link Event}/{@link PaymentIntent} SDK types — those
 * are only ever touched by {@link #handleWebhook} itself (the thin SDK-facing glue, left
 * unverified-at-runtime the same way {@code payment.StripePaymentGateway} is, since neither can be
 * meaningfully exercised without a real Stripe test account). Splitting the dedup/correlation/
 * resolve logic out this way is what makes it unit-testable at all — constructing a real,
 * fully-functional Stripe {@code Event} object (backed by its own {@code EventDataObjectDeserializer})
 * in a plain Mockito test is impractical.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookService {

    private static final String PAYMENT_INTENT_SUCCEEDED = "payment_intent.succeeded";
    private static final String PAYMENT_INTENT_PAYMENT_FAILED = "payment_intent.payment_failed";
    private static final String PAYMENT_INTENT_CANCELED = "payment_intent.canceled";
    private static final Set<String> HANDLED_EVENT_TYPES =
            Set.of(PAYMENT_INTENT_SUCCEEDED, PAYMENT_INTENT_PAYMENT_FAILED, PAYMENT_INTENT_CANCELED);

    private final StripeWebhookEventRepository stripeWebhookEventRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentHandoffService paymentHandoffService;
    private final PaymentProperties paymentProperties;

    /**
     * Verifies {@code signatureHeader} against {@code rawPayload} (US-4.5's own "verify Stripe's
     * signature before trusting the payload"), then dispatches whichever event types this reactor
     * cares about. {@code rawPayload} must be the exact, unmodified request body bytes — HMAC
     * verification is byte-sensitive, which is why the controller binds it as {@code byte[]}
     * rather than letting a JSON message converter touch it first.
     *
     * @throws SignatureVerificationException if the signature doesn't match — the controller maps
     *         this to a {@code 400}, never processing an unverified payload
     */
    @Transactional(rollbackFor = Throwable.class)
    public void handleWebhook(byte[] rawPayload, String signatureHeader) throws SignatureVerificationException {
        Event event = Webhook.constructEvent(
                new String(rawPayload, StandardCharsets.UTF_8), signatureHeader, paymentProperties.stripe().webhookSecret());

        if (!HANDLED_EVENT_TYPES.contains(event.getType())) {
            log.info("Ignoring unhandled Stripe webhook event type={} id={}", event.getType(), event.getId());
            return;
        }

        PaymentIntent intent = extractPaymentIntent(event);
        // A canceled intent can still carry a real last_payment_error (e.g. Stripe's own
        // confirmation-limit auto-cancel happens right after the final attempt's own decline), so
        // this reactor's shopper-facing failure detail comes from the same place either way.
        boolean carriesFailureDetail =
                PAYMENT_INTENT_PAYMENT_FAILED.equals(event.getType()) || PAYMENT_INTENT_CANCELED.equals(event.getType());
        StripeError error = carriesFailureDetail ? intent.getLastPaymentError() : null;
        applyPaymentIntentEvent(event.getId(), event.getType(), intent.getId(),
                error == null ? null : StripeFailureCategoryMapper.categorize(error),
                error == null ? null : error.getMessage());
    }

    /**
     * The actual dedup/correlate/resolve logic (US-4.5), independent of any Stripe SDK type — see
     * the class Javadoc for why this split exists. Package-private so a test in this same package
     * can call it directly.
     *
     * <p><b>{@code payment_intent.payment_failed} resolves as {@link PaymentResult#attemptFailed},
     * not {@link PaymentResult#declined}</b> — a bug fix (see that factory's own Javadoc for the
     * full incident): this event means the shopper's own attempt just failed, not that the charge
     * is over. The {@code PaymentIntent} itself drops back to {@code "requires_payment_method"} —
     * still open for another try with a different card — so treating it as a final decline used to
     * finalize the order to {@code FAILED} on the shopper's very first mistyped card, permanently
     * blocking them from ever completing it with a working one afterward.
     *
     * <p><b>{@code payment_intent.canceled} resolves as {@link PaymentResult#declined} instead — the
     * opposite of {@code payment_failed} above — but only when {@code payment.getStatus()} is still
     * {@link PaymentStatus#PENDING}</b> (see the class Javadoc for the full reasoning, including why
     * that guard exists: this same event also fires for our own explicit
     * {@code cancelUnconfirmed} call, which already resolves the row itself).
     */
    void applyPaymentIntentEvent(String stripeEventId, String eventType, String paymentIntentId,
            PaymentFailureCategory failureCategory, String gatewayFailureMessage) {
        if (stripeWebhookEventRepository.existsByStripeEventId(stripeEventId)) {
            log.info("Ignoring already-processed Stripe webhook event id={} (at-least-once redelivery)", stripeEventId);
            return;
        }

        Payment payment = paymentRepository.findByGatewayReference(paymentIntentId).orElse(null);
        if (payment == null) {
            // Defensive: a webhook for a PaymentIntent this reactor has no matching row for (e.g. a
            // charge attempted through some other integration). Still record the dedup row so a
            // redelivery of the same event id doesn't repeat this warning forever.
            log.warn("No Payment row found for Stripe PaymentIntent id={} (webhook event id={}, type={})",
                    paymentIntentId, stripeEventId, eventType);
            recordProcessed(stripeEventId, eventType);
            return;
        }

        if (PAYMENT_INTENT_CANCELED.equals(eventType) && payment.getStatus() != PaymentStatus.PENDING) {
            // Already resolved through some other path — most likely our own explicit
            // payment.PaymentGatewayPort#cancelUnconfirmed call, which fires this identical event
            // as a side effect of its own synchronous cancel and already marked this row
            // CANCELLED (see the class Javadoc). Must not let a redelivery of that same event
            // overwrite whatever already resolved it.
            log.info("Ignoring payment_intent.canceled for paymentId={} — already resolved as {}",
                    payment.getId(), payment.getStatus());
            recordProcessed(stripeEventId, eventType);
            return;
        }

        PaymentResult result = switch (eventType) {
            case PAYMENT_INTENT_SUCCEEDED -> PaymentResult.succeeded(paymentIntentId);
            case PAYMENT_INTENT_CANCELED -> PaymentResult.declined(paymentIntentId, failureCategory, gatewayFailureMessage);
            default -> PaymentResult.attemptFailed(paymentIntentId, failureCategory, gatewayFailureMessage);
        };
        paymentHandoffService.resolvePayment(payment.getOrder().getId(), result);
        recordProcessed(stripeEventId, eventType);
    }

    private void recordProcessed(String stripeEventId, String eventType) {
        StripeWebhookEvent processed = new StripeWebhookEvent();
        processed.setStripeEventId(stripeEventId);
        processed.setEventType(eventType);
        stripeWebhookEventRepository.save(processed);
    }

    private PaymentIntent extractPaymentIntent(Event event) {
        return (PaymentIntent) event.getDataObjectDeserializer().getObject()
                .orElseThrow(() -> new IllegalStateException(
                        "Could not deserialize Stripe event id=" + event.getId() + " type=" + event.getType()));
    }
}
