package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.orderstatus.OrderStatusHandlerRegistry;
import com.ttg.devknowledgeplatform.ecommerce.orderstatus.PaymentCancellationService;
import com.ttg.devknowledgeplatform.ecommerce.orderstatus.PaymentHandoffService;
import com.ttg.devknowledgeplatform.ecommerce.orderstatus.PaymentReconciliationService;
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
 * orchestration (mocked {@link PaymentHandoffService}/{@link PaymentCancellationService}/
 * {@link PaymentGatewayPort} — the durable steps and the gateway calls themselves are each covered
 * by their own dedicated test class, not re-verified here; this class only pins down the calling
 * order/conditional-refund wiring).
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
    private PaymentCancellationService paymentCancellationService;
    @Mock
    private PaymentReconciliationService paymentReconciliationService;
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
            PaymentCancellationService.CancellationResult cancellation =
                    new PaymentCancellationService.CancellationResult(cancelled, false, false, null, null, null);
            when(paymentCancellationService.applyCancellation(1, OWNER_UUID)).thenReturn(cancellation);

            Order result = service.cancel(1, OWNER_UUID);

            assertThat(result).isSameAs(cancelled);
            verify(paymentGatewayPort, never()).refund(any(), any());
            verify(paymentCancellationService, never()).applyRefundResult(any(), any());
            verify(paymentGatewayPort, never()).cancelUnconfirmed(any());
            verify(paymentCancellationService, never()).applyGatewayCancellation(any(), any());
        }

        @Test
        void issuesARefundAndAppliesTheResultWhenOneIsOwed() {
            Order cancelled = orderOwnedBy(OWNER_UUID);
            cancelled.setStatus(OrderStatus.CANCELLED);
            PaymentCancellationService.CancellationResult cancellation = new PaymentCancellationService.CancellationResult(
                    cancelled, true, false, 100, "gw-ref-1", new BigDecimal("25.00"));
            when(paymentCancellationService.applyCancellation(1, OWNER_UUID)).thenReturn(cancellation);
            RefundResult refundResult = RefundResult.succeeded("gw-refund-1");
            when(paymentGatewayPort.refund("gw-ref-1", new BigDecimal("25.00"))).thenReturn(refundResult);

            Order result = service.cancel(1, OWNER_UUID);

            assertThat(result).isSameAs(cancelled);
            verify(paymentGatewayPort).refund("gw-ref-1", new BigDecimal("25.00"));
            verify(paymentCancellationService).applyRefundResult(100, refundResult);
            verify(paymentGatewayPort, never()).cancelUnconfirmed(any());
        }

        @Test
        void cancelsTheUnconfirmedChargeAtTheGatewayAndReturnsTheAppliedResultWhenOneIsOwed() {
            // Option A follow-up — a shopper cancelling while their Stripe PaymentIntent is still
            // unconfirmed must actively void it at the gateway, since nothing else would ever
            // resolve the order otherwise (see PaymentHandoffService's own Javadoc for the incident).
            Order stillProcessing = orderOwnedBy(OWNER_UUID);
            stillProcessing.setStatus(OrderStatus.PAYMENT_PROCESSING);
            PaymentCancellationService.CancellationResult cancellation = new PaymentCancellationService.CancellationResult(
                    stillProcessing, false, true, 100, "gw-ref-1", null);
            when(paymentCancellationService.applyCancellation(1, OWNER_UUID)).thenReturn(cancellation);
            PaymentCancellationResult gatewayResult = PaymentCancellationResult.cancelled();
            when(paymentGatewayPort.cancelUnconfirmed("gw-ref-1")).thenReturn(gatewayResult);
            Order finallyCancelled = orderOwnedBy(OWNER_UUID);
            finallyCancelled.setStatus(OrderStatus.CANCELLED);
            when(paymentCancellationService.applyGatewayCancellation(1, gatewayResult)).thenReturn(finallyCancelled);

            Order result = service.cancel(1, OWNER_UUID);

            assertThat(result).isSameAs(finallyCancelled);
            verify(paymentGatewayPort).cancelUnconfirmed("gw-ref-1");
            verify(paymentCancellationService).applyGatewayCancellation(1, gatewayResult);
            verify(paymentGatewayPort, never()).refund(any(), any());
            verify(paymentCancellationService, never()).applyRefundResult(any(), any());
        }

        @Test
        void propagatesAnyExceptionFromApplyCancellationWithoutTouchingTheGateway() {
            when(paymentCancellationService.applyCancellation(1, OTHER_UUID))
                    .thenThrow(new BusinessException(EcommerceErrorCode.ORDER_NOT_FOUND, 1));

            assertThatThrownBy(() -> service.cancel(1, OTHER_UUID))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.ORDER_NOT_FOUND);
            verify(paymentGatewayPort, never()).refund(any(), any());
            verify(paymentCancellationService, never()).applyRefundResult(any(), any());
            verify(paymentGatewayPort, never()).cancelUnconfirmed(any());
            verify(paymentCancellationService, never()).applyGatewayCancellation(any(), any());
        }

        @Test
        void recoversWhenApplyGatewayCancellationLosesARaceAndTheOrderIsAlreadyCancelled() {
            // Bug fix regression: the webhook/reconciliation (or a second concurrent click)
            // finished resolving this same order to CANCELLED a moment before this call's own
            // applyGatewayCancellation ran — that's a race this method should tolerate, not error on.
            Order stillProcessing = orderOwnedBy(OWNER_UUID);
            stillProcessing.setStatus(OrderStatus.PAYMENT_PROCESSING);
            PaymentCancellationService.CancellationResult cancellation = new PaymentCancellationService.CancellationResult(
                    stillProcessing, false, true, 100, "gw-ref-1", null);
            when(paymentCancellationService.applyCancellation(1, OWNER_UUID)).thenReturn(cancellation);
            PaymentCancellationResult gatewayResult = PaymentCancellationResult.cancelled();
            when(paymentGatewayPort.cancelUnconfirmed("gw-ref-1")).thenReturn(gatewayResult);
            when(paymentCancellationService.applyGatewayCancellation(1, gatewayResult))
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
            PaymentCancellationService.CancellationResult cancellation = new PaymentCancellationService.CancellationResult(
                    stillProcessing, false, true, 100, "gw-ref-1", null);
            when(paymentCancellationService.applyCancellation(1, OWNER_UUID)).thenReturn(cancellation);
            PaymentCancellationResult gatewayResult = PaymentCancellationResult.cancelled();
            when(paymentGatewayPort.cancelUnconfirmed("gw-ref-1")).thenReturn(gatewayResult);
            when(paymentCancellationService.applyGatewayCancellation(1, gatewayResult))
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
            PaymentCancellationService.CancellationResult cancellation = new PaymentCancellationService.CancellationResult(
                    stillProcessing, false, true, 100, "gw-ref-1", null);
            when(paymentCancellationService.applyCancellation(1, OWNER_UUID)).thenReturn(cancellation);
            PaymentCancellationResult gatewayResult = PaymentCancellationResult.cancelled();
            when(paymentGatewayPort.cancelUnconfirmed("gw-ref-1")).thenReturn(gatewayResult);
            when(paymentCancellationService.applyGatewayCancellation(1, gatewayResult))
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
            PaymentCancellationService.CancellationResult cancellation = new PaymentCancellationService.CancellationResult(
                    cancelled, true, false, 100, "gw-ref-1", new BigDecimal("25.00"));
            when(paymentCancellationService.applyCancellation(1, OWNER_UUID)).thenReturn(cancellation);
            when(paymentGatewayPort.refund("gw-ref-1", new BigDecimal("25.00")))
                    .thenThrow(new PaymentGatewayException("Stripe refund failed"));

            assertThatThrownBy(() -> service.cancel(1, OWNER_UUID))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
            verify(paymentCancellationService, never()).applyRefundResult(any(), any());
        }

        @Test
        void translatesAPaymentGatewayExceptionFromCancelUnconfirmedIntoAFriendlyApiException() {
            Order stillProcessing = orderOwnedBy(OWNER_UUID);
            stillProcessing.setStatus(OrderStatus.PAYMENT_PROCESSING);
            PaymentCancellationService.CancellationResult cancellation = new PaymentCancellationService.CancellationResult(
                    stillProcessing, false, true, 100, "gw-ref-1", null);
            when(paymentCancellationService.applyCancellation(1, OWNER_UUID)).thenReturn(cancellation);
            when(paymentGatewayPort.cancelUnconfirmed("gw-ref-1"))
                    .thenThrow(new PaymentGatewayException("Stripe cancel failed"));

            assertThatThrownBy(() -> service.cancel(1, OWNER_UUID))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
            verify(paymentCancellationService, never()).applyGatewayCancellation(any(), any());
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

        @Test
        void aReEntrantCallOnAnAlreadyProcessingOrderChecksLiveStatusInsteadOfReplayingCharge() {
            // Bug fix: the shopper reloading the page (or a "Continue Payment" action) while
            // already PAYMENT_PROCESSING must not replay charge() — Stripe's own idempotent replay
            // would return the frozen response from the ORIGINAL create() call, never a live
            // re-fetch, so it could hand back a stale "still needs payment" snapshot even if the
            // shopper already paid on another tab in the meantime. checkStatus() does a live
            // retrieve instead.
            Order alreadyProcessing = orderOwnedBy(OWNER_UUID);
            alreadyProcessing.setStatus(OrderStatus.PAYMENT_PROCESSING);
            alreadyProcessing.setIdempotencyKey("1");
            when(orderRepository.findById(1)).thenReturn(Optional.of(alreadyProcessing));
            Order pending = orderOwnedBy(OWNER_UUID);
            pending.setStatus(OrderStatus.PAYMENT_PROCESSING);
            pending.setIdempotencyKey("1");
            pending.setTotal(new BigDecimal("25.00"));
            when(paymentHandoffService.startPaymentProcessing(1, OWNER_UUID)).thenReturn(pending);
            PaymentResult stillOpen = PaymentResult.pending("pi_1", "pi_1_secret_abc");
            when(paymentGatewayPort.checkStatus("1")).thenReturn(stillOpen);
            when(paymentHandoffService.resolvePayment(1, stillOpen)).thenReturn(pending);

            var returned = service.initiatePayment(1, OWNER_UUID);

            assertThat(returned.clientSecret()).isEqualTo("pi_1_secret_abc");
            verify(paymentGatewayPort, never()).charge(any(), any());
            verify(paymentGatewayPort).checkStatus("1");
            verify(paymentHandoffService).resolvePayment(1, stillOpen);
        }

        @Test
        void aReEntrantCallThatDiscoversThePaymentAlreadySucceededFinalizesInsteadOfShowingAStalePaymentForm() {
            Order alreadyProcessing = orderOwnedBy(OWNER_UUID);
            alreadyProcessing.setStatus(OrderStatus.PAYMENT_PROCESSING);
            alreadyProcessing.setIdempotencyKey("1");
            when(orderRepository.findById(1)).thenReturn(Optional.of(alreadyProcessing));
            Order pending = orderOwnedBy(OWNER_UUID);
            pending.setStatus(OrderStatus.PAYMENT_PROCESSING);
            pending.setIdempotencyKey("1");
            pending.setTotal(new BigDecimal("25.00"));
            when(paymentHandoffService.startPaymentProcessing(1, OWNER_UUID)).thenReturn(pending);
            PaymentResult alreadySucceeded = PaymentResult.succeeded("pi_1");
            when(paymentGatewayPort.checkStatus("1")).thenReturn(alreadySucceeded);
            Order confirmed = orderOwnedBy(OWNER_UUID);
            confirmed.setStatus(OrderStatus.CONFIRMED);
            when(paymentHandoffService.resolvePayment(1, alreadySucceeded)).thenReturn(confirmed);

            var returned = service.initiatePayment(1, OWNER_UUID);

            assertThat(returned.order()).isSameAs(confirmed);
            assertThat(returned.clientSecret()).isNull();
            verify(paymentGatewayPort, never()).charge(any(), any());
        }

        @Test
        void translatesAConcurrentReservationExpiryIntoACleanErrorAfterAStatusTransitionConflict() {
            // Bug fix: a shopper resuming payment on a still-PENDING order can race
            // OrderReservationExpiryJob's own sweep of the same order right at the edge of the
            // reservation timeout. The loser must not see a raw, internal-method-named
            // ORDER_INVALID_STATUS_TRANSITION — it should surface a clean, actionable
            // ORDER_RESERVATION_EXPIRED instead.
            when(paymentHandoffService.startPaymentProcessing(1, OWNER_UUID))
                    .thenThrow(new BusinessException(
                            EcommerceErrorCode.ORDER_INVALID_STATUS_TRANSITION, "startPaymentProcessing", OrderStatus.EXPIRED));
            Order expired = orderOwnedBy(OWNER_UUID);
            expired.setStatus(OrderStatus.EXPIRED);
            when(orderRepository.findById(1)).thenReturn(Optional.of(expired));

            assertThatThrownBy(() -> service.initiatePayment(1, OWNER_UUID))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.ORDER_RESERVATION_EXPIRED);
            verify(paymentGatewayPort, never()).charge(any(), any());
            verify(paymentGatewayPort, never()).checkStatus(any());
        }

        @Test
        void translatesAnOptimisticLockConflictIntoACleanErrorWhenTheOrderIsAlreadyExpired() {
            when(paymentHandoffService.startPaymentProcessing(1, OWNER_UUID))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Order.class, 1));
            Order expired = orderOwnedBy(OWNER_UUID);
            expired.setStatus(OrderStatus.EXPIRED);
            when(orderRepository.findById(1)).thenReturn(Optional.of(expired));

            assertThatThrownBy(() -> service.initiatePayment(1, OWNER_UUID))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.ORDER_RESERVATION_EXPIRED);
        }

        @Test
        void rethrowsWhenTheOrderIsNotActuallyExpiredAfterAStatusTransitionConflict() {
            // Not the tolerated race — a genuinely different rejection must still surface as-is
            // rather than being silently swallowed or mislabeled as an expiry.
            when(paymentHandoffService.startPaymentProcessing(1, OWNER_UUID))
                    .thenThrow(new BusinessException(
                            EcommerceErrorCode.ORDER_INVALID_STATUS_TRANSITION, "startPaymentProcessing", OrderStatus.SHIPPED));
            Order stillNotExpired = orderOwnedBy(OWNER_UUID);
            stillNotExpired.setStatus(OrderStatus.SHIPPED);
            when(orderRepository.findById(1)).thenReturn(Optional.of(stillNotExpired));

            assertThatThrownBy(() -> service.initiatePayment(1, OWNER_UUID))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.ORDER_INVALID_STATUS_TRANSITION);
        }
    }

    @Nested
    class ReconcilePayment {

        @Test
        void delegatesToPaymentReconciliationServiceWhenOwnershipChecksOut() {
            Order order = orderOwnedBy(OWNER_UUID);
            order.setStatus(OrderStatus.PAYMENT_PROCESSING);
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));
            Order resolved = orderOwnedBy(OWNER_UUID);
            resolved.setStatus(OrderStatus.CONFIRMED);
            when(paymentReconciliationService.reconcileNow(1)).thenReturn(resolved);

            Order result = service.reconcilePayment(1, OWNER_UUID);

            assertThat(result).isSameAs(resolved);
            verify(paymentReconciliationService).reconcileNow(1);
        }

        @Test
        void rejectsAsNotFoundWhenTheOrderBelongsToSomeoneElse() {
            Order order = orderOwnedBy(OWNER_UUID);
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service.reconcilePayment(1, OTHER_UUID))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.ORDER_NOT_FOUND);
            verify(paymentReconciliationService, never()).reconcileNow(any());
        }

        @Test
        void rejectsAsNotFoundWhenTheOrderDoesNotExist() {
            when(orderRepository.findById(1)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.reconcilePayment(1, OWNER_UUID))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.ORDER_NOT_FOUND);
        }

        @Test
        void translatesAPaymentGatewayExceptionIntoAFriendlyApiException() {
            Order order = orderOwnedBy(OWNER_UUID);
            order.setStatus(OrderStatus.PAYMENT_PROCESSING);
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));
            when(paymentReconciliationService.reconcileNow(1))
                    .thenThrow(new PaymentGatewayException("Stripe checkStatus failed"));

            assertThatThrownBy(() -> service.reconcilePayment(1, OWNER_UUID))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
        }

        @Test
        void recoversWhenReconcileNowLosesARaceAndTheOrderAlreadyReachedATerminalStatus() {
            // Bug fix regression: OrderReconciliationJob's own scheduled poll (or a second browser
            // tab hitting this same endpoint) finished reconciling this order to EXPIRED a moment
            // before this call's own reconcileNow ran — that's a race this method should tolerate,
            // not error on. Unlike Cancel's own equivalent test, the recovered-to status here is
            // whatever reconcileNow actually landed on, not always the same one target status.
            Order alreadyExpired = orderOwnedBy(OWNER_UUID);
            alreadyExpired.setStatus(OrderStatus.EXPIRED);
            when(orderRepository.findById(1)).thenReturn(Optional.of(alreadyExpired));
            when(paymentReconciliationService.reconcileNow(1)).thenThrow(
                    new BusinessException(EcommerceErrorCode.ORDER_INVALID_STATUS_TRANSITION, "failPayment", OrderStatus.EXPIRED));

            Order result = service.reconcilePayment(1, OWNER_UUID);

            assertThat(result).isSameAs(alreadyExpired);
        }

        @Test
        void recoversWhenReconcileNowLosesARaceAndTheOrderAlreadyReachedAnyOtherTerminalStatus() {
            // Confirms the recovery isn't hardcoded to one specific status the way Cancel's own
            // is (CANCELLED only) — reconcileNow can land the order on CONFIRMED/FAILED just as
            // easily as EXPIRED/CANCELLED, depending on which racer won.
            Order alreadyConfirmed = orderOwnedBy(OWNER_UUID);
            alreadyConfirmed.setStatus(OrderStatus.CONFIRMED);
            when(orderRepository.findById(1)).thenReturn(Optional.of(alreadyConfirmed));
            when(paymentReconciliationService.reconcileNow(1)).thenThrow(
                    new ObjectOptimisticLockingFailureException(Order.class, 1));

            Order result = service.reconcilePayment(1, OWNER_UUID);

            assertThat(result).isSameAs(alreadyConfirmed);
        }

        @Test
        void rethrowsWhenTheOrderIsStillPaymentProcessingAfterAStatusTransitionConflict() {
            // Not the tolerated race — the order never actually moved, so this must still surface
            // as a real error rather than being silently swallowed.
            Order stillProcessing = orderOwnedBy(OWNER_UUID);
            stillProcessing.setStatus(OrderStatus.PAYMENT_PROCESSING);
            when(orderRepository.findById(1)).thenReturn(Optional.of(stillProcessing));
            when(paymentReconciliationService.reconcileNow(1)).thenThrow(
                    new BusinessException(EcommerceErrorCode.ORDER_INVALID_STATUS_TRANSITION, "failPayment", OrderStatus.PAYMENT_PROCESSING));

            assertThatThrownBy(() -> service.reconcilePayment(1, OWNER_UUID))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.ORDER_INVALID_STATUS_TRANSITION);
        }
    }
}
