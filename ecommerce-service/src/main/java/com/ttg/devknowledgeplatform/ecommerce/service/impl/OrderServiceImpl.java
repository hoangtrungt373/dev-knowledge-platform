package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.common.exception.Validator;
import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.orderstatus.OrderStatusHandlerRegistry;
import com.ttg.devknowledgeplatform.ecommerce.orderstatus.PaymentCancellationService;
import com.ttg.devknowledgeplatform.ecommerce.orderstatus.PaymentHandoffService;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentCancellationResult;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentGatewayException;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentGatewayPort;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentResult;
import com.ttg.devknowledgeplatform.ecommerce.payment.RefundResult;
import com.ttg.devknowledgeplatform.ecommerce.repository.OrderRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.spec.OrderSpecification;
import com.ttg.devknowledgeplatform.ecommerce.service.OrderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * Implementation of {@link OrderService}.
 *
 * <p>{@link #cancel} hides ownership the same way {@code ProductService.getActiveBySlug} hides a
 * deactivated product's slug: an order that doesn't exist and an order that exists but belongs to
 * someone else both surface as {@code ORDER_NOT_FOUND}, never a distinguishable "forbidden" — a
 * caller has no legitimate reason to learn that an order id they don't own exists at all.
 *
 * <p>{@link #initiatePayment} is deliberately <b>not</b> itself {@code @Transactional} — see
 * {@link PaymentHandoffService}'s own Javadoc for why the durable-commit/gateway-call/
 * durable-commit sequence needs two independent transactions rather than one wrapping the whole
 * method (which would let a crash mid-gateway-call silently roll back the very
 * {@code PAYMENT_PROCESSING} marker US-3.4's reconciliation job depends on existing).
 * {@link #cancel} (Epic 4 Phase 6, US-4.6) is the same shape for the opposite direction: it's not
 * {@code @Transactional} either, since a refund is owed only when {@link PaymentCancellationService
 * #applyCancellation} reports one, and that refund gateway call must happen outside any local
 * transaction for the identical reason. An Option A follow-up added a third branch alongside
 * refund: when {@code applyCancellation} instead reports {@code gatewayCancellationNeeded()} (the
 * cancel only queued because payment is still an unconfirmed Stripe PaymentIntent — see {@link
 * PaymentCancellationService}'s own Javadoc), this method actively voids that charge attempt at the
 * gateway, again outside any transaction, before applying the result.
 *
 * <p><b>Bug fix: {@link #cancel} now tolerates losing a race to a concurrent resolution of the
 * same order, instead of surfacing that race as an error.</b> Because this method's own follow-up
 * steps run outside any single transaction (by design, above), a shopper double-clicking Cancel
 * Order, or the Stripe webhook/{@code OrderReconciliationJob} resolving the same order at almost
 * the same moment, can both be mid-flight on the same order at once. Whichever one commits last
 * would previously hit {@code EcommerceErrorCode#ORDER_INVALID_STATUS_TRANSITION} (the order is
 * already {@code CANCELLED} — a terminal status with no registered handler — by the time it tries
 * to apply its own result) or {@link ObjectOptimisticLockingFailureException} (the order's own
 * {@code @Version} was already bumped by the racer that won), and that exception propagated
 * straight to the shopper as an error — even though the order had, in truth, already reached
 * exactly the outcome they asked for. {@link #cancel} now catches both, re-fetches the order, and
 * if it's genuinely {@code CANCELLED} (the only outcome a queued cancel can ever resolve to, per
 * {@code PaymentProcessingOrderStatusHandler}'s own {@code cancelRequested}-wins rule — never
 * {@code CONFIRMED}), returns it as a normal success instead of an error. Any other rejection (e.g.
 * a stale page trying to cancel an order an admin already shipped) still propagates unchanged —
 * this only swallows the one specific "someone already finished the thing I was trying to do"
 * race, never a genuinely invalid request.
 *
 * <p><b>Bug fix: every direct gateway call in this class (charge/refund/cancel-unconfirmed) now
 * translates a {@link PaymentGatewayException} into {@link EcommerceErrorCode
 * #PAYMENT_GATEWAY_UNAVAILABLE} instead of letting it propagate as a raw, uncaught
 * {@code RuntimeException}.</b> Before this fix, a genuine Stripe outage during any of these calls
 * fell through to the generic {@code Exception} handler as an unhelpful {@code 500} with no error
 * code — this reactor's own convention is that services never leak an unmapped exception to a
 * caller. This translation happens strictly after whatever durable step already committed (e.g.
 * {@link PaymentHandoffService#startPaymentProcessing} for {@link #initiatePayment}), so it changes
 * only the shape of the response the caller receives — it does not touch the existing guarantee
 * that a mid-call crash still leaves the order safely {@code PAYMENT_PROCESSING} for {@code
 * OrderReconciliationJob} to resolve later (see {@link PaymentGatewayException}'s own Javadoc).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderStatusHandlerRegistry orderStatusHandlerRegistry;
    private final PaymentHandoffService paymentHandoffService;
    private final PaymentCancellationService paymentCancellationService;
    private final PaymentGatewayPort paymentGatewayPort;

    @Override
    @Transactional(readOnly = true)
    public Order getOrder(Integer orderId, String callerUuid) {
        return Validator.notFound(
                orderRepository.findById(orderId).filter(o -> o.getOwnerUuid().equals(callerUuid)),
                EcommerceErrorCode.ORDER_NOT_FOUND, orderId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Order> listOrders(String callerUuid, Collection<OrderStatus> statuses, Pageable pageable) {
        return orderRepository.findAll(OrderSpecification.withOwnerAndStatuses(callerUuid, statuses), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Order> listAllOrders(OrderStatus status, Pageable pageable) {
        return orderRepository.findAll(OrderSpecification.withFilters(status), pageable);
    }

    @Override
    public Order cancel(Integer orderId, String callerUuid) {
        try {
            return doCancel(orderId, callerUuid);
        } catch (BusinessException e) {
            return recoverFromConcurrentCancelResolution(
                    orderId, e, e.getErrorCode() == EcommerceErrorCode.ORDER_INVALID_STATUS_TRANSITION);
        } catch (ObjectOptimisticLockingFailureException e) {
            return recoverFromConcurrentCancelResolution(orderId, e, true);
        }
    }

    private Order doCancel(Integer orderId, String callerUuid) {
        PaymentCancellationService.CancellationResult cancellation =
                paymentCancellationService.applyCancellation(orderId, callerUuid);
        if (cancellation.refundNeeded()) {
            RefundResult result = callGatewayOrFail(
                    () -> paymentGatewayPort.refund(cancellation.gatewayReference(), cancellation.amount()));
            paymentCancellationService.applyRefundResult(cancellation.paymentId(), result);
            return cancellation.order();
        }
        if (cancellation.gatewayCancellationNeeded()) {
            PaymentCancellationResult result = callGatewayOrFail(
                    () -> paymentGatewayPort.cancelUnconfirmed(cancellation.gatewayReference()));
            return paymentCancellationService.applyGatewayCancellation(orderId, result);
        }
        return cancellation.order();
    }

    /**
     * Called when {@link #doCancel} threw something that might just mean "someone else already
     * finished cancelling this order" — see this class's own Javadoc. Only actually swallows it
     * when {@code mightBeRace} is true (the exception is the specific terminal-status/optimistic-
     * lock shape a race produces) <i>and</i> the order genuinely reached {@code CANCELLED} in the
     * meantime; any other case rethrows the original exception unchanged, since it's either a
     * different kind of failure entirely or a genuinely invalid request this method should still
     * reject.
     */
    private Order recoverFromConcurrentCancelResolution(Integer orderId, RuntimeException cause, boolean mightBeRace) {
        if (mightBeRace) {
            Order current = orderRepository.findById(orderId).orElse(null);
            if (current != null && current.getStatus() == OrderStatus.CANCELLED) {
                log.info("cancel(orderId={}) lost a race to a concurrent resolution that already reached "
                        + "CANCELLED — treating as success instead of propagating {}", orderId, cause.getClass().getSimpleName());
                return current;
            }
        }
        throw cause;
    }

    /**
     * Runs a direct {@link PaymentGatewayPort} call, translating a {@link PaymentGatewayException}
     * (a genuine gateway/network outage, never a card decline — see that exception's own Javadoc)
     * into a friendly {@link EcommerceErrorCode#PAYMENT_GATEWAY_UNAVAILABLE} instead of letting an
     * unmapped {@code RuntimeException} reach the caller.
     */
    private <T> T callGatewayOrFail(Supplier<T> gatewayCall) {
        try {
            return gatewayCall.get();
        } catch (PaymentGatewayException e) {
            throw new ApiException(EcommerceErrorCode.PAYMENT_GATEWAY_UNAVAILABLE, e);
        }
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public Order ship(Integer orderId) {
        Order order = findOrder(orderId);
        orderStatusHandlerRegistry.ship(order);
        return orderRepository.save(order);
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public Order deliver(Integer orderId) {
        Order order = findOrder(orderId);
        orderStatusHandlerRegistry.deliver(order);
        return orderRepository.save(order);
    }

    @Override
    public PaymentInitiationResult initiatePayment(Integer orderId, String callerUuid) {
        Order pending = paymentHandoffService.startPaymentProcessing(orderId, callerUuid);
        PaymentResult result = callGatewayOrFail(
                () -> paymentGatewayPort.charge(pending.getIdempotencyKey(), pending.getTotal()));
        Order resolved = paymentHandoffService.resolvePayment(orderId, result);
        return new PaymentInitiationResult(resolved, result.clientSecret());
    }

    private Order findOrder(Integer orderId) {
        return Validator.notFound(orderRepository.findById(orderId), EcommerceErrorCode.ORDER_NOT_FOUND, orderId);
    }
}
