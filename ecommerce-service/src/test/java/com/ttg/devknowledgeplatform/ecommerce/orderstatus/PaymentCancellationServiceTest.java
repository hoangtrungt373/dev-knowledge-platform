package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.entity.OutboxEvent;
import com.ttg.devknowledgeplatform.ecommerce.entity.Payment;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.enums.OutboxAggregateType;
import com.ttg.devknowledgeplatform.ecommerce.enums.PaymentStatus;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentCancellationResult;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentResult;
import com.ttg.devknowledgeplatform.ecommerce.payment.RefundResult;
import com.ttg.devknowledgeplatform.ecommerce.repository.OrderRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.OutboxEventRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.PaymentRepository;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PaymentCancellationService} — the cancellation/refund lifecycle split out
 * of {@link PaymentHandoffService} (see that class's own Javadoc for the God-class split this test
 * class mirrors). {@link PaymentHandoffService} itself is mocked here, exactly the same way
 * {@link OrderStatusHandlerRegistry} already is — {@link #applyGatewayCancellation}'s
 * {@code ALREADY_RESOLVED} branch delegates to it directly rather than reimplementing
 * {@code resolvePayment}'s own logic, so its own {@link PaymentHandoffServiceTest} is where that
 * logic is actually exercised.
 */
@ExtendWith(MockitoExtension.class)
class PaymentCancellationServiceTest {

    private static final String OWNER_UUID = "owner-uuid-1";

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private OrderStatusHandlerRegistry orderStatusHandlerRegistry;
    @Mock
    private PaymentHandoffService paymentHandoffService;

    @InjectMocks
    private PaymentCancellationService service;

    private static Order orderOwnedBy(String ownerUuid) {
        Order order = new Order();
        order.setId(1);
        order.setOwnerUuid(ownerUuid);
        order.setStatus(OrderStatus.PENDING);
        order.setIdempotencyKey("1");
        order.setTotal(new BigDecimal("25.00"));
        return order;
    }

    @Nested
    class ApplyCancellation {

        @Test
        void reportsNoRefundWhenTheOrderTransitionsToCancelledWithNoSucceededPayment() {
            Order order = orderOwnedBy(OWNER_UUID);
            order.setStatus(OrderStatus.PENDING);
            doAnswer(invocation -> {
                order.setStatus(OrderStatus.CANCELLED);
                return null;
            }).when(orderStatusHandlerRegistry).cancel(order);
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);
            when(paymentRepository.findByOrderId(1)).thenReturn(Optional.empty());

            PaymentCancellationService.CancellationResult result = service.applyCancellation(1, OWNER_UUID);

            assertThat(result.order()).isSameAs(order);
            assertThat(result.refundNeeded()).isFalse();
            assertThat(result.gatewayCancellationNeeded()).isFalse();
        }

        @Test
        void reportsARefundWhenTheOrderTransitionsToCancelledWithASucceededPayment() {
            Order order = orderOwnedBy(OWNER_UUID);
            order.setStatus(OrderStatus.CONFIRMED);
            doAnswer(invocation -> {
                order.setStatus(OrderStatus.CANCELLED);
                return null;
            }).when(orderStatusHandlerRegistry).cancel(order);
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);
            Payment payment = new Payment();
            payment.setId(100);
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setGatewayReference("gw-ref-1");
            payment.setAmount(new BigDecimal("25.00"));
            when(paymentRepository.findByOrderId(1)).thenReturn(Optional.of(payment));

            PaymentCancellationService.CancellationResult result = service.applyCancellation(1, OWNER_UUID);

            assertThat(result.refundNeeded()).isTrue();
            assertThat(result.gatewayCancellationNeeded()).isFalse();
            assertThat(result.paymentId()).isEqualTo(100);
            assertThat(result.gatewayReference()).isEqualTo("gw-ref-1");
            assertThat(result.amount()).isEqualByComparingTo("25.00");
        }

        @Test
        void reportsNeitherWhenTheCancelOnlyQueuesAndNoPaymentRowExistsYet() {
            Order order = orderOwnedBy(OWNER_UUID);
            order.setStatus(OrderStatus.PAYMENT_PROCESSING);
            // The mocked registry doesn't mutate the order's status at all here, mirroring
            // PaymentProcessingOrderStatusHandler.cancel — it only queues (cancelRequested), it
            // never transitions. No Payment row at all is the (rare) window right before
            // startPaymentProcessing's own write — nothing to cancel at the gateway yet either.
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);
            when(paymentRepository.findByOrderId(1)).thenReturn(Optional.empty());

            PaymentCancellationService.CancellationResult result = service.applyCancellation(1, OWNER_UUID);

            assertThat(result.refundNeeded()).isFalse();
            assertThat(result.gatewayCancellationNeeded()).isFalse();
        }

        @Test
        void reportsGatewayCancellationNeededWhenTheCancelOnlyQueuesAndPaymentIsStillPendingWithAGatewayReference() {
            // The realistic Option A case: the shopper cancels while their Stripe PaymentIntent is
            // still unconfirmed — nothing else will ever resolve the queued cancel, so the caller
            // must actively void the charge attempt at the gateway (see this class's own Javadoc).
            Order order = orderOwnedBy(OWNER_UUID);
            order.setStatus(OrderStatus.PAYMENT_PROCESSING);
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);
            Payment payment = new Payment();
            payment.setId(100);
            payment.setStatus(PaymentStatus.PENDING);
            payment.setGatewayReference("gw-ref-1");
            when(paymentRepository.findByOrderId(1)).thenReturn(Optional.of(payment));

            PaymentCancellationService.CancellationResult result = service.applyCancellation(1, OWNER_UUID);

            assertThat(result.refundNeeded()).isFalse();
            assertThat(result.gatewayCancellationNeeded()).isTrue();
            assertThat(result.paymentId()).isEqualTo(100);
            assertThat(result.gatewayReference()).isEqualTo("gw-ref-1");
        }

        @Test
        void reportsNoGatewayCancellationNeededWhenThePendingPaymentHasNoGatewayReferenceYet() {
            // charge() hasn't even reached Stripe for this attempt yet (e.g. crashed right after
            // startPaymentProcessing committed) — nothing exists at the gateway to cancel.
            Order order = orderOwnedBy(OWNER_UUID);
            order.setStatus(OrderStatus.PAYMENT_PROCESSING);
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);
            Payment payment = new Payment();
            payment.setId(100);
            payment.setStatus(PaymentStatus.PENDING);
            when(paymentRepository.findByOrderId(1)).thenReturn(Optional.of(payment));

            PaymentCancellationService.CancellationResult result = service.applyCancellation(1, OWNER_UUID);

            assertThat(result.refundNeeded()).isFalse();
            assertThat(result.gatewayCancellationNeeded()).isFalse();
        }

        @Test
        void rejectsAsNotFoundWhenTheOrderBelongsToSomeoneElse() {
            Order order = orderOwnedBy(OWNER_UUID);
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service.applyCancellation(1, "someone-else-uuid"))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.ORDER_NOT_FOUND);
            verify(orderStatusHandlerRegistry, never()).cancel(any());
        }

        @Test
        void rejectsAsNotFoundWhenTheOrderDoesNotExist() {
            when(orderRepository.findById(1)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.applyCancellation(1, OWNER_UUID))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.ORDER_NOT_FOUND);
        }
    }

    @Nested
    class ApplyGatewayCancellation {

        @Test
        void cancelledDispatchesToFailPaymentAndMarksThePaymentRowCancelledWithNoOutboxEvent() {
            Order order = orderOwnedBy(OWNER_UUID);
            order.setStatus(OrderStatus.PAYMENT_PROCESSING);
            doAnswer(invocation -> {
                order.setStatus(OrderStatus.CANCELLED);
                return null;
            }).when(orderStatusHandlerRegistry).failPayment(order);
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);
            Payment payment = new Payment();
            payment.setId(100);
            payment.setStatus(PaymentStatus.PENDING);
            payment.setGatewayReference("gw-ref-1");
            when(paymentRepository.findByOrderId(1)).thenReturn(Optional.of(payment));

            Order result = service.applyGatewayCancellation(1, PaymentCancellationResult.cancelled());

            assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            verify(orderStatusHandlerRegistry).failPayment(order);
            verify(orderStatusHandlerRegistry, never()).confirmPayment(any());
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
            verify(paymentRepository).save(payment);
            verify(outboxEventRepository, never()).save(any());
            verify(paymentHandoffService, never()).resolvePayment(any(), any());
        }

        @Test
        void alreadyResolvedDelegatesStraightToPaymentHandoffServiceWithTheGatewaysRealResult() {
            // The race this branch exists for: the shopper confirmed on another tab a moment
            // before the gateway cancel call arrived — the caller must not force a cancellation
            // that never actually happened, and must not reimplement resolvePayment's own logic
            // here — that's PaymentHandoffServiceTest's job to verify.
            PaymentResult succeeded = PaymentResult.succeeded("gw-ref-1");
            Order order = orderOwnedBy(OWNER_UUID);
            when(paymentHandoffService.resolvePayment(1, succeeded)).thenReturn(order);

            Order result = service.applyGatewayCancellation(1, PaymentCancellationResult.alreadyResolved(succeeded));

            verify(paymentHandoffService).resolvePayment(1, succeeded);
            assertThat(result).isSameAs(order);
            verify(orderRepository, never()).findById(any());
            verify(orderStatusHandlerRegistry, never()).failPayment(any());
        }

        @Test
        void throwsIllegalStateWhenNoPaymentRowExistsForTheCancelledOutcome() {
            Order order = orderOwnedBy(OWNER_UUID);
            order.setStatus(OrderStatus.PAYMENT_PROCESSING);
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);
            when(paymentRepository.findByOrderId(1)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.applyGatewayCancellation(1, PaymentCancellationResult.cancelled()))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void rejectsAsNotFoundWhenTheOrderDoesNotExist() {
            when(orderRepository.findById(1)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.applyGatewayCancellation(1, PaymentCancellationResult.cancelled()))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.ORDER_NOT_FOUND);
        }
    }

    @Nested
    class ApplyAbandonmentExpiry {

        @Test
        void cancelledDispatchesToExpireAndMarksThePaymentRowCancelledWithNoOutboxEvent() {
            Order order = orderOwnedBy(OWNER_UUID);
            order.setStatus(OrderStatus.PAYMENT_PROCESSING);
            doAnswer(invocation -> {
                order.setStatus(OrderStatus.EXPIRED);
                return null;
            }).when(orderStatusHandlerRegistry).expire(order);
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);
            Payment payment = new Payment();
            payment.setId(100);
            payment.setStatus(PaymentStatus.PENDING);
            payment.setGatewayReference("gw-ref-1");
            when(paymentRepository.findByOrderId(1)).thenReturn(Optional.of(payment));

            Order result = service.applyAbandonmentExpiry(1, PaymentCancellationResult.cancelled());

            assertThat(result.getStatus()).isEqualTo(OrderStatus.EXPIRED);
            verify(orderStatusHandlerRegistry).expire(order);
            verify(orderStatusHandlerRegistry, never()).failPayment(any());
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
            verify(paymentRepository).save(payment);
            verify(outboxEventRepository, never()).save(any());
            verify(paymentHandoffService, never()).resolvePayment(any(), any());
        }

        @Test
        void alreadyResolvedDelegatesStraightToPaymentHandoffServiceWithTheGatewaysRealResult() {
            // Mirrors ApplyGatewayCancellation's own equivalent case — the shopper confirmed on
            // another tab a moment before the abandonment cancel call reached the gateway.
            PaymentResult succeeded = PaymentResult.succeeded("gw-ref-1");
            Order order = orderOwnedBy(OWNER_UUID);
            when(paymentHandoffService.resolvePayment(1, succeeded)).thenReturn(order);

            Order result = service.applyAbandonmentExpiry(1, PaymentCancellationResult.alreadyResolved(succeeded));

            verify(paymentHandoffService).resolvePayment(1, succeeded);
            assertThat(result).isSameAs(order);
            verify(orderRepository, never()).findById(any());
            verify(orderStatusHandlerRegistry, never()).expire(any());
        }

        @Test
        void throwsIllegalStateWhenNoPaymentRowExistsForTheCancelledOutcome() {
            Order order = orderOwnedBy(OWNER_UUID);
            order.setStatus(OrderStatus.PAYMENT_PROCESSING);
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);
            when(paymentRepository.findByOrderId(1)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.applyAbandonmentExpiry(1, PaymentCancellationResult.cancelled()))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void rejectsAsNotFoundWhenTheOrderDoesNotExist() {
            when(orderRepository.findById(1)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.applyAbandonmentExpiry(1, PaymentCancellationResult.cancelled()))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.ORDER_NOT_FOUND);
        }
    }

    @Nested
    class ApplyRefundResult {

        @Test
        void succeededMarksThePaymentRowRefundedAndPublishesTheOutboxEvent() {
            Order order = orderOwnedBy(OWNER_UUID);
            Payment payment = new Payment();
            payment.setId(100);
            payment.setOrder(order);
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setAmount(new BigDecimal("25.00"));
            payment.setGatewayReference("gw-ref-1");
            when(paymentRepository.findById(100)).thenReturn(Optional.of(payment));

            service.applyRefundResult(100, RefundResult.succeeded("gw-refund-1"));

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            verify(paymentRepository).save(payment);

            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(captor.capture());
            OutboxEvent event = captor.getValue();
            assertThat(event.getEventType()).isEqualTo(PaymentRefundedOutboxEventHandler.EVENT_TYPE);
            assertThat(event.getAggregateType()).isEqualTo(OutboxAggregateType.PAYMENT);
            assertThat(event.getAggregateId()).isEqualTo(100);
            assertThat(event.getPayload())
                    .containsEntry("orderId", 1)
                    .containsEntry("amount", "25.00")
                    .containsEntry("gatewayReference", "gw-ref-1");
        }

        @Test
        void succeededIsASafeNoOpWhenTheRowIsAlreadyRefunded() {
            // Edge-case fix: RefundReconciliationJob's own poll can race
            // OrderServiceImpl#cancel's synchronous refund call for the same payment — both
            // gateway calls are already safe (Stripe's own refund idempotency key), but without
            // this guard, both callers would each re-publish PAYMENT_REFUNDED for a row a
            // concurrent caller already resolved.
            Payment payment = new Payment();
            payment.setId(100);
            payment.setStatus(PaymentStatus.REFUNDED);
            when(paymentRepository.findById(100)).thenReturn(Optional.of(payment));

            service.applyRefundResult(100, RefundResult.succeeded("gw-refund-1"));

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            verify(paymentRepository, never()).save(any());
            verify(outboxEventRepository, never()).save(any());
        }

        @Test
        void failedLeavesThePaymentRowSucceededAndPublishesNoEvent() {
            Payment payment = new Payment();
            payment.setId(100);
            payment.setStatus(PaymentStatus.SUCCEEDED);
            when(paymentRepository.findById(100)).thenReturn(Optional.of(payment));

            service.applyRefundResult(100, RefundResult.failed("gw-ref-1", "refund failed at the gateway"));

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
            verify(paymentRepository, never()).save(any());
            verify(outboxEventRepository, never()).save(any());
        }

        @Test
        void blowsUpIfThePaymentRowIsMissing() {
            when(paymentRepository.findById(100)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.applyRefundResult(100, RefundResult.succeeded("gw-refund-1")))
                    .isInstanceOf(IllegalStateException.class);
            verify(outboxEventRepository, never()).save(any());
        }
    }
}
