package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.ecommerce.config.OrderJobProperties;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.repository.OrderRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrderReconciliationJob} — now just the poll-batch query plus the
 * per-id delegation to {@link PaymentReconciliationService#reconcileNow} (mocked here); the actual
 * per-order reconciliation logic moved to {@link PaymentReconciliationServiceTest}, which is where
 * it's actually exercised. Mirrors {@code OrderReservationExpiryProcessorTest}'s shape for the
 * "poll batch, delegate per id" pattern.
 */
@ExtendWith(MockitoExtension.class)
class OrderReconciliationJobTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentReconciliationService paymentReconciliationService;

    private OrderReconciliationJob job;

    @BeforeEach
    void setUp() {
        // OrderJobProperties is a record (implicitly final) — Mockito's mock maker here can't
        // mock/spy a final class, and there's no real reason to: it's a plain, cheap value object,
        // so a real instance (not a mock) is simplest.
        OrderJobProperties orderJobProperties = new OrderJobProperties(null,
                new OrderJobProperties.Reconciliation(Duration.ofMinutes(2), Duration.ofMinutes(30)));
        job = new OrderReconciliationJob(orderRepository, paymentReconciliationService, orderJobProperties);
    }

    @Test
    void reconcilesEveryStuckOrderFromThePollBatchByDelegatingToPaymentReconciliationService() {
        when(orderRepository.findIdsByStatusAndPaymentProcessingStartedAtBefore(
                eq(OrderStatus.PAYMENT_PROCESSING), any(), any(Pageable.class)))
                .thenReturn(List.of(1, 2));

        job.reconcileStuckPayments();

        verify(paymentReconciliationService).reconcileNow(1);
        verify(paymentReconciliationService).reconcileNow(2);
    }

    @Test
    void aFailureReconcilingOneOrderDoesNotStopTheRestOfTheBatch() {
        when(orderRepository.findIdsByStatusAndPaymentProcessingStartedAtBefore(
                eq(OrderStatus.PAYMENT_PROCESSING), any(), any(Pageable.class)))
                .thenReturn(List.of(1, 2));
        when(paymentReconciliationService.reconcileNow(1)).thenThrow(new RuntimeException("gateway timeout"));

        job.reconcileStuckPayments();

        verify(paymentReconciliationService).reconcileNow(1);
        verify(paymentReconciliationService).reconcileNow(2);
    }
}
