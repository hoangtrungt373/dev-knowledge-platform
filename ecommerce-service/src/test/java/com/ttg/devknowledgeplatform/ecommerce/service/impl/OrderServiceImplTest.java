package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.orderstatus.OrderStatusHandlerRegistry;
import com.ttg.devknowledgeplatform.ecommerce.orderstatus.PaymentHandoffService;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentCancellationResult;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentGatewayException;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentGatewayPort;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentResult;
import com.ttg.devknowledgeplatform.ecommerce.payment.RefundResult;
import com.ttg.devknowledgeplatform.ecommerce.repository.OrderRepository;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrderServiceImpl} — US-3.5's ownership-checked get/list, US-3.7/3.8's
 * admin ship/deliver (thin wrappers around a mocked {@link OrderStatusHandlerRegistry}), and
 * US-3.3's {@link OrderServiceImpl#initiatePayment}/US-4.6's {@link OrderServiceImpl#cancel}
 * orchestration (mocked {@link PaymentHandoffService}/{@link PaymentGatewayPort} — the durable
 * steps and the gateway calls themselves are each covered by their own dedicated test class, not
 * re-verified here; this class only pins down the calling order/conditional-refund wiring).
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    private static final String OWNER_UUID = "owner-uuid-1";
    private static final String OTHER_UUID = "someone-else-uuid";

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderStatusHandlerRegistry orderStatusHandlerRegistry;
    @Mock
    private PaymentHandoffService paymentHandoffService;
    @Mock
    private PaymentGatewayPort paymentGatewayPort;

    @InjectMocks
    private OrderServiceImpl service;

    private static Order orderOwnedBy(String ownerUuid) {
        Order order = new Order();
        order.setId(1);
        order.setOwnerUuid(ownerUuid);
        order.setStatus(OrderStatus.PENDING);
        return order;
    }

    @Nested
    class GetOrder {

        @Test
        void returnsTheOrderWhenTheCallerOwnsIt() {
            Order order = orderOwnedBy(OWNER_UUID);
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));

            assertThat(service.getOrder(1, OWNER_UUID)).isSameAs(order);
        }

        @Test
        void rejectsAsNotFoundWhenTheOrderBelongsToSomeoneElse() {
            Order order = orderOwnedBy(OWNER_UUID);
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service.getOrder(1, OTHER_UUID))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.ORDER_NOT_FOUND);
        }

        @Test
        void rejectsAsNotFoundWhenTheOrderDoesNotExist() {
            when(orderRepository.findById(1)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getOrder(1, OWNER_UUID))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.ORDER_NOT_FOUND);
        }
    }

    @Nested
    class ListOrders {

        @Test
        void delegatesToTheRepositoryWithASpecificationBuiltFromOwnerAndNoStatusFilter() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Order> page = new PageImpl<>(List.of(orderOwnedBy(OWNER_UUID)));
            // Same reasoning as ListAllOrders below — the Specification is a fresh lambda built
            // inside listOrders, never equal by reference/value to one built here, so this only
            // verifies delegation (owner/statuses/page wiring), not the Specification's own
            // filtering logic.
            when(orderRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

            Page<Order> result = service.listOrders(OWNER_UUID, null, pageable);

            assertThat(result).isSameAs(page);
        }

        @Test
        void delegatesToTheRepositoryWithASpecificationBuiltFromOwnerAndAStatusFilter() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Order> page = new PageImpl<>(List.of(orderOwnedBy(OWNER_UUID)));
            when(orderRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

            Page<Order> result = service.listOrders(
                    OWNER_UUID, List.of(OrderStatus.PENDING, OrderStatus.PAYMENT_PROCESSING), pageable);

            assertThat(result).isSameAs(page);
        }
    }

    @Nested
    class ListAllOrders {

        @Test
        void delegatesToTheRepositoryWithASpecificationBuiltFromStatus() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Order> page = new PageImpl<>(List.of(orderOwnedBy(OWNER_UUID)));
            // The Specification itself is a fresh lambda built inside listAllOrders — never equal
            // by reference/value to one built here, so this only verifies delegation (page/filter
            // wiring), not the Specification's own filtering logic (same reasoning
            // ProductSpecification/ProductCategorySpecification are left to their own devices,
            // untested at the unit level, in this module).
            when(orderRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

            Page<Order> result = service.listAllOrders(OrderStatus.CONFIRMED, pageable);

            assertThat(result).isSameAs(page);
        }
    }

    @Nested
    class Cancel {

        @Test
        void returnsTheCancelledOrderAndNeverTouchesTheGatewayWhenNoRefundOrGatewayCancelIsOwed() {
            Order cancelled = orderOwnedBy(OWNER_UUID);
            cancelled.setStatus(OrderStatus.CANCELLED);
            PaymentHandoffService.CancellationResult cancellation =
                    new PaymentHandoffService.CancellationResult(cancelled, false, false, null, null, null);
            when(paymentHandoffService.applyCancellation(1, OWNER_UUID)).thenReturn(cancellation);

            Order result = service.cancel(1, OWNER_UUID);

            assertThat(result).isSameAs(cancelled);
            verify(paymentGatewayPort, never()).refund(any(), any());
            verify(paymentHandoffService, never()).applyRefundResult(any(), any());
            verify(paymentGatewayPort, never()).cancelUnconfirmed(any());
            verify(paymentHandoffService, never()).applyGatewayCancellation(any(), any());
        }

        @Test
        void issuesARefundAndAppliesTheResultWhenOneIsOwed() {
            Order cancelled = orderOwnedBy(OWNER_UUID);
            cancelled.setStatus(OrderStatus.CANCELLED);
            PaymentHandoffService.CancellationResult cancellation = new PaymentHandoffService.CancellationResult(
                    cancelled, true, false, 100, "gw-ref-1", new BigDecimal("25.00"));
            when(paymentHandoffService.applyCancellation(1, OWNER_UUID)).thenReturn(cancellation);
            RefundResult refundResult = RefundResult.succeeded("gw-refund-1");
            when(paymentGatewayPort.refund("gw-ref-1", new BigDecimal("25.00"))).thenReturn(refundResult);

            Order result = service.cancel(1, OWNER_UUID);

            assertThat(result).isSameAs(cancelled);
            verify(paymentGatewayPort).refund("gw-ref-1", new BigDecimal("25.00"));
            verify(paymentHandoffService).applyRefundResult(100, refundResult);
            verify(paymentGatewayPort, never()).cancelUnconfirmed(any());
        }

        @Test
        void cancelsTheUnconfirmedChargeAtTheGatewayAndReturnsTheAppliedResultWhenOneIsOwed() {
            // Option A follow-up — a shopper cancelling while their Stripe PaymentIntent is still
            // unconfirmed must actively void it at the gateway, since nothing else would ever
            // resolve the order otherwise (see PaymentHandoffService's own Javadoc for the incident).
            Order stillProcessing = orderOwnedBy(OWNER_UUID);
            stillProcessing.setStatus(OrderStatus.PAYMENT_PROCESSING);
            PaymentHandoffService.CancellationResult cancellation = new PaymentHandoffService.CancellationResult(
                    stillProcessing, false, true, 100, "gw-ref-1", null);
            when(paymentHandoffService.applyCancellation(1, OWNER_UUID)).thenReturn(cancellation);
            PaymentCancellationResult gatewayResult = PaymentCancellationResult.cancelled();
            when(paymentGatewayPort.cancelUnconfirmed("gw-ref-1")).thenReturn(gatewayResult);
            Order finallyCancelled = orderOwnedBy(OWNER_UUID);
            finallyCancelled.setStatus(OrderStatus.CANCELLED);
            when(paymentHandoffService.applyGatewayCancellation(1, gatewayResult)).thenReturn(finallyCancelled);

            Order result = service.cancel(1, OWNER_UUID);

            assertThat(result).isSameAs(finallyCancelled);
            verify(paymentGatewayPort).cancelUnconfirmed("gw-ref-1");
            verify(paymentHandoffService).applyGatewayCancellation(1, gatewayResult);
            verify(paymentGatewayPort, never()).refund(any(), any());
            verify(paymentHandoffService, never()).applyRefundResult(any(), any());
        }

        @Test
        void propagatesAnyExceptionFromApplyCancellationWithoutTouchingTheGateway() {
            when(paymentHandoffService.applyCancellation(1, OTHER_UUID))
                    .thenThrow(new BusinessException(EcommerceErrorCode.ORDER_NOT_FOUND, 1));

            assertThatThrownBy(() -> service.cancel(1, OTHER_UUID))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.ORDER_NOT_FOUND);
            verify(paymentGatewayPort, never()).refund(any(), any());
            verify(paymentHandoffService, never()).applyRefundResult(any(), any());
            verify(paymentGatewayPort, never()).cancelUnconfirmed(any());
            verify(paymentHandoffService, never()).applyGatewayCancellation(any(), any());
        }

        @Test
        void recoversWhenApplyGatewayCancellationLosesARaceAndTheOrderIsAlreadyCancelled() {
            // Bug fix regression: the webhook/reconciliation (or a second concurrent click)
            // finished resolving this same order to CANCELLED a moment before this call's own
            // applyGatewayCancellation ran — that's a race this method should tolerate, not error on.
            Order stillProcessing = orderOwnedBy(OWNER_UUID);
            stillProcessing.setStatus(OrderStatus.PAYMENT_PROCESSING);
            PaymentHandoffService.CancellationResult cancellation = new PaymentHandoffService.CancellationResult(
                    stillProcessing, false, true, 100, "gw-ref-1", null);
            when(paymentHandoffService.applyCancellation(1, OWNER_UUID)).thenReturn(cancellation);
            PaymentCancellationResult gatewayResult = PaymentCancellationResult.cancelled();
            when(paymentGatewayPort.cancelUnconfirmed("gw-ref-1")).thenReturn(gatewayResult);
            when(paymentHandoffService.applyGatewayCancellation(1, gatewayResult))
                    .thenThrow(new BusinessException(EcommerceErrorCode.ORDER_INVALID_STATUS_TRANSITION, "failPayment", OrderStatus.CANCELLED));
            Order alreadyCancelled = orderOwnedBy(OWNER_UUID);
            alreadyCancelled.setStatus(OrderStatus.CANCELLED);
            when(orderRepository.findById(1)).thenReturn(Optional.of(alreadyCancelled));

            Order result = service.cancel(1, OWNER_UUID);

            assertThat(result).isSameAs(alreadyCancelled);
        }

        @Test
        void rethrowsWhenTheOrderIsNotActuallyCancelledAfterAStatusTransitionConflict() {
            // The order ended up somewhere other than CANCELLED (e.g. still PAYMENT_PROCESSING, or
            // a genuinely different rejection) — this is not the tolerated race, so it must still
            // surface as a real error rather than being silently swallowed.
            Order stillProcessing = orderOwnedBy(OWNER_UUID);
            stillProcessing.setStatus(OrderStatus.PAYMENT_PROCESSING);
            PaymentHandoffService.CancellationResult cancellation = new PaymentHandoffService.CancellationResult(
                    stillProcessing, false, true, 100, "gw-ref-1", null);
            when(paymentHandoffService.applyCancellation(1, OWNER_UUID)).thenReturn(cancellation);
            PaymentCancellationResult gatewayResult = PaymentCancellationResult.cancelled();
            when(paymentGatewayPort.cancelUnconfirmed("gw-ref-1")).thenReturn(gatewayResult);
            when(paymentHandoffService.applyGatewayCancellation(1, gatewayResult))
                    .thenThrow(new BusinessException(EcommerceErrorCode.ORDER_INVALID_STATUS_TRANSITION, "failPayment", OrderStatus.SHIPPED));
            Order stillNotCancelled = orderOwnedBy(OWNER_UUID);
            stillNotCancelled.setStatus(OrderStatus.SHIPPED);
            when(orderRepository.findById(1)).thenReturn(Optional.of(stillNotCancelled));

            assertThatThrownBy(() -> service.cancel(1, OWNER_UUID))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.ORDER_INVALID_STATUS_TRANSITION);
        }

        @Test
        void recoversFromAnOptimisticLockConflictWhenTheOrderIsAlreadyCancelled() {
            Order stillProcessing = orderOwnedBy(OWNER_UUID);
            stillProcessing.setStatus(OrderStatus.PAYMENT_PROCESSING);
            PaymentHandoffService.CancellationResult cancellation = new PaymentHandoffService.CancellationResult(
                    stillProcessing, false, true, 100, "gw-ref-1", null);
            when(paymentHandoffService.applyCancellation(1, OWNER_UUID)).thenReturn(cancellation);
            PaymentCancellationResult gatewayResult = PaymentCancellationResult.cancelled();
            when(paymentGatewayPort.cancelUnconfirmed("gw-ref-1")).thenReturn(gatewayResult);
            when(paymentHandoffService.applyGatewayCancellation(1, gatewayResult))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Order.class, 1));
            Order alreadyCancelled = orderOwnedBy(OWNER_UUID);
            alreadyCancelled.setStatus(OrderStatus.CANCELLED);
            when(orderRepository.findById(1)).thenReturn(Optional.of(alreadyCancelled));

            Order result = service.cancel(1, OWNER_UUID);

            assertThat(result).isSameAs(alreadyCancelled);
        }

        @Test
        void translatesAPaymentGatewayExceptionFromRefundIntoAFriendlyApiException() {
            Order cancelled = orderOwnedBy(OWNER_UUID);
            cancelled.setStatus(OrderStatus.CANCELLED);
            PaymentHandoffService.CancellationResult cancellation = new PaymentHandoffService.CancellationResult(
                    cancelled, true, false, 100, "gw-ref-1", new BigDecimal("25.00"));
            when(paymentHandoffService.applyCancellation(1, OWNER_UUID)).thenReturn(cancellation);
            when(paymentGatewayPort.refund("gw-ref-1", new BigDecimal("25.00")))
                    .thenThrow(new PaymentGatewayException("Stripe refund failed"));

            assertThatThrownBy(() -> service.cancel(1, OWNER_UUID))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
            verify(paymentHandoffService, never()).applyRefundResult(any(), any());
        }

        @Test
        void translatesAPaymentGatewayExceptionFromCancelUnconfirmedIntoAFriendlyApiException() {
            Order stillProcessing = orderOwnedBy(OWNER_UUID);
            stillProcessing.setStatus(OrderStatus.PAYMENT_PROCESSING);
            PaymentHandoffService.CancellationResult cancellation = new PaymentHandoffService.CancellationResult(
                    stillProcessing, false, true, 100, "gw-ref-1", null);
            when(paymentHandoffService.applyCancellation(1, OWNER_UUID)).thenReturn(cancellation);
            when(paymentGatewayPort.cancelUnconfirmed("gw-ref-1"))
                    .thenThrow(new PaymentGatewayException("Stripe cancel failed"));

            assertThatThrownBy(() -> service.cancel(1, OWNER_UUID))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
            verify(paymentHandoffService, never()).applyGatewayCancellation(any(), any());
        }
    }

    @Nested
    class Ship {

        @Test
        void dispatchesToTheRegistryAndSaves() {
            Order order = orderOwnedBy(OWNER_UUID);
            order.setStatus(OrderStatus.CONFIRMED);
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);

            Order result = service.ship(1);

            verify(orderStatusHandlerRegistry).ship(order);
            assertThat(result).isSameAs(order);
        }

        @Test
        void rejectsAsNotFoundWhenTheOrderDoesNotExist() {
            when(orderRepository.findById(1)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.ship(1))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.ORDER_NOT_FOUND);
            verify(orderStatusHandlerRegistry, never()).ship(any());
        }
    }

    @Nested
    class Deliver {

        @Test
        void dispatchesToTheRegistryAndSaves() {
            Order order = orderOwnedBy(OWNER_UUID);
            order.setStatus(OrderStatus.SHIPPED);
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);

            Order result = service.deliver(1);

            verify(orderStatusHandlerRegistry).deliver(order);
            assertThat(result).isSameAs(order);
        }

        @Test
        void rejectsAsNotFoundWhenTheOrderDoesNotExist() {
            when(orderRepository.findById(1)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deliver(1))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.ORDER_NOT_FOUND);
            verify(orderStatusHandlerRegistry, never()).deliver(any());
        }
    }

    @Nested
    class InitiatePayment {

        @Test
        void chainsStartPaymentProcessingTheGatewayCallAndResolvePaymentInOrder() {
            Order pending = orderOwnedBy(OWNER_UUID);
            pending.setStatus(OrderStatus.PAYMENT_PROCESSING);
            pending.setIdempotencyKey("1");
            pending.setTotal(new BigDecimal("25.00"));
            Order confirmed = orderOwnedBy(OWNER_UUID);
            confirmed.setStatus(OrderStatus.CONFIRMED);
            PaymentResult result = PaymentResult.succeeded("gw-ref-1");
            when(paymentHandoffService.startPaymentProcessing(1, OWNER_UUID)).thenReturn(pending);
            when(paymentGatewayPort.charge("1", new BigDecimal("25.00"))).thenReturn(result);
            when(paymentHandoffService.resolvePayment(1, result)).thenReturn(confirmed);

            var returned = service.initiatePayment(1, OWNER_UUID);

            assertThat(returned.order()).isSameAs(confirmed);
            assertThat(returned.clientSecret()).isNull();
            verify(paymentHandoffService).startPaymentProcessing(1, OWNER_UUID);
            verify(paymentGatewayPort).charge("1", new BigDecimal("25.00"));
            verify(paymentHandoffService).resolvePayment(1, result);
        }

        @Test
        void returnsTheClientSecretWhenTheChargeIsStillPendingClientSideConfirmation() {
            // Option A (Stripe Elements): a fresh PaymentIntent comes back PENDING with a client
            // secret the gui needs to mount a PaymentElement — this must reach the controller.
            Order pending = orderOwnedBy(OWNER_UUID);
            pending.setStatus(OrderStatus.PAYMENT_PROCESSING);
            pending.setIdempotencyKey("1");
            pending.setTotal(new BigDecimal("25.00"));
            PaymentResult result = PaymentResult.pending("pi_1", "pi_1_secret_abc");
            when(paymentHandoffService.startPaymentProcessing(1, OWNER_UUID)).thenReturn(pending);
            when(paymentGatewayPort.charge("1", new BigDecimal("25.00"))).thenReturn(result);
            when(paymentHandoffService.resolvePayment(1, result)).thenReturn(pending);

            var returned = service.initiatePayment(1, OWNER_UUID);

            assertThat(returned.order()).isSameAs(pending);
            assertThat(returned.clientSecret()).isEqualTo("pi_1_secret_abc");
        }

        @Test
        void neverCallsTheGatewayOrResolvesWhenStartingPaymentProcessingFails() {
            when(paymentHandoffService.startPaymentProcessing(1, OWNER_UUID))
                    .thenThrow(new BusinessException(
                            EcommerceErrorCode.ORDER_INVALID_STATUS_TRANSITION, "startPaymentProcessing", OrderStatus.SHIPPED));

            assertThatThrownBy(() -> service.initiatePayment(1, OWNER_UUID)).isInstanceOf(ApiException.class);

            verify(paymentGatewayPort, never()).charge(any(), any());
            verify(paymentHandoffService, never()).resolvePayment(any(), any());
        }

        @Test
        void translatesAPaymentGatewayExceptionFromChargeIntoAFriendlyApiException() {
            // Bug fix: a genuine gateway outage during charge() must not leak as a raw, unmapped
            // 500 — the PAYMENT_PROCESSING transition above has already committed durably by this
            // point regardless (OrderReconciliationJob will still resolve it later), so translating
            // this into a friendly error only changes what the caller sees, not that guarantee.
            Order pending = orderOwnedBy(OWNER_UUID);
            pending.setStatus(OrderStatus.PAYMENT_PROCESSING);
            pending.setIdempotencyKey("1");
            pending.setTotal(new BigDecimal("25.00"));
            when(paymentHandoffService.startPaymentProcessing(1, OWNER_UUID)).thenReturn(pending);
            when(paymentGatewayPort.charge("1", new BigDecimal("25.00")))
                    .thenThrow(new PaymentGatewayException("Stripe charge failed"));

            assertThatThrownBy(() -> service.initiatePayment(1, OWNER_UUID))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
            verify(paymentHandoffService, never()).resolvePayment(any(), any());
        }
    }
}
