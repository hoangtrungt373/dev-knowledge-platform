package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.common.exception.Validator;
import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.entity.OutboxEvent;
import com.ttg.devknowledgeplatform.ecommerce.entity.Payment;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.enums.OutboxAggregateType;
import com.ttg.devknowledgeplatform.ecommerce.enums.PaymentStatus;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentOutcome;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentResult;
import com.ttg.devknowledgeplatform.ecommerce.repository.OrderRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.OutboxEventRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two durable local-transaction steps around US-3.3's payment <b>charge</b> handoff —
 * {@link #startPaymentProcessing} and {@link #resolvePayment} — kept as their own
 * {@code @Transactional} methods on their own bean (not inline in whatever orchestrates a payment
 * attempt) for the same reason as every other split in this reactor between a caller and its
 * {@code @Transactional} target (see {@code outbox.OutboxEventProcessor}'s own Javadoc): a bean
 * calling its own {@code @Transactional} method via {@code this.foo()} bypasses Spring's proxy.
 *
 * <p>The real reason these are two <i>separate</i> transactions rather than one, though, is US-3.3
 * itself: {@link #startPaymentProcessing} must durably commit the {@code PENDING} ->
 * {@code PAYMENT_PROCESSING} transition and the idempotency key <b>before</b> the payment gateway
 * is ever called — whatever orchestrates the actual attempt (e.g. {@code service.OrderService.initiatePayment})
 * calls this, then calls {@code payment.PaymentGatewayPort.charge} outside any transaction, then
 * calls {@link #resolvePayment} in a second, independent transaction. If the process crashes
 * between the gateway call and {@link #resolvePayment}, the order is left durably
 * {@code PAYMENT_PROCESSING} with its idempotency key intact — exactly the state
 * {@code OrderReconciliationJob} (US-3.4) exists to recover from, rather than the whole attempt
 * silently rolling back and risking a double charge on retry.
 *
 * <p><b>Epic 4 Phase 3 (US-4.2/4.3):</b> {@link #startPaymentProcessing} now also writes the
 * {@code entity.Payment} row for this attempt, {@code PENDING}, in the exact same transaction as
 * the order's own transition — this is what {@code Payment}'s own Javadoc has promised since
 * Phase 1, now actually true. {@link #resolvePayment} takes the full {@link PaymentResult} (not
 * just the bare {@link PaymentOutcome} Epic 3 originally passed) so it can update that same row's
 * {@code status}/{@code gatewayReference}/{@code failureCategory}/{@code gatewayFailureMessage} in
 * the same transaction as the order's {@code CONFIRMED}/{@code FAILED} transition (US-4.3) — never
 * one without the other. The {@code Payment} row's own {@code status} always reflects what the
 * gateway actually decided, independent of whatever the order status ends up being — a queued
 * cancel racing a gateway success still records the payment as {@code SUCCEEDED} (the money really
 * was captured), even though {@code PaymentProcessingOrderStatusHandler.confirmPayment} sends the
 * order itself to {@code CANCELLED} with a restock; {@link PaymentCancellationService} is what
 * turns that {@code SUCCEEDED} row into a real refund.
 *
 * <p><b>Epic 4 Phase 4 (US-4.4):</b> {@link #resolvePayment} also publishes a
 * {@code PAYMENT_SUCCEEDED}/{@code PAYMENT_FAILED} {@code OutboxEvent} in that same transaction,
 * right alongside the {@code Payment} row update — the dual-write problem this pattern exists to
 * solve (a DB commit and an external publish can't be made atomic any other way). See
 * {@link PaymentSucceededOutboxEventHandler}/{@link PaymentFailedOutboxEventHandler}'s own Javadoc
 * for why each is a deliberate, documented placeholder consumer rather than an unconsumed event —
 * this reactor chose to publish anyway (unlike the earlier, opposite call made for
 * {@code ORDER_CREATED}, see this module's own `CLAUDE.md`), since US-4.4/US-4.6 name these events
 * as real acceptance criteria, not a speculative nice-to-have.
 *
 * <p><b>Code-quality follow-up: cancellation and refund handling (Epic 4 Phase 6/US-4.6, plus the
 * Option A unconfirmed-PaymentIntent follow-up) moved out to the sibling
 * {@link PaymentCancellationService}.</b> This class used to also own {@code applyCancellation}/
 * {@code applyGatewayCancellation}/{@code applyRefundResult} — a God class spanning three
 * unrelated payment lifecycles (charge, cancellation, refund) that an ecommerce-service-wide code
 * audit flagged as worth splitting; see {@link PaymentCancellationService}'s own Javadoc for the
 * full detail on those three methods. {@link PaymentCancellationService} depends on this class
 * (never the other way around) for exactly one thing: its {@code applyGatewayCancellation}'s
 * {@code ALREADY_RESOLVED} branch — the gateway reports the charge actually reached a real
 * terminal state moments before the cancellation could apply — delegates straight to this class's
 * own {@link #resolvePayment}, since that method's own {@code cancelRequested}-aware handling
 * already does the right thing with a genuine result arriving after a queued cancel. The two
 * classes don't otherwise share any code — each of the three {@code OutboxEvent}-publish helpers
 * this class used to hold was used by exactly one of the two post-split lifecycles, so each simply
 * moved with its own call site rather than needing a shared component.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentHandoffService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OrderStatusHandlerRegistry orderStatusHandlerRegistry;

    /**
     * Transitions {@code orderId} to {@code PAYMENT_PROCESSING} on the caller's behalf, stamping
     * its idempotency key, and writes the {@code PENDING} {@code Payment} row for this attempt —
     * both in the one durable step that must commit before any gateway call.
     *
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if the order
     *         doesn't exist or doesn't belong to {@code callerUuid}
     * @throws com.ttg.devknowledgeplatform.common.exception.BusinessException if the order isn't
     *         currently {@code PENDING} (or already {@code PAYMENT_PROCESSING} — see below)
     */
    @Transactional(rollbackFor = Throwable.class)
    public Order startPaymentProcessing(Integer orderId, String callerUuid) {
        Order order = Validator.notFound(
                orderRepository.findById(orderId).filter(o -> o.getOwnerUuid().equals(callerUuid)),
                EcommerceErrorCode.ORDER_NOT_FOUND, orderId);
        if (order.getStatus() == OrderStatus.PAYMENT_PROCESSING) {
            // Re-entrant on purpose (Option A, Stripe Elements): the shopper can call pay() again
            // while a PaymentIntent is still awaiting client-side confirmation (e.g. they closed the
            // payment dialog before finishing, or just reloaded the page) — there's already a
            // PENDING Payment row and an idempotency key from the first call, and
            // payment.PaymentGatewayPort#charge is itself idempotent on that same key (see
            // StripePaymentGateway's own Javadoc), so the caller just needs the same order handed
            // back to retry the gateway call, not a second Payment row or a rejected transition.
            return order;
        }
        orderStatusHandlerRegistry.startPaymentProcessing(order);
        Order saved = orderRepository.save(order);
        paymentRepository.save(newPendingPayment(saved));
        return saved;
    }

    /**
     * Applies the gateway's (or the reconciliation job's) verdict for {@code orderId} — the second
     * durable step, called after the external gateway call has already happened (or, for
     * reconciliation, after re-querying it). A {@link PaymentOutcome#PENDING} verdict leaves the
     * order's own status untouched — still {@code PAYMENT_PROCESSING}, to be checked again later —
     * but {@link #applyResultToPayment} still persists {@code result.gatewayReference()} onto the
     * {@code Payment} row when one is present. That matters as of the Option A (Stripe Elements)
     * follow-up: {@code payment.StripePaymentGateway#charge}'s very first call now routinely
     * resolves {@code PENDING} (an unconfirmed {@code PaymentIntent}, status
     * {@code "requires_payment_method"}) rather than the rarer edge case it used to be — an earlier
     * revision of this method skipped the {@code Payment} row entirely on {@code PENDING},
     * which meant {@code gatewayReference} was never recorded for the *common* case at all. Both
     * {@code webhook.StripeWebhookService} (correlates an incoming event by
     * {@code Payment.gatewayReference}) and this class's own {@link #startPaymentProcessing}'s
     * re-entrant retry path (via {@code payment.StripePaymentGateway#checkStatus}, which looks the
     * same column up by idempotency key) depend on it being set from the very first {@code charge}
     * call — without it, a real {@code payment_intent.succeeded} webhook event finds no matching
     * row and silently no-ops (still returns Stripe a {@code 200}, so nothing about the delivery
     * looks like a failure), and the reconciliation job's own fallback poll is broken the identical
     * way. Caught via a real end-to-end `stripe listen` session where the order stayed durably
     * stuck {@code PAYMENT_PROCESSING} despite Stripe's own event log showing
     * {@code payment_intent.succeeded} having fired and been delivered with a {@code 200}.
     *
     * <p><b>Bug fix: {@link PaymentOutcome#PENDING} can now also carry a decline reason, via
     * {@link PaymentResult#attemptFailed} — one retryable attempt failing is not the same thing as
     * the charge being over.</b> {@code webhook.StripeWebhookService} used to build a bare
     * {@link PaymentResult#declined} for every {@code payment_intent.payment_failed} event, which
     * this method's {@code DECLINED} branch (below) finalizes to {@code FAILED} — permanently
     * blocking a shopper from ever completing the order after their very first mistyped card,
     * since Option A's {@code PaymentElement} actually keeps the same {@code PaymentIntent} open
     * for another attempt with a different card (Stripe reports it back at
     * {@code "requires_payment_method"}, the exact same status a fresh, never-attempted intent
     * starts in). The webhook now reports that event as {@code PENDING} instead, carrying the
     * failure detail for display; this branch's own empty {@code PENDING} arm is what makes that
     * safe — the order genuinely stays put, exactly as it should, while
     * {@link #applyResultToPayment} still records the reason onto the {@code Payment} row (and
     * clears it again once a later attempt actually succeeds). {@link PaymentOutcome#DECLINED}
     * itself is unchanged and still finalizes to {@code FAILED} here — that's still correct for
     * {@code MockPaymentGateway}'s synchronous one-shot decline and for a reconciliation poll that
     * finds the intent genuinely {@code "canceled"} at Stripe; only the webhook's own
     * classification of a retryable decline was wrong.
     *
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if the order
     *         no longer exists
     * @throws IllegalStateException if no {@code Payment} row exists for this order — a genuine
     *         invariant violation, since {@link #startPaymentProcessing} always writes one first;
     *         rolls back the order transition alongside it rather than leaving a corrupted trail
     */
    @Transactional(rollbackFor = Throwable.class)
    public Order resolvePayment(Integer orderId, PaymentResult result) {
        Order order = Validator.notFound(orderRepository.findById(orderId), EcommerceErrorCode.ORDER_NOT_FOUND, orderId);
        // Java 21 arrow-style switch over the enum — no fall-through, and PENDING's empty arm is
        // deliberate: nothing to do yet, a later reconciliation poll will re-check.
        switch (result.outcome()) {
            case SUCCEEDED -> orderStatusHandlerRegistry.confirmPayment(order);
            case DECLINED -> orderStatusHandlerRegistry.failPayment(order);
            case PENDING -> { /* still not resolved — leave PAYMENT_PROCESSING for the next poll */ }
        }
        Order saved = orderRepository.save(order);
        applyResultToPayment(saved, result);
        return saved;
    }

    private Payment newPendingPayment(Order order) {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(order.getTotal());
        payment.setIdempotencyKey(order.getIdempotencyKey());
        payment.setStatus(PaymentStatus.PENDING);
        return payment;
    }

    private void applyResultToPayment(Order order, PaymentResult result) {
        Payment payment = paymentRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "No Payment row found for order id=" + order.getId() + " — startPaymentProcessing "
                                + "should always have written one before any gateway call"));
        // Always record a real gatewayReference as soon as one exists — even for PENDING, whose
        // very first occurrence (Option A's unconfirmed PaymentIntent) is exactly when this column
        // gets its only chance to be set before a later webhook/reconciliation call needs to look
        // this row up by it. See this method's own call site (resolvePayment) for the incident this
        // guards against. A reconciliation poll's own repeat PENDING carries the same reference
        // already stored — this is then just a harmless overwrite with the same value.
        if (result.gatewayReference() != null) {
            payment.setGatewayReference(result.gatewayReference());
        }
        switch (result.outcome()) {
            case PENDING -> {
                // A plain "still waiting" poll (e.g. reconciliation re-checking an untouched
                // intent) carries no failure detail at all — don't let it clobber a real, more
                // recent decline reason (from PaymentResult#attemptFailed, a retryable decline
                // under Option A — see that factory's own Javadoc) with null. Only ever overwrite
                // when this particular result actually carries new information.
                //
                // Edge-case fix: also only ever apply that detail while this row is still
                // genuinely PENDING. Stripe explicitly does not guarantee webhook delivery order —
                // payment_intent.payment_failed for an EARLIER attempt (this method's own PENDING
                // outcome) can be delivered AFTER payment_intent.succeeded for a LATER attempt
                // against the same still-open PaymentIntent already resolved this row SUCCEEDED.
                // Without this guard, that stale, out-of-order event would reintroduce the old
                // decline reason onto an already-paid order — cosmetic (this branch never touches
                // the order's own status), but a real, user-visible "your card was declined" banner
                // reappearing on a successfully-paid order.
                if (payment.getStatus() == PaymentStatus.PENDING && result.failureCategory() != null) {
                    payment.setFailureCategory(result.failureCategory());
                    payment.setGatewayFailureMessage(result.gatewayFailureMessage());
                }
                paymentRepository.save(payment);
            }
            case SUCCEEDED -> {
                payment.setStatus(PaymentStatus.SUCCEEDED);
                // Clear any decline reason left over from an earlier failed attempt against this
                // same still-open PaymentIntent (Option A's retry flow) — otherwise a now-paid
                // order would keep showing a stale "your card was declined" reason.
                payment.setFailureCategory(null);
                payment.setGatewayFailureMessage(null);
                paymentRepository.save(payment);
                publishPaymentSucceeded(order, payment);
            }
            case DECLINED -> {
                payment.setStatus(PaymentStatus.DECLINED);
                payment.setFailureCategory(result.failureCategory());
                payment.setGatewayFailureMessage(result.gatewayFailureMessage());
                paymentRepository.save(payment);
                publishPaymentFailed(order, payment);
            }
        }
    }

    private void publishPaymentSucceeded(Order order, Payment payment) {
        OutboxEvent event = new OutboxEvent();
        event.setEventType(PaymentSucceededOutboxEventHandler.EVENT_TYPE);
        event.setAggregateType(OutboxAggregateType.PAYMENT);
        event.setAggregateId(payment.getId());
        event.setPayload(new PaymentSucceededOutboxEventHandler.Payload(
                order.getId(), payment.getAmount(), payment.getGatewayReference()).toMap());
        outboxEventRepository.save(event);
    }

    private void publishPaymentFailed(Order order, Payment payment) {
        OutboxEvent event = new OutboxEvent();
        event.setEventType(PaymentFailedOutboxEventHandler.EVENT_TYPE);
        event.setAggregateType(OutboxAggregateType.PAYMENT);
        event.setAggregateId(payment.getId());
        String failureCategory = payment.getFailureCategory() == null ? null : payment.getFailureCategory().name();
        event.setPayload(new PaymentFailedOutboxEventHandler.Payload(
                order.getId(), payment.getAmount(), failureCategory, payment.getGatewayFailureMessage()).toMap());
        outboxEventRepository.save(event);
    }
}
