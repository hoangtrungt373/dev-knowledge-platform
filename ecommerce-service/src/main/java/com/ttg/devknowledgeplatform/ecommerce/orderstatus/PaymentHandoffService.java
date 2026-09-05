package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.common.exception.Validator;
import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.entity.OutboxEvent;
import com.ttg.devknowledgeplatform.ecommerce.entity.Payment;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.enums.OutboxAggregateType;
import com.ttg.devknowledgeplatform.ecommerce.enums.PaymentStatus;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.payment.CancellationOutcome;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentCancellationResult;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentOutcome;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentResult;
import com.ttg.devknowledgeplatform.ecommerce.payment.RefundResult;
import com.ttg.devknowledgeplatform.ecommerce.repository.OrderRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.OutboxEventRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * The two durable local-transaction steps around US-3.3's payment handoff — kept as their own
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
 * order itself to {@code CANCELLED} with a restock; Epic 4 Phase 6 is what will turn that
 * {@code SUCCEEDED} row into a real refund.
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
 * <p><b>Epic 4 Phase 6 (US-4.6):</b> {@link #applyCancellation} is this same
 * durable-step/gateway-call/durable-step shape applied to refunds — the same reason
 * {@link #resolvePayment} isn't allowed to call {@code payment.PaymentGatewayPort} directly
 * applies here too: a real network call must never happen inside an open DB transaction, since a
 * later, unrelated failure in the same transaction would roll back a cancellation the gateway had
 * already durably refunded, leaving this reactor's own records silently wrong about money that
 * really moved. {@code service.impl.OrderServiceImpl#cancel} calls {@link #applyCancellation}
 * (durably transitions the order to {@code CANCELLED} and reports whether a refund is owed),
 * then — only if one is — calls {@code payment.PaymentGatewayPort#refund} outside any
 * transaction, then {@link #applyRefundResult} in a second, independent transaction. Scoped to
 * US-4.6's own literal acceptance criterion — a shopper explicitly cancelling an already-
 * {@code CONFIRMED} order — not the rarer race in {@code PaymentProcessingOrderStatusHandler
 * #confirmPayment} (a queued cancel winning over a gateway success that arrives moments later):
 * that path still only restocks, same as before this phase — see that handler's own Javadoc.
 * Unlike {@link #startPaymentProcessing}, no new intermediate status/durable "refund in flight"
 * marker was added: {@code StripePaymentGateway#refund}'s own idempotency key is deterministic
 * (derived from {@code gatewayReference}, not a fresh key per call — see that class's own
 * Javadoc), so retrying the whole operation after a crash can never double-refund at the gateway,
 * unlike a charge attempt retried with a fresh key. If the refund call itself fails, the
 * {@code Payment} row is simply left {@code SUCCEEDED} (the order is already durably
 * {@code CANCELLED} from step one) — a known, undone-money gap this phase doesn't build automatic
 * recovery for, since nothing in US-4.6 asked for one; revisit if that's ever a real problem.
 *
 * <p><b>Option A follow-up: {@link #applyGatewayCancellation} closes the gap where an explicit
 * shopper cancel during an unconfirmed Stripe PaymentIntent left the order stuck {@code
 * PAYMENT_PROCESSING} forever.</b> Before this method existed, cancelling while payment was
 * {@code PAYMENT_PROCESSING} only ever queued {@code Order.cancelRequested} via {@link
 * #applyCancellation} — correct for the narrow race that shape was originally built for (a gateway
 * call already in flight, resolving within moments), but Option A's client-side confirmation can
 * leave a charge attempt unconfirmed indefinitely while the shopper is simply looking at the card
 * form. Nothing was ever going to pick that queued flag up on its own: no webhook fires for a
 * PaymentIntent the shopper never confirmed, and {@link #resolvePayment}'s own {@code PENDING}
 * branch never even inspects {@code cancelRequested}. {@link #applyCancellation} now detects exactly
 * this state (order still {@code PAYMENT_PROCESSING} after the cancel only queued, with a
 * still-{@code PENDING} {@code Payment} row carrying a real {@code gatewayReference}) and reports
 * {@code gatewayCancellationNeeded() == true} instead, so {@code service.impl.OrderServiceImpl
 * #cancel} can actively void the charge attempt at the gateway
 * ({@code payment.PaymentGatewayPort#cancelUnconfirmed}, outside any transaction, same reason as
 * every other gateway call in this class) and then apply the result via
 * {@link #applyGatewayCancellation} — the same durable-step/gateway-call/durable-step shape this
 * class already uses everywhere else. Found via a real end-to-end `stripe listen` session where an
 * order stayed durably {@code PAYMENT_PROCESSING} even after Cancel Order was clicked.
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

    /**
     * The outcome of {@link #applyCancellation} — whether a refund is now owed, whether the caller
     * must actively cancel an in-flight gateway charge attempt, and if either, everything the
     * relevant {@code payment.PaymentGatewayPort} call needs. {@link #refundNeeded()} and
     * {@link #gatewayCancellationNeeded()} are never both {@code true} — a refund only applies to an
     * already-{@code SUCCEEDED} payment on an order that just became {@code CANCELLED} outright,
     * while a gateway cancellation only applies to a still-{@code PENDING} payment on an order whose
     * cancel only queued (still {@code PAYMENT_PROCESSING}) — mutually exclusive states of the same
     * order.
     */
    public record CancellationResult(
            Order order, boolean refundNeeded, boolean gatewayCancellationNeeded,
            Integer paymentId, String gatewayReference, BigDecimal amount) {
    }

    /**
     * Transitions {@code orderId} to {@code CANCELLED} (or queues the cancel, per
     * {@code OrderStatusHandler}'s own per-status rules) on the caller's behalf — the durable step
     * that must commit before any refund/gateway-cancel call (US-4.6, plus the Option A follow-up —
     * see this class's own Javadoc).
     *
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if the order
     *         doesn't exist or doesn't belong to {@code callerUuid}
     * @throws com.ttg.devknowledgeplatform.common.exception.BusinessException if the order's
     *         current status has no valid cancel transition at all
     */
    @Transactional(rollbackFor = Throwable.class)
    public CancellationResult applyCancellation(Integer orderId, String callerUuid) {
        Order order = Validator.notFound(
                orderRepository.findById(orderId).filter(o -> o.getOwnerUuid().equals(callerUuid)),
                EcommerceErrorCode.ORDER_NOT_FOUND, orderId);
        orderStatusHandlerRegistry.cancel(order);
        Order saved = orderRepository.save(order);

        if (saved.getStatus() != OrderStatus.CANCELLED) {
            // A PAYMENT_PROCESSING order only queues the cancel (Order.cancelRequested) — nothing to
            // refund yet. But if the in-flight attempt is a still-unconfirmed Stripe PaymentIntent
            // (Option A), nothing will ever resolve it on its own — the caller must actively cancel
            // it at the gateway so the queued cancel can actually take effect (see this class's own
            // Javadoc for the incident this addresses).
            Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
            if (payment != null && payment.getStatus() == PaymentStatus.PENDING && payment.getGatewayReference() != null) {
                return new CancellationResult(saved, false, true, payment.getId(), payment.getGatewayReference(), null);
            }
            return new CancellationResult(saved, false, false, null, null, null);
        }
        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.SUCCEEDED) {
            // A PENDING order that never reached a charge attempt, or one whose payment never
            // actually succeeded — nothing was captured, so there's nothing to give back.
            return new CancellationResult(saved, false, false, null, null, null);
        }
        return new CancellationResult(
                saved, true, false, payment.getId(), payment.getGatewayReference(), payment.getAmount());
    }

    /**
     * Applies a gateway-cancellation verdict for {@code orderId} (the Option A follow-up — see this
     * class's own Javadoc) — the second durable step, called after
     * {@code payment.PaymentGatewayPort#cancelUnconfirmed} has already happened.
     *
     * <p>{@link CancellationOutcome#CANCELLED} dispatches through the exact same {@code
     * failPayment} release-and-respect-{@code cancelRequested} logic {@link #resolvePayment}'s own
     * {@code DECLINED} branch already uses (releasing a reservation and honoring a queued cancel is
     * identical work either way — {@code PaymentProcessingOrderStatusHandler#failPayment}'s own
     * {@code cancelRequested} check ends the order {@code CANCELLED}), but marks the {@code Payment}
     * row {@code CANCELLED}, never {@code DECLINED} — nothing was actually declined, the shopper
     * simply chose not to pay. No outbox event is published for this branch either — a
     * shopper-initiated cancel isn't the {@code PAYMENT_FAILED} business event {@code
     * PaymentFailedOutboxEventHandler} exists for.
     *
     * <p>{@link CancellationOutcome#ALREADY_RESOLVED} means the gateway reports the charge reached a
     * real terminal state (typically {@code SUCCEEDED} — the shopper confirmed on another tab a
     * moment before this call arrived) before the cancellation could apply; delegates straight to
     * {@link #resolvePayment} with the gateway's own real result instead of forcing a cancellation
     * that never actually happened — that method's own {@code cancelRequested}-aware {@code
     * confirmPayment}/{@code failPayment} handling already does the right thing with a genuine
     * result arriving after a queued cancel.
     *
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if the order
     *         no longer exists
     * @throws IllegalStateException if no {@code Payment} row exists for this order — a genuine
     *         invariant violation, same as {@link #resolvePayment}'s own
     */
    @Transactional(rollbackFor = Throwable.class)
    public Order applyGatewayCancellation(Integer orderId, PaymentCancellationResult result) {
        if (result.outcome() == CancellationOutcome.ALREADY_RESOLVED) {
            return resolvePayment(orderId, result.resolvedResult());
        }
        Order order = Validator.notFound(orderRepository.findById(orderId), EcommerceErrorCode.ORDER_NOT_FOUND, orderId);
        orderStatusHandlerRegistry.failPayment(order);
        Order saved = orderRepository.save(order);
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException(
                        "No Payment row found for order id=" + orderId + " — startPaymentProcessing "
                                + "should always have written one before any gateway call"));
        payment.setStatus(PaymentStatus.CANCELLED);
        paymentRepository.save(payment);
        return saved;
    }

    /**
     * Applies a refund's verdict (US-4.6) — the second durable step, called after
     * {@code payment.PaymentGatewayPort#refund} has already happened. Mirrors
     * {@link #resolvePayment}'s own shape for the refund vocabulary.
     *
     * @throws IllegalStateException if no {@code Payment} row exists for {@code paymentId} — a
     *         genuine invariant violation, since {@link #applyCancellation} only ever reports
     *         {@code refundNeeded() == true} alongside a real {@code paymentId}
     */
    @Transactional(rollbackFor = Throwable.class)
    public void applyRefundResult(Integer paymentId, RefundResult result) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalStateException("No Payment row found for id=" + paymentId));
        switch (result.outcome()) {
            case SUCCEEDED -> {
                payment.setStatus(PaymentStatus.REFUNDED);
                paymentRepository.save(payment);
                publishPaymentRefunded(payment);
            }
            case FAILED -> log.warn("Refund failed for paymentId={} gatewayReference={}: {} — Payment row left "
                            + "SUCCEEDED (no automatic retry built; US-4.6 didn't ask for one)",
                    paymentId, payment.getGatewayReference(), result.failureMessage());
            case PENDING -> log.info("Refund for paymentId={} gatewayReference={} is still pending at the gateway",
                    paymentId, payment.getGatewayReference());
        }
    }

    private void publishPaymentRefunded(Payment payment) {
        OutboxEvent event = new OutboxEvent();
        event.setEventType(PaymentRefundedOutboxEventHandler.EVENT_TYPE);
        event.setAggregateType(OutboxAggregateType.PAYMENT);
        event.setAggregateId(payment.getId());
        event.setPayload(new PaymentRefundedOutboxEventHandler.Payload(
                payment.getOrder().getId(), payment.getAmount(), payment.getGatewayReference()).toMap());
        outboxEventRepository.save(event);
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
                if (result.failureCategory() != null) {
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
