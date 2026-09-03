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
     *         currently {@code PENDING}
     */
    @Transactional(rollbackFor = Throwable.class)
    public Order startPaymentProcessing(Integer orderId, String callerUuid) {
        Order order = Validator.notFound(
                orderRepository.findById(orderId).filter(o -> o.getOwnerUuid().equals(callerUuid)),
                EcommerceErrorCode.ORDER_NOT_FOUND, orderId);
        orderStatusHandlerRegistry.startPaymentProcessing(order);
        Order saved = orderRepository.save(order);
        paymentRepository.save(newPendingPayment(saved));
        return saved;
    }

    /**
     * Applies the gateway's (or the reconciliation job's) verdict for {@code orderId} — the second
     * durable step, called after the external gateway call has already happened (or, for
     * reconciliation, after re-querying it). A {@link PaymentOutcome#PENDING} verdict leaves both
     * the order and its {@code Payment} row untouched — still {@code PAYMENT_PROCESSING}/
     * {@code PENDING} respectively, to be checked again later.
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
     * The outcome of {@link #applyCancellation} — whether a refund is now owed, and if so,
     * everything {@code payment.PaymentGatewayPort#refund} needs to issue it. {@link #refundNeeded()}
     * is {@code false} whenever the order didn't just become {@code CANCELLED} as a direct result
     * of this call (e.g. it was still {@code PAYMENT_PROCESSING}, so the cancel only queued) or
     * when it did but no {@code Payment} row was ever {@code SUCCEEDED} for it (a {@code PENDING}
     * order has no charge attempt at all).
     */
    public record CancellationResult(
            Order order, boolean refundNeeded, Integer paymentId, String gatewayReference, BigDecimal amount) {
    }

    /**
     * Transitions {@code orderId} to {@code CANCELLED} (or queues the cancel, per
     * {@code OrderStatusHandler}'s own per-status rules) on the caller's behalf — the durable step
     * that must commit before any refund gateway call (US-4.6).
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
            // e.g. a PAYMENT_PROCESSING order only queues the cancel (Order.cancelRequested) —
            // nothing to refund yet, and nothing ever will be from this call.
            return new CancellationResult(saved, false, null, null, null);
        }
        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.SUCCEEDED) {
            // A PENDING order that never reached a charge attempt, or one whose payment never
            // actually succeeded — nothing was captured, so there's nothing to give back.
            return new CancellationResult(saved, false, null, null, null);
        }
        return new CancellationResult(saved, true, payment.getId(), payment.getGatewayReference(), payment.getAmount());
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
        if (result.outcome() == PaymentOutcome.PENDING) {
            return;
        }
        Payment payment = paymentRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "No Payment row found for order id=" + order.getId() + " — startPaymentProcessing "
                                + "should always have written one before any gateway call"));
        payment.setGatewayReference(result.gatewayReference());
        if (result.outcome() == PaymentOutcome.SUCCEEDED) {
            payment.setStatus(PaymentStatus.SUCCEEDED);
            paymentRepository.save(payment);
            publishPaymentSucceeded(order, payment);
        } else {
            payment.setStatus(PaymentStatus.DECLINED);
            payment.setFailureCategory(result.failureCategory());
            payment.setGatewayFailureMessage(result.gatewayFailureMessage());
            paymentRepository.save(payment);
            publishPaymentFailed(order, payment);
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
