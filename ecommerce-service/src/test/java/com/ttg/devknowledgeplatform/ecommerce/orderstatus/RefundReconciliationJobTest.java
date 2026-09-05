package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.entity.Payment;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.enums.PaymentStatus;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentGatewayPort;
import com.ttg.devknowledgeplatform.ecommerce.payment.RefundResult;
import com.ttg.devknowledgeplatform.ecommerce.repository.PaymentRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RefundReconciliationJob} — the code-quality-audit follow-up closing the
 * "queued cancel loses the race to a gateway success" money gap, in isolation from
 * {@link PaymentCancellationService}'s real refund-applying logic (mocked here), mirroring
 * {@code OrderReconciliationJobTest}'s shape.
 */
@ExtendWith(MockitoExtension.class)
class RefundReconciliationJobTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentGatewayPort paymentGatewayPort;
    @Mock
    private PaymentCancellationService paymentCancellationService;

    @InjectMocks
    private RefundReconciliationJob job;

    private static Payment succeededPayment(Integer id, Integer orderId, String gatewayReference) {
        Order order = new Order();
        order.setId(orderId);
        order.setStatus(OrderStatus.CANCELLED);
        Payment payment = new Payment();
        payment.setId(id);
        payment.setOrder(order);
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setAmount(new BigDecimal("25.00"));
        payment.setGatewayReference(gatewayReference);
        return payment;
    }

    @Test
    void reconcilesEveryMissedRefundFromThePollBatch() {
        when(paymentRepository.findIdsByStatusAndOrderStatus(
                eq(PaymentStatus.SUCCEEDED), eq(OrderStatus.CANCELLED), any(Pageable.class)))
                .thenReturn(List.of(1, 2));
        Payment payment1 = succeededPayment(1, 100, "gw-ref-1");
        Payment payment2 = succeededPayment(2, 200, "gw-ref-2");
        when(paymentRepository.findById(1)).thenReturn(Optional.of(payment1));
        when(paymentRepository.findById(2)).thenReturn(Optional.of(payment2));
        RefundResult result1 = RefundResult.succeeded("gw-refund-1");
        RefundResult result2 = RefundResult.succeeded("gw-refund-2");
        when(paymentGatewayPort.refund("gw-ref-1", new BigDecimal("25.00"))).thenReturn(result1);
        when(paymentGatewayPort.refund("gw-ref-2", new BigDecimal("25.00"))).thenReturn(result2);

        job.reconcileMissedRefunds();

        verify(paymentCancellationService).applyRefundResult(1, result1);
        verify(paymentCancellationService).applyRefundResult(2, result2);
    }

    @Test
    void aPaymentThatVanishedBeforeLookupIsASafeNoOp() {
        when(paymentRepository.findIdsByStatusAndOrderStatus(
                eq(PaymentStatus.SUCCEEDED), eq(OrderStatus.CANCELLED), any(Pageable.class)))
                .thenReturn(List.of(1));
        when(paymentRepository.findById(1)).thenReturn(Optional.empty());

        job.reconcileMissedRefunds();

        verify(paymentGatewayPort, never()).refund(anyString(), any());
        verify(paymentCancellationService, never()).applyRefundResult(any(), any());
    }

    @Test
    void aPaymentAlreadyResolvedByAConcurrentCancelIsASafeNoOp() {
        // A concurrent OrderServiceImpl#cancel refund (or an earlier poll tick) already turned this
        // row REFUNDED between the poll query and this lookup — must not double-refund it.
        Payment payment = succeededPayment(1, 100, "gw-ref-1");
        payment.setStatus(PaymentStatus.REFUNDED);
        when(paymentRepository.findIdsByStatusAndOrderStatus(
                eq(PaymentStatus.SUCCEEDED), eq(OrderStatus.CANCELLED), any(Pageable.class)))
                .thenReturn(List.of(1));
        when(paymentRepository.findById(1)).thenReturn(Optional.of(payment));

        job.reconcileMissedRefunds();

        verify(paymentGatewayPort, never()).refund(anyString(), any());
        verify(paymentCancellationService, never()).applyRefundResult(any(), any());
    }

    @Test
    void aFailureReconcilingOnePaymentDoesNotStopTheRestOfTheBatch() {
        when(paymentRepository.findIdsByStatusAndOrderStatus(
                eq(PaymentStatus.SUCCEEDED), eq(OrderStatus.CANCELLED), any(Pageable.class)))
                .thenReturn(List.of(1, 2));
        Payment payment1 = succeededPayment(1, 100, "gw-ref-1");
        Payment payment2 = succeededPayment(2, 200, "gw-ref-2");
        when(paymentRepository.findById(1)).thenReturn(Optional.of(payment1));
        when(paymentRepository.findById(2)).thenReturn(Optional.of(payment2));
        RefundResult result2 = RefundResult.succeeded("gw-refund-2");
        when(paymentGatewayPort.refund("gw-ref-1", new BigDecimal("25.00")))
                .thenThrow(new RuntimeException("gateway timeout"));
        when(paymentGatewayPort.refund("gw-ref-2", new BigDecimal("25.00"))).thenReturn(result2);

        job.reconcileMissedRefunds();

        verify(paymentCancellationService, never()).applyRefundResult(eq(1), any());
        verify(paymentCancellationService).applyRefundResult(2, result2);
    }
}
