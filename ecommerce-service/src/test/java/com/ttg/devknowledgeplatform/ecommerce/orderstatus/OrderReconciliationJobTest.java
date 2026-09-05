package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.ecommerce.config.OrderJobProperties;
import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.enums.PaymentFailureCategory;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentCancellationResult;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentGatewayPort;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentOutcome;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentResult;
import com.ttg.devknowledgeplatform.ecommerce.repository.OrderRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
    @Mock
    private PaymentCancellationService paymentCancellationService;

    private OrderReconciliationJob job;

    @BeforeEach
    void setUp() {
        // OrderJobProperties is a record (implicitly final) — Mockito's mock maker here can't
        // mock/spy a final class, and there's no real reason to: it's a plain, cheap value object,
        // so a real instance (not a mock) is simplest. A short 30-minute abandonmentTimeout keeps
        // the abandonment tests below readable without needing hour-scale Instants.
        OrderJobProperties orderJobProperties = new OrderJobProperties(null,
                new OrderJobProperties.Reconciliation(Duration.ofMinutes(2), Duration.ofMinutes(30)));
        job = new OrderReconciliationJob(orderRepository, paymentGatewayPort, paymentHandoffService,
                paymentCancellationService, orderJobProperties);
    }

    private static Order stuckOrder(Integer id) {
        Order order = new Order();
        order.setId(id);
        order.setStatus(OrderStatus.PAYMENT_PROCESSING);
        order.setIdempotencyKey(String.valueOf(id));
        return order;
    }

    @Test
    void reconcilesEveryStuckOrderFromThePollBatch() {
        when(orderRepository.findIdsByStatusAndPaymentProcessingStartedAtBefore(
                eq(OrderStatus.PAYMENT_PROCESSING), any(), any(Pageable.class)))
                .thenReturn(List.of(1, 2));
        Order order1 = stuckOrder(1);
        Order order2 = stuckOrder(2);
        when(orderRepository.findById(1)).thenReturn(Optional.of(order1));
        when(orderRepository.findById(2)).thenReturn(Optional.of(order2));
        PaymentResult result1 = PaymentResult.succeeded("gw-1");
        PaymentResult result2 = PaymentResult.declined("gw-2", null, null);
        when(paymentGatewayPort.checkStatus("1")).thenReturn(result1);
        when(paymentGatewayPort.checkStatus("2")).thenReturn(result2);

        job.reconcileStuckPayments();

        verify(paymentHandoffService).resolvePayment(1, result1);
        verify(paymentHandoffService).resolvePayment(2, result2);
    }

    @Test
    void anOrderThatVanishedBeforeLookupIsASafeNoOp() {
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
        when(orderRepository.findIdsByStatusAndPaymentProcessingStartedAtBefore(
                eq(OrderStatus.PAYMENT_PROCESSING), any(), any(Pageable.class)))
                .thenReturn(List.of(1, 2));
        Order order1 = stuckOrder(1);
        Order order2 = stuckOrder(2);
        when(orderRepository.findById(1)).thenReturn(Optional.of(order1));
        when(orderRepository.findById(2)).thenReturn(Optional.of(order2));
        PaymentResult result2 = PaymentResult.succeeded("gw-2");
        when(paymentGatewayPort.checkStatus("1")).thenThrow(new RuntimeException("gateway timeout"));
        when(paymentGatewayPort.checkStatus("2")).thenReturn(result2);

        job.reconcileStuckPayments();

        verify(paymentHandoffService, never()).resolvePayment(eq(1), any());
        verify(paymentHandoffService).resolvePayment(2, result2);
    }

    @Test
    void finalizesAsASyntheticDeclineWhenTheChargeNeverReachedTheGatewayAtAll() {
        // Regression coverage for a real gap: checkStatus returns PENDING with a null
        // gatewayReference only when charge() itself never reached Stripe (e.g. crashed before the
        // create call returned) — nothing to ever retrieve, so this must not poll forever.
        when(orderRepository.findIdsByStatusAndPaymentProcessingStartedAtBefore(
                eq(OrderStatus.PAYMENT_PROCESSING), any(), any(Pageable.class)))
                .thenReturn(List.of(1));
        Order order = stuckOrder(1);
        when(orderRepository.findById(1)).thenReturn(Optional.of(order));
        when(paymentGatewayPort.checkStatus("1")).thenReturn(PaymentResult.pending(null, null));

        job.reconcileStuckPayments();

        ArgumentCaptor<PaymentResult> resultCaptor = ArgumentCaptor.forClass(PaymentResult.class);
        verify(paymentHandoffService).resolvePayment(eq(1), resultCaptor.capture());
        PaymentResult result = resultCaptor.getValue();
        assertThat(result.outcome()).isEqualTo(PaymentOutcome.DECLINED);
        assertThat(result.gatewayReference()).isNull();
        assertThat(result.failureCategory()).isEqualTo(PaymentFailureCategory.GATEWAY_ERROR);
    }

    @Test
    void leavesAGenuinelyStillProcessingOrderPendingWhenARealGatewayReferenceExists() {
        // Must not conflate this with the null-gatewayReference case above — a real, still-open
        // PaymentIntent (Option A's unconfirmed-intent window) always carries a real reference, and
        // must keep being polled, not finalized.
        when(orderRepository.findIdsByStatusAndPaymentProcessingStartedAtBefore(
                eq(OrderStatus.PAYMENT_PROCESSING), any(), any(Pageable.class)))
                .thenReturn(List.of(1));
        Order order = stuckOrder(1);
        when(orderRepository.findById(1)).thenReturn(Optional.of(order));
        PaymentResult stillPending = PaymentResult.pending("pi_1", "pi_1_secret");
        when(paymentGatewayPort.checkStatus("1")).thenReturn(stillPending);

        job.reconcileStuckPayments();

        verify(paymentHandoffService).resolvePayment(1, stillPending);
    }

    @Test
    void leavesAStillProcessingOrderPendingWhenItHasNotYetCrossedTheAbandonmentWindow() {
        // Same real-gatewayReference shape as above, but explicit about the boundary this time:
        // stuck for 10 minutes (past the 2-minute grace period this job already polls on) but well
        // short of the 30-minute abandonmentTimeout this test's own setUp configures — must still
        // just keep polling, not cancel anything at the gateway.
        when(orderRepository.findIdsByStatusAndPaymentProcessingStartedAtBefore(
                eq(OrderStatus.PAYMENT_PROCESSING), any(), any(Pageable.class)))
                .thenReturn(List.of(1));
        Order order = stuckOrder(1);
        order.setPaymentProcessingStartedAt(Instant.now().minus(Duration.ofMinutes(10)));
        when(orderRepository.findById(1)).thenReturn(Optional.of(order));
        PaymentResult stillPending = PaymentResult.pending("pi_1", "pi_1_secret");
        when(paymentGatewayPort.checkStatus("1")).thenReturn(stillPending);

        job.reconcileStuckPayments();

        verify(paymentHandoffService).resolvePayment(1, stillPending);
        verify(paymentGatewayPort, never()).cancelUnconfirmed(anyString());
        verify(paymentCancellationService, never()).applyAbandonmentExpiry(any(), any());
    }

    @Test
    void activelyCancelsAndExpiresAStillOpenPaymentIntentPastTheAbandonmentWindow() {
        when(orderRepository.findIdsByStatusAndPaymentProcessingStartedAtBefore(
                eq(OrderStatus.PAYMENT_PROCESSING), any(), any(Pageable.class)))
                .thenReturn(List.of(1));
        Order order = stuckOrder(1);
        order.setPaymentProcessingStartedAt(Instant.now().minus(Duration.ofMinutes(45)));
        when(orderRepository.findById(1)).thenReturn(Optional.of(order));
        PaymentResult stillPending = PaymentResult.pending("pi_1", "pi_1_secret");
        when(paymentGatewayPort.checkStatus("1")).thenReturn(stillPending);
        PaymentCancellationResult cancellation = PaymentCancellationResult.cancelled();
        when(paymentGatewayPort.cancelUnconfirmed("pi_1")).thenReturn(cancellation);

        job.reconcileStuckPayments();

        verify(paymentGatewayPort).cancelUnconfirmed("pi_1");
        verify(paymentCancellationService).applyAbandonmentExpiry(1, cancellation);
        verify(paymentHandoffService, never()).resolvePayment(any(), any());
    }

    @Test
    void aPaymentIntentThatResolvedAtTheGatewayJustBeforeTheAbandonmentCancelStillReconcilesCorrectly() {
        // The job's own live-check-first safety property: it delegates the ALREADY_RESOLVED race
        // entirely to PaymentCancellationService#applyAbandonmentExpiry (see that class's own
        // test suite for the actual resolvePayment-delegation assertion) — this test only confirms
        // the job calls that method rather than trying to resolve the race itself.
        when(orderRepository.findIdsByStatusAndPaymentProcessingStartedAtBefore(
                eq(OrderStatus.PAYMENT_PROCESSING), any(), any(Pageable.class)))
                .thenReturn(List.of(1));
        Order order = stuckOrder(1);
        order.setPaymentProcessingStartedAt(Instant.now().minus(Duration.ofMinutes(45)));
        when(orderRepository.findById(1)).thenReturn(Optional.of(order));
        PaymentResult stillPending = PaymentResult.pending("pi_1", "pi_1_secret");
        when(paymentGatewayPort.checkStatus("1")).thenReturn(stillPending);
        PaymentCancellationResult alreadyResolved =
                PaymentCancellationResult.alreadyResolved(PaymentResult.succeeded("pi_1"));
        when(paymentGatewayPort.cancelUnconfirmed("pi_1")).thenReturn(alreadyResolved);

        job.reconcileStuckPayments();

        verify(paymentCancellationService).applyAbandonmentExpiry(1, alreadyResolved);
        verify(paymentHandoffService, never()).resolvePayment(any(), any());
    }
}
