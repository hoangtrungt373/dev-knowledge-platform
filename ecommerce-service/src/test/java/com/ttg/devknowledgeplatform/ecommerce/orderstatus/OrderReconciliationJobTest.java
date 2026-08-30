package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentGatewayPort;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentOutcome;
import com.ttg.devknowledgeplatform.ecommerce.repository.OrderRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrderReconciliationJob} — US-3.4's per-order reconciliation mechanism in
 * isolation from {@link PaymentHandoffService}'s real transition logic (mocked here), mirroring
 * {@code OrderReservationExpiryProcessorTest}'s shape.
 */
@ExtendWith(MockitoExtension.class)
class OrderReconciliationJobTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentGatewayPort paymentGatewayPort;
    @Mock
    private PaymentHandoffService paymentHandoffService;

    @InjectMocks
    private OrderReconciliationJob job;

    private static Order stuckOrder(Integer id) {
        Order order = new Order();
        order.setId(id);
        order.setStatus(OrderStatus.PAYMENT_PROCESSING);
        order.setIdempotencyKey(String.valueOf(id));
        return order;
    }

    @Test
    void reconcilesEveryStuckOrderFromThePollBatch() {
        ReflectionTestUtils.setField(job, "gracePeriod", Duration.ofMinutes(2));
        when(orderRepository.findIdsByStatusAndPaymentProcessingStartedAtBefore(
                eq(OrderStatus.PAYMENT_PROCESSING), any(), any(Pageable.class)))
                .thenReturn(List.of(1, 2));
        Order order1 = stuckOrder(1);
        Order order2 = stuckOrder(2);
        when(orderRepository.findById(1)).thenReturn(Optional.of(order1));
        when(orderRepository.findById(2)).thenReturn(Optional.of(order2));
        when(paymentGatewayPort.checkStatus("1")).thenReturn(PaymentOutcome.SUCCEEDED);
        when(paymentGatewayPort.checkStatus("2")).thenReturn(PaymentOutcome.DECLINED);

        job.reconcileStuckPayments();

        verify(paymentHandoffService).resolvePayment(1, PaymentOutcome.SUCCEEDED);
        verify(paymentHandoffService).resolvePayment(2, PaymentOutcome.DECLINED);
    }

    @Test
    void anOrderThatVanishedBeforeLookupIsASafeNoOp() {
        ReflectionTestUtils.setField(job, "gracePeriod", Duration.ofMinutes(2));
        when(orderRepository.findIdsByStatusAndPaymentProcessingStartedAtBefore(
                eq(OrderStatus.PAYMENT_PROCESSING), any(), any(Pageable.class)))
                .thenReturn(List.of(1));
        when(orderRepository.findById(1)).thenReturn(Optional.empty());

        job.reconcileStuckPayments();

        verify(paymentGatewayPort, never()).checkStatus(anyString());
        verify(paymentHandoffService, never()).resolvePayment(any(), any());
    }

    @Test
    void anOrderNoLongerPaymentProcessingIsASafeNoOp() {
        ReflectionTestUtils.setField(job, "gracePeriod", Duration.ofMinutes(2));
        Order order = stuckOrder(1);
        order.setStatus(OrderStatus.CONFIRMED); // already resolved via the synchronous flow
        when(orderRepository.findIdsByStatusAndPaymentProcessingStartedAtBefore(
                eq(OrderStatus.PAYMENT_PROCESSING), any(), any(Pageable.class)))
                .thenReturn(List.of(1));
        when(orderRepository.findById(1)).thenReturn(Optional.of(order));

        job.reconcileStuckPayments();

        verify(paymentGatewayPort, never()).checkStatus(anyString());
        verify(paymentHandoffService, never()).resolvePayment(any(), any());
    }

    @Test
    void aFailureReconcilingOneOrderDoesNotStopTheRestOfTheBatch() {
        ReflectionTestUtils.setField(job, "gracePeriod", Duration.ofMinutes(2));
        when(orderRepository.findIdsByStatusAndPaymentProcessingStartedAtBefore(
                eq(OrderStatus.PAYMENT_PROCESSING), any(), any(Pageable.class)))
                .thenReturn(List.of(1, 2));
        Order order1 = stuckOrder(1);
        Order order2 = stuckOrder(2);
        when(orderRepository.findById(1)).thenReturn(Optional.of(order1));
        when(orderRepository.findById(2)).thenReturn(Optional.of(order2));
        when(paymentGatewayPort.checkStatus("1")).thenThrow(new RuntimeException("gateway timeout"));
        when(paymentGatewayPort.checkStatus("2")).thenReturn(PaymentOutcome.SUCCEEDED);

        job.reconcileStuckPayments();

        verify(paymentHandoffService, never()).resolvePayment(eq(1), any());
        verify(paymentHandoffService).resolvePayment(2, PaymentOutcome.SUCCEEDED);
    }
}
