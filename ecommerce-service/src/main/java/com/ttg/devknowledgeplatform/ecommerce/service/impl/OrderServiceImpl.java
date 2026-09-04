package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.common.exception.Validator;
import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.orderstatus.OrderStatusHandlerRegistry;
import com.ttg.devknowledgeplatform.ecommerce.orderstatus.PaymentHandoffService;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentCancellationResult;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentGatewayPort;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentResult;
import com.ttg.devknowledgeplatform.ecommerce.payment.RefundResult;
import com.ttg.devknowledgeplatform.ecommerce.repository.OrderRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.spec.OrderSpecification;
import com.ttg.devknowledgeplatform.ecommerce.service.OrderService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

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
 * {@code @Transactional} either, since a refund is owed only when {@link PaymentHandoffService
 * #applyCancellation} reports one, and that refund gateway call must happen outside any local
 * transaction for the identical reason. An Option A follow-up added a third branch alongside
 * refund: when {@code applyCancellation} instead reports {@code gatewayCancellationNeeded()} (the
 * cancel only queued because payment is still an unconfirmed Stripe PaymentIntent — see {@link
 * PaymentHandoffService}'s own Javadoc), this method actively voids that charge attempt at the
 * gateway, again outside any transaction, before applying the result.
 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderStatusHandlerRegistry orderStatusHandlerRegistry;
    private final PaymentHandoffService paymentHandoffService;
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
        PaymentHandoffService.CancellationResult cancellation = paymentHandoffService.applyCancellation(orderId, callerUuid);
        if (cancellation.refundNeeded()) {
            RefundResult result = paymentGatewayPort.refund(cancellation.gatewayReference(), cancellation.amount());
            paymentHandoffService.applyRefundResult(cancellation.paymentId(), result);
            return cancellation.order();
        }
        if (cancellation.gatewayCancellationNeeded()) {
            PaymentCancellationResult result = paymentGatewayPort.cancelUnconfirmed(cancellation.gatewayReference());
            return paymentHandoffService.applyGatewayCancellation(orderId, result);
        }
        return cancellation.order();
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
        PaymentResult result = paymentGatewayPort.charge(pending.getIdempotencyKey(), pending.getTotal());
        Order resolved = paymentHandoffService.resolvePayment(orderId, result);
        return new PaymentInitiationResult(resolved, result.clientSecret());
    }

    private Order findOrder(Integer orderId) {
        return Validator.notFound(orderRepository.findById(orderId), EcommerceErrorCode.ORDER_NOT_FOUND, orderId);
    }
}
