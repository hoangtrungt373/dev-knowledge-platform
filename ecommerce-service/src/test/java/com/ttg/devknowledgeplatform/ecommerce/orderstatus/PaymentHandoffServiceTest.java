package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.entity.OutboxEvent;
import com.ttg.devknowledgeplatform.ecommerce.entity.Payment;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.enums.OutboxAggregateType;
import com.ttg.devknowledgeplatform.ecommerce.enums.PaymentFailureCategory;
import com.ttg.devknowledgeplatform.ecommerce.enums.PaymentStatus;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentResult;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PaymentHandoffService} — US-3.3's two durable <b>charge</b> steps, in
 * isolation from {@link OrderStatusHandlerRegistry}'s real transition logic (mocked here), plus
 * Epic 4 Phase 3's {@code Payment} row bookkeeping alongside them. See
 * {@link PaymentCancellationServiceTest} for the cancellation/refund lifecycle this class used to
 * also own, before the God-class split documented in {@link PaymentHandoffService}'s own Javadoc.
 */
@ExtendWith(MockitoExtension.class)
class PaymentHandoffServiceTest {

    private static final String OWNER_UUID = "owner-uuid-1";

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private OrderStatusHandlerRegistry orderStatusHandlerRegistry;

    @InjectMocks
    private PaymentHandoffService service;

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
    class StartPaymentProcessing {

        @Test
        void dispatchesToTheRegistrySavesAndWritesAPendingPaymentRow() {
            Order order = orderOwnedBy(OWNER_UUID);
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);

            Order result = service.startPaymentProcessing(1, OWNER_UUID);

