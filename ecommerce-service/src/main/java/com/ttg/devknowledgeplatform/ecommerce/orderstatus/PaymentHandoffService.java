package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.common.exception.Validator;
import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentOutcome;
import com.ttg.devknowledgeplatform.ecommerce.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
 */
@Component
@RequiredArgsConstructor
public class PaymentHandoffService {

    private final OrderRepository orderRepository;
    private final OrderStatusHandlerRegistry orderStatusHandlerRegistry;

    /**
     * Transitions {@code orderId} to {@code PAYMENT_PROCESSING} on the caller's behalf, stamping
     * its idempotency key — the durable step that must commit before any gateway call.
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
        return orderRepository.save(order);
    }

    /**
     * Applies the gateway's (or the reconciliation job's) verdict for {@code orderId} — the second
     * durable step, called after the external gateway call has already happened (or, for
     * reconciliation, after re-querying it). A {@link PaymentOutcome#PENDING} verdict leaves the
     * order untouched — still {@code PAYMENT_PROCESSING}, to be checked again later.
     *
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if the order
     *         no longer exists
     */
    @Transactional(rollbackFor = Throwable.class)
    public Order resolvePayment(Integer orderId, PaymentOutcome outcome) {
        Order order = Validator.notFound(orderRepository.findById(orderId), EcommerceErrorCode.ORDER_NOT_FOUND, orderId);
        // Java 21 arrow-style switch over the enum — no fall-through, and PENDING's empty arm is
        // deliberate: nothing to do yet, a later reconciliation poll will re-check.
        switch (outcome) {
            case SUCCEEDED -> orderStatusHandlerRegistry.confirmPayment(order);
            case DECLINED -> orderStatusHandlerRegistry.failPayment(order);
            case PENDING -> { /* still not resolved — leave PAYMENT_PROCESSING for the next poll */ }
        }
        return orderRepository.save(order);
    }
}
