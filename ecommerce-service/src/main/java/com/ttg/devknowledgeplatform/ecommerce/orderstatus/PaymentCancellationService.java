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
 * The cancellation- and refund-side counterpart to {@link PaymentHandoffService} — split out of
 * that class (see its own Javadoc) once it had grown into a God class spanning three unrelated
 * payment lifecycles (charge, cancellation, refund), per a code-quality audit of this module.
 * Every method here follows the identical durable-step/gateway-call/durable-step shape
 * {@link PaymentHandoffService} itself established: a real network call must never happen inside
 * an open DB transaction, since a later, unrelated failure in the same transaction would roll back
 * a cancellation/refund the gateway had already durably applied, leaving this reactor's own
 * records silently wrong about money that really moved. This class depends on
 * {@link PaymentHandoffService} (never the reverse) for exactly one thing — see
 * {@link #applyGatewayCancellation}'s {@code ALREADY_RESOLVED} branch below.
 *
 * <p><b>Epic 4 Phase 6 (US-4.6):</b> {@link #applyCancellation} durably transitions the order to
 * {@code CANCELLED} and reports whether a refund is now owed; {@code service.impl.OrderServiceImpl
 * #cancel} then — only if one is — calls {@code payment.PaymentGatewayPort#refund} outside any
 * transaction, then {@link #applyRefundResult} in a second, independent transaction. Scoped to
 * US-4.6's own literal acceptance criterion — a shopper explicitly cancelling an already-
 * {@code CONFIRMED} order — not the rarer race in {@code PaymentProcessingOrderStatusHandler
 * #confirmPayment} (a queued cancel winning over a gateway success that arrives moments later):
 * that path still only restocks synchronously, same as before this phase — see that handler's own
 * Javadoc. Unlike {@link PaymentHandoffService#startPaymentProcessing}, no new intermediate
 * status/durable "refund in flight" marker was added: {@code StripePaymentGateway#refund}'s own
 * idempotency key is deterministic (derived from {@code gatewayReference}, not a fresh key per
 * call — see that class's own Javadoc), so retrying the whole operation after a crash can never
 * double-refund at the gateway, unlike a charge attempt retried with a fresh key.
 *
 * <p><b>Code-quality-audit follow-up: {@link RefundReconciliationJob} closes the money gap left
 * both by that rarer race and by the refund call itself failing.</b> Either way, the end state is
 * identical — a {@code Payment} row left {@code SUCCEEDED} on an order that already reached
 * {@code CANCELLED} — and previously nothing ever automatically refunded it (this phase's own
 * synchronous path only ever runs once, from {@code OrderServiceImpl#cancel}'s own single call).
 * {@link RefundReconciliationJob} now polls for exactly that combination and applies the missed
 * refund asynchronously — not instant, but no longer a permanent, manually-recovered gap. See that
 * class's own Javadoc for the full detail.
 *
 * <p><b>Option A follow-up: {@link #applyGatewayCancellation} closes the gap where an explicit
 * shopper cancel during an unconfirmed Stripe PaymentIntent left the order stuck {@code
 * PAYMENT_PROCESSING} forever.</b> Before this method existed, cancelling while payment was
 * {@code PAYMENT_PROCESSING} only ever queued {@code Order.cancelRequested} via {@link
 * #applyCancellation} — correct for the narrow race that shape was originally built for (a gateway
 * call already in flight, resolving within moments), but Option A's client-side confirmation can
 * leave a charge attempt unconfirmed indefinitely while the shopper is simply looking at the card
 * form. Nothing was ever going to pick that queued flag up on its own: no webhook fires for a
 * PaymentIntent the shopper never confirmed, and {@link PaymentHandoffService#resolvePayment}'s
 * own {@code PENDING} branch never even inspects {@code cancelRequested}. {@link #applyCancellation}
 * now detects exactly this state (order still {@code PAYMENT_PROCESSING} after the cancel only
 * queued, with a still-{@code PENDING} {@code Payment} row carrying a real
 * {@code gatewayReference}) and reports {@code gatewayCancellationNeeded() == true} instead, so
 * {@code service.impl.OrderServiceImpl#cancel} can actively void the charge attempt at the gateway
 * ({@code payment.PaymentGatewayPort#cancelUnconfirmed}, outside any transaction, same reason as
 * every other gateway call in this class) and then apply the result via
 * {@link #applyGatewayCancellation} — the same durable-step/gateway-call/durable-step shape this
 * class already uses everywhere else. Found via a real end-to-end `stripe listen` session where an
 * order stayed durably {@code PAYMENT_PROCESSING} even after Cancel Order was clicked.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentCancellationService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OrderStatusHandlerRegistry orderStatusHandlerRegistry;
    private final PaymentHandoffService paymentHandoffService;

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
     * failPayment} release-and-respect-{@code cancelRequested} logic {@link PaymentHandoffService
     * #resolvePayment}'s own {@code DECLINED} branch already uses (releasing a reservation and
     * honoring a queued cancel is identical work either way —
     * {@code PaymentProcessingOrderStatusHandler#failPayment}'s own {@code cancelRequested} check
     * ends the order {@code CANCELLED}), but marks the {@code Payment} row {@code CANCELLED}, never
     * {@code DECLINED} — nothing was actually declined, the shopper simply chose not to pay. No
     * outbox event is published for this branch either — a shopper-initiated cancel isn't the
     * {@code PAYMENT_FAILED} business event {@code PaymentFailedOutboxEventHandler} exists for.
     *
     * <p>{@link CancellationOutcome#ALREADY_RESOLVED} means the gateway reports the charge reached a
     * real terminal state (typically {@code SUCCEEDED} — the shopper confirmed on another tab a
     * moment before this call arrived) before the cancellation could apply; delegates straight to
     * {@link PaymentHandoffService#resolvePayment} (a different bean, so this goes through Spring's
     * proxy correctly and joins this method's own already-open transaction — see that class's own
     * Javadoc) with the gateway's own real result, instead of forcing a cancellation that never
     * actually happened — that method's own {@code cancelRequested}-aware {@code confirmPayment}/
     * {@code failPayment} handling already does the right thing with a genuine result arriving
     * after a queued cancel.
     *
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if the order
     *         no longer exists
     * @throws IllegalStateException if no {@code Payment} row exists for this order — a genuine
     *         invariant violation, same as {@link PaymentHandoffService#resolvePayment}'s own
     */
    @Transactional(rollbackFor = Throwable.class)
    public Order applyGatewayCancellation(Integer orderId, PaymentCancellationResult result) {
        if (result.outcome() == CancellationOutcome.ALREADY_RESOLVED) {
            return paymentHandoffService.resolvePayment(orderId, result.resolvedResult());
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
     * {@link PaymentHandoffService#resolvePayment}'s own shape for the refund vocabulary.
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
}