            verify(orderStatusHandlerRegistry).startPaymentProcessing(order);
            assertThat(result).isSameAs(order);
            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(captor.capture());
            Payment payment = captor.getValue();
            assertThat(payment.getOrder()).isSameAs(order);
            assertThat(payment.getAmount()).isEqualByComparingTo("25.00");
            assertThat(payment.getIdempotencyKey()).isEqualTo("1");
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @Test
        void rejectsAsNotFoundWhenTheOrderBelongsToSomeoneElse() {
            Order order = orderOwnedBy(OWNER_UUID);
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service.startPaymentProcessing(1, "someone-else-uuid"))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.ORDER_NOT_FOUND);
            verify(orderStatusHandlerRegistry, never()).startPaymentProcessing(order);
            verify(paymentRepository, never()).save(any());
        }

        @Test
        void isReentrantWhenTheOrderIsAlreadyPaymentProcessing() {
            // Option A (Stripe Elements): the shopper can call pay() again before ever confirming
            // the first PaymentIntent client-side — this must hand the same order back rather than
            // rejecting the transition or writing a second Payment row.
            Order order = orderOwnedBy(OWNER_UUID);
            order.setStatus(OrderStatus.PAYMENT_PROCESSING);
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));

            Order result = service.startPaymentProcessing(1, OWNER_UUID);

            assertThat(result).isSameAs(order);
            verify(orderStatusHandlerRegistry, never()).startPaymentProcessing(any());
            verify(orderRepository, never()).save(any());
            verify(paymentRepository, never()).save(any());
        }
    }

    @Nested
    class ResolvePayment {

        @Test
        void succeededDispatchesToConfirmPaymentMarksThePaymentRowSucceededAndPublishesTheOutboxEvent() {
            Order order = orderOwnedBy(OWNER_UUID);
            Payment payment = new Payment();
            payment.setId(100);
            payment.setAmount(new BigDecimal("25.00"));
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);
            when(paymentRepository.findByOrderId(1)).thenReturn(Optional.of(payment));

            service.resolvePayment(1, PaymentResult.succeeded("gw-ref-1"));

            verify(orderStatusHandlerRegistry).confirmPayment(order);
            verify(orderStatusHandlerRegistry, never()).failPayment(order);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
            assertThat(payment.getGatewayReference()).isEqualTo("gw-ref-1");
            verify(paymentRepository).save(payment);

            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(captor.capture());
            OutboxEvent event = captor.getValue();
            assertThat(event.getEventType()).isEqualTo(PaymentSucceededOutboxEventHandler.EVENT_TYPE);
            assertThat(event.getAggregateType()).isEqualTo(OutboxAggregateType.PAYMENT);
            assertThat(event.getAggregateId()).isEqualTo(100);
            assertThat(event.getPayload())
                    .containsEntry("orderId", 1)
                    .containsEntry("amount", "25.00")
                    .containsEntry("gatewayReference", "gw-ref-1");
        }

        @Test
        void declinedDispatchesToFailPaymentMarksThePaymentRowDeclinedAndPublishesTheOutboxEvent() {
            Order order = orderOwnedBy(OWNER_UUID);
            Payment payment = new Payment();
            payment.setId(100);
            payment.setAmount(new BigDecimal("25.00"));
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);
            when(paymentRepository.findByOrderId(1)).thenReturn(Optional.of(payment));

            service.resolvePayment(1, PaymentResult.declined(
                    "gw-ref-1", PaymentFailureCategory.INSUFFICIENT_FUNDS, "raw gateway message"));

            verify(orderStatusHandlerRegistry).failPayment(order);
            verify(orderStatusHandlerRegistry, never()).confirmPayment(order);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DECLINED);
            assertThat(payment.getFailureCategory()).isEqualTo(PaymentFailureCategory.INSUFFICIENT_FUNDS);
            assertThat(payment.getGatewayFailureMessage()).isEqualTo("raw gateway message");
            verify(paymentRepository).save(payment);

            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(captor.capture());
            OutboxEvent event = captor.getValue();
            assertThat(event.getEventType()).isEqualTo(PaymentFailedOutboxEventHandler.EVENT_TYPE);
            assertThat(event.getAggregateType()).isEqualTo(OutboxAggregateType.PAYMENT);
            assertThat(event.getAggregateId()).isEqualTo(100);
            assertThat(event.getPayload())
                    .containsEntry("orderId", 1)
                    .containsEntry("amount", "25.00")
                    .containsEntry("failureCategory", "INSUFFICIENT_FUNDS")
                    .containsEntry("gatewayFailureMessage", "raw gateway message");
        }

        @Test
        void pendingLeavesTheOrderStatusUntouchedButStillRecordsTheGatewayReferenceAndPublishesNoEvent() {
            // Regression coverage for a real bug: an earlier revision of applyResultToPayment
            // skipped the Payment row entirely on PENDING, so Payment.gatewayReference was never
            // set on Option A's very first (routinely-PENDING) charge() call — breaking both the
            // Stripe webhook's own correlation lookup and the reconciliation job's checkStatus
            // retry, which both key off that same column. See PaymentHandoffService.resolvePayment's
            // own updated Javadoc for the full incident writeup.
            Order order = orderOwnedBy(OWNER_UUID);
            order.setStatus(OrderStatus.PAYMENT_PROCESSING);
            Payment payment = new Payment();
            payment.setId(100);
            payment.setAmount(new BigDecimal("25.00"));
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);
            when(paymentRepository.findByOrderId(1)).thenReturn(Optional.of(payment));

            service.resolvePayment(1, PaymentResult.pending("gw-ref-1", "secret_1"));

            verify(orderStatusHandlerRegistry, never()).confirmPayment(order);
            verify(orderStatusHandlerRegistry, never()).failPayment(order);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PROCESSING);
            assertThat(payment.getGatewayReference()).isEqualTo("gw-ref-1");
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            verify(paymentRepository).save(payment);
            verify(outboxEventRepository, never()).save(any());
        }

        @Test
        void pendingWithNoGatewayReferenceYetLeavesThePaymentRowsExistingReferenceUntouched() {
            // The reconciliation job's own checkStatus call returns PaymentResult.pending(null,
            // null) when Payment.gatewayReference is itself still null (nothing to retrieve yet) —
            // must not overwrite a real reference with null were one already recorded.
            Order order = orderOwnedBy(OWNER_UUID);
            order.setStatus(OrderStatus.PAYMENT_PROCESSING);
            Payment payment = new Payment();
            payment.setId(100);
            payment.setAmount(new BigDecimal("25.00"));
            payment.setGatewayReference("gw-ref-1");
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);
            when(paymentRepository.findByOrderId(1)).thenReturn(Optional.of(payment));

            service.resolvePayment(1, PaymentResult.pending(null, null));

            assertThat(payment.getGatewayReference()).isEqualTo("gw-ref-1");
            verify(paymentRepository).save(payment);
        }

        @Test
        void attemptFailedLeavesTheOrderAtPaymentProcessingButRecordsTheDeclineReasonAndPublishesNoEvent() {
            // Bug fix regression coverage: payment_intent.payment_failed (StripeWebhookService's
            // own attemptFailed result) must not finalize the order the way a bare DECLINED does —
            // the shopper can still retry with a different card against the same still-open
            // PaymentIntent. See PaymentResult#attemptFailed's own Javadoc for the full incident.
            Order order = orderOwnedBy(OWNER_UUID);
            order.setStatus(OrderStatus.PAYMENT_PROCESSING);
            Payment payment = new Payment();
            payment.setId(100);
            payment.setStatus(PaymentStatus.PENDING);
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);
            when(paymentRepository.findByOrderId(1)).thenReturn(Optional.of(payment));

            service.resolvePayment(1, PaymentResult.attemptFailed(
                    "gw-ref-1", PaymentFailureCategory.CARD_DECLINED, "Your card was declined."));

            verify(orderStatusHandlerRegistry, never()).confirmPayment(order);
            verify(orderStatusHandlerRegistry, never()).failPayment(order);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PROCESSING);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.getFailureCategory()).isEqualTo(PaymentFailureCategory.CARD_DECLINED);
            assertThat(payment.getGatewayFailureMessage()).isEqualTo("Your card was declined.");
            verify(paymentRepository).save(payment);
            verify(outboxEventRepository, never()).save(any());
        }

        @Test
        void succeededAfterAnEarlierAttemptFailedClearsTheStaleDeclineReason() {
            // A shopper who mistyped a card, saw the failure, then retried with a working one on
            // the same PaymentIntent must not still see a stale "card declined" reason once the
            // order actually succeeds.
            Order order = orderOwnedBy(OWNER_UUID);
            order.setStatus(OrderStatus.PAYMENT_PROCESSING);
            Payment payment = new Payment();
            payment.setId(100);
            payment.setAmount(new BigDecimal("25.00"));
            payment.setStatus(PaymentStatus.PENDING);
            payment.setFailureCategory(PaymentFailureCategory.CARD_DECLINED);
            payment.setGatewayFailureMessage("Your card was declined.");
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);
            when(paymentRepository.findByOrderId(1)).thenReturn(Optional.of(payment));

            service.resolvePayment(1, PaymentResult.succeeded("gw-ref-1"));

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
            assertThat(payment.getFailureCategory()).isNull();
            assertThat(payment.getGatewayFailureMessage()).isNull();
        }

        @Test
        void anAttemptFailedThatArrivesAfterTheOrderAlreadySucceededDoesNotReintroduceAStaleDeclineReason() {
            // Edge-case fix: Stripe explicitly does not guarantee webhook delivery order — the
            // payment_intent.payment_failed event for an EARLIER attempt (this attemptFailed
            // result) can be delivered after payment_intent.succeeded for a LATER attempt against
            // the same PaymentIntent already resolved this row SUCCEEDED. Must not let the stale,
            // out-of-order event reintroduce a "your card was declined" reason onto a paid order.
            Order order = orderOwnedBy(OWNER_UUID);
            order.setStatus(OrderStatus.CONFIRMED);
            Payment payment = new Payment();
            payment.setId(100);
            payment.setStatus(PaymentStatus.SUCCEEDED);
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);
            when(paymentRepository.findByOrderId(1)).thenReturn(Optional.of(payment));

            service.resolvePayment(1, PaymentResult.attemptFailed(
                    "gw-ref-1", PaymentFailureCategory.CARD_DECLINED, "Your card was declined."));

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
            assertThat(payment.getFailureCategory()).isNull();
            assertThat(payment.getGatewayFailureMessage()).isNull();
            verify(paymentRepository).save(payment);
        }

        @Test
        void rejectsAsNotFoundWhenTheOrderNoLongerExists() {
            when(orderRepository.findById(1)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.resolvePayment(1, PaymentResult.succeeded("gw-ref-1")))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.ORDER_NOT_FOUND);
        }

        @Test
        void blowsUpIfTheStartedPaymentRowIsSomehowMissing() {
            Order order = orderOwnedBy(OWNER_UUID);
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);
            when(paymentRepository.findByOrderId(1)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.resolvePayment(1, PaymentResult.succeeded("gw-ref-1")))
                    .isInstanceOf(IllegalStateException.class);
            verify(outboxEventRepository, never()).save(any());
        }
    }
}
