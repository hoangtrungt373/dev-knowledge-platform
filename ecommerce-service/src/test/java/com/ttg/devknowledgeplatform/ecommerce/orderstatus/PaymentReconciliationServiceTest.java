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

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PaymentReconciliationService} — US-3.4's "ask the gateway for the ground
 * truth on one order and act on it" logic, extracted out of {@code OrderReconciliationJob} (see
 * that class's own Javadoc) once the auto-expire follow-up's on-demand
 * {@code service.impl.OrderServiceImpl#reconcilePayment} endpoint needed the identical logic.
 * Mirrors the shape {@code OrderReconciliationJobTest} used to have before this extraction.
 */
@ExtendWith(MockitoExtension.class)
class PaymentReconciliationServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentGatewayPort paymentGatewayPort;
    @Mock
    private PaymentHandoffService paymentHandoffService;
    @Mock
    private PaymentCancellationService paymentCancellationService;

    private PaymentReconciliationService service;

    @BeforeEach
    void setUp() {
        // OrderJobProperties is a record (implicitly final) — same "real instance, not a mock"
        // reasoning as OrderReconciliationJobTest's own setUp. A short 30-minute abandonmentTimeout
        // keeps the abandonment tests below readable without needing hour-scale Instants.
        OrderJobProperties orderJobProperties = new OrderJobProperties(null,
                new OrderJobProperties.Reconciliation(Duration.ofMinutes(2), Duration.ofMinutes(30)));
        service = new PaymentReconciliationService(orderRepository, paymentGatewayPort, paymentHandoffService,
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
    void anOrderThatVanishedBeforeLookupIsASafeNoOp() {
        when(orderRepository.findById(1)).thenReturn(Optional.empty());

        Order result = service.reconcileNow(1);

        assertThat(result).isNull();
        verify(paymentGatewayPort, never()).checkStatus(anyString());
        verify(paymentHandoffService, never()).resolvePayment(any(), any());
    }

    @Test
    void anOrderNoLongerPaymentProcessingIsASafeNoOp() {
        Order order = stuckOrder(1);
        order.setStatus(OrderStatus.CONFIRMED); // already resolved via the synchronous flow
        when(orderRepository.findById(1)).thenReturn(Optional.of(order));

        Order result = service.reconcileNow(1);

        assertThat(result).isSameAs(order);
        verify(paymentGatewayPort, never()).checkStatus(anyString());
        verify(paymentHandoffService, never()).resolvePayment(any(), any());
    }

    @Test
    void resolvesNormallyAndReturnsTheResolvedOrderWhenTheGatewayReportsADefiniteOutcome() {
        Order order = stuckOrder(1);
        when(orderRepository.findById(1)).thenReturn(Optional.of(order));
        PaymentResult result = PaymentResult.succeeded("gw-1");
        when(paymentGatewayPort.checkStatus("1")).thenReturn(result);
        Order confirmed = stuckOrder(1);
        confirmed.setStatus(OrderStatus.CONFIRMED);
        when(paymentHandoffService.resolvePayment(1, result)).thenReturn(confirmed);

        Order actual = service.reconcileNow(1);

        assertThat(actual).isSameAs(confirmed);
        verify(paymentHandoffService).resolvePayment(1, result);
    }

    @Test
    void finalizesAsASyntheticDeclineWhenTheChargeNeverReachedTheGatewayAtAll() {
        // Regression coverage for a real gap: checkStatus returns PENDING with a null
        // gatewayReference only when charge() itself never reached Stripe (e.g. crashed before the
        // create call returned) — nothing to ever retrieve, so this must not poll forever.
        Order order = stuckOrder(1);
        when(orderRepository.findById(1)).thenReturn(Optional.of(order));
        when(paymentGatewayPort.checkStatus("1")).thenReturn(PaymentResult.pending(null, null));

        service.reconcileNow(1);

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
        Order order = stuckOrder(1);
        when(orderRepository.findById(1)).thenReturn(Optional.of(order));
        PaymentResult stillPending = PaymentResult.pending("pi_1", "pi_1_secret");
        when(paymentGatewayPort.checkStatus("1")).thenReturn(stillPending);
        when(paymentHandoffService.resolvePayment(1, stillPending)).thenReturn(order);

        service.reconcileNow(1);

        verify(paymentHandoffService).resolvePayment(1, stillPending);
    }

    @Test
    void leavesAStillProcessingOrderPendingWhenItHasNotYetCrossedTheAbandonmentWindow() {
        // Same real-gatewayReference shape as above, but explicit about the boundary this time:
        // stuck for 10 minutes (past the 2-minute grace period the job itself polls on) but well
        // short of the 30-minute abandonmentTimeout this test's own setUp configures — must still
        // just keep polling, not cancel anything at the gateway.
        Order order = stuckOrder(1);
        order.setPaymentProcessingStartedAt(Instant.now().minus(Duration.ofMinutes(10)));
        when(orderRepository.findById(1)).thenReturn(Optional.of(order));
        PaymentResult stillPending = PaymentResult.pending("pi_1", "pi_1_secret");
        when(paymentGatewayPort.checkStatus("1")).thenReturn(stillPending);
        when(paymentHandoffService.resolvePayment(1, stillPending)).thenReturn(order);

        service.reconcileNow(1);

        verify(paymentHandoffService).resolvePayment(1, stillPending);
        verify(paymentGatewayPort, never()).cancelUnconfirmed(anyString());
        verify(paymentCancellationService, never()).applyAbandonmentExpiry(any(), any());
    }

    @Test
    void activelyCancelsAndExpiresAStillOpenPaymentIntentPastTheAbandonmentWindow() {
        Order order = stuckOrder(1);
        order.setPaymentProcessingStartedAt(Instant.now().minus(Duration.ofMinutes(45)));
        when(orderRepository.findById(1)).thenReturn(Optional.of(order));
        PaymentResult stillPending = PaymentResult.pending("pi_1", "pi_1_secret");
        when(paymentGatewayPort.checkStatus("1")).thenReturn(stillPending);
        PaymentCancellationResult cancellation = PaymentCancellationResult.cancelled();
        when(paymentGatewayPort.cancelUnconfirmed("pi_1")).thenReturn(cancellation);
        Order expired = stuckOrder(1);
        expired.setStatus(OrderStatus.EXPIRED);
        when(paymentCancellationService.applyAbandonmentExpiry(1, cancellation)).thenReturn(expired);

        Order result = service.reconcileNow(1);

        assertThat(result).isSameAs(expired);
        verify(paymentGatewayPort).cancelUnconfirmed("pi_1");
        verify(paymentCancellationService).applyAbandonmentExpiry(1, cancellation);
        verify(paymentHandoffService, never()).resolvePayment(any(), any());
    }

    @Test
    void aPaymentIntentThatResolvedAtTheGatewayJustBeforeTheAbandonmentCancelStillReconcilesCorrectly() {
        // The job's own live-check-first safety property: it delegates the ALREADY_RESOLVED race
        // entirely to PaymentCancellationService#applyAbandonmentExpiry (see that class's own test
        // suite for the actual resolvePayment-delegation assertion) — this test only confirms this
        // method calls that method rather than trying to resolve the race itself.
        Order order = stuckOrder(1);
        order.setPaymentProcessingStartedAt(Instant.now().minus(Duration.ofMinutes(45)));
        when(orderRepository.findById(1)).thenReturn(Optional.of(order));
        PaymentResult stillPending = PaymentResult.pending("pi_1", "pi_1_secret");
        when(paymentGatewayPort.checkStatus("1")).thenReturn(stillPending);
        PaymentCancellationResult alreadyResolved =
                PaymentCancellationResult.alreadyResolved(PaymentResult.succeeded("pi_1"));
        when(paymentGatewayPort.cancelUnconfirmed("pi_1")).thenReturn(alreadyResolved);
        Order confirmed = stuckOrder(1);
        confirmed.setStatus(OrderStatus.CONFIRMED);
        when(paymentCancellationService.applyAbandonmentExpiry(1, alreadyResolved)).thenReturn(confirmed);

        Order result = service.reconcileNow(1);

        assertThat(result).isSameAs(confirmed);
        verify(paymentCancellationService).applyAbandonmentExpiry(1, alreadyResolved);
        verify(paymentHandoffService, never()).resolvePayment(any(), any());
    }
}
