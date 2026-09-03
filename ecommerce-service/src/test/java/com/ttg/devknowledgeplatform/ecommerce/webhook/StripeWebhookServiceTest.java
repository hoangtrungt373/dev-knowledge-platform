package com.ttg.devknowledgeplatform.ecommerce.webhook;

import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.entity.Payment;
import com.ttg.devknowledgeplatform.ecommerce.entity.StripeWebhookEvent;
import com.ttg.devknowledgeplatform.ecommerce.enums.PaymentFailureCategory;
import com.ttg.devknowledgeplatform.ecommerce.orderstatus.PaymentHandoffService;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentResult;
import com.ttg.devknowledgeplatform.ecommerce.repository.PaymentRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.StripeWebhookEventRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StripeWebhookService#applyPaymentIntentEvent} — US-4.5's dedup/correlate/
 * resolve logic, in isolation from the real Stripe SDK types {@link StripeWebhookService
 * #handleWebhook} touches (see that class's own Javadoc for why the split exists and why
 * {@code handleWebhook} itself has no dedicated test, mirroring {@code payment.StripePaymentGateway}'s
 * own precedent).
 */
@ExtendWith(MockitoExtension.class)
class StripeWebhookServiceTest {

    @Mock
    private StripeWebhookEventRepository stripeWebhookEventRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentHandoffService paymentHandoffService;

    @InjectMocks
    private StripeWebhookService service;

    private static Payment paymentForOrder(Integer orderId) {
        Order order = new Order();
        order.setId(orderId);
        Payment payment = new Payment();
        payment.setOrder(order);
        return payment;
    }

    @Test
    void anAlreadyProcessedEventIsASafeNoOp() {
        when(stripeWebhookEventRepository.existsByStripeEventId("evt_1")).thenReturn(true);

        service.applyPaymentIntentEvent("evt_1", "payment_intent.succeeded", "pi_1", null, null);

        verify(paymentRepository, never()).findByGatewayReference(any());
        verify(paymentHandoffService, never()).resolvePayment(any(), any());
        verify(stripeWebhookEventRepository, never()).save(any());
    }

    @Test
    void noMatchingPaymentRowRecordsTheEventAsProcessedButNeverResolves() {
        when(stripeWebhookEventRepository.existsByStripeEventId("evt_1")).thenReturn(false);
        when(paymentRepository.findByGatewayReference("pi_1")).thenReturn(Optional.empty());

        service.applyPaymentIntentEvent("evt_1", "payment_intent.succeeded", "pi_1", null, null);

        verify(paymentHandoffService, never()).resolvePayment(any(), any());
        ArgumentCaptor<StripeWebhookEvent> captor = ArgumentCaptor.forClass(StripeWebhookEvent.class);
        verify(stripeWebhookEventRepository).save(captor.capture());
        assertThat(captor.getValue().getStripeEventId()).isEqualTo("evt_1");
        assertThat(captor.getValue().getEventType()).isEqualTo("payment_intent.succeeded");
    }

    @Test
    void succeededResolvesTheOrderAndThenRecordsTheEventAsProcessed() {
        when(stripeWebhookEventRepository.existsByStripeEventId("evt_1")).thenReturn(false);
        Payment payment = paymentForOrder(42);
        when(paymentRepository.findByGatewayReference("pi_1")).thenReturn(Optional.of(payment));

        service.applyPaymentIntentEvent("evt_1", "payment_intent.succeeded", "pi_1", null, null);

        ArgumentCaptor<PaymentResult> resultCaptor = ArgumentCaptor.forClass(PaymentResult.class);
        verify(paymentHandoffService).resolvePayment(eq(42), resultCaptor.capture());
        assertThat(resultCaptor.getValue().outcome().name()).isEqualTo("SUCCEEDED");
        assertThat(resultCaptor.getValue().gatewayReference()).isEqualTo("pi_1");

        InOrder order = inOrder(paymentHandoffService, stripeWebhookEventRepository);
        order.verify(paymentHandoffService).resolvePayment(eq(42), any());
        order.verify(stripeWebhookEventRepository).save(any());
    }

    @Test
    void failedResolvesTheOrderWithTheDeclineDetailAndRecordsTheEventAsProcessed() {
        when(stripeWebhookEventRepository.existsByStripeEventId("evt_2")).thenReturn(false);
        Payment payment = paymentForOrder(43);
        when(paymentRepository.findByGatewayReference("pi_2")).thenReturn(Optional.of(payment));

        service.applyPaymentIntentEvent("evt_2", "payment_intent.payment_failed", "pi_2",
                PaymentFailureCategory.INSUFFICIENT_FUNDS, "Your card has insufficient funds.");

        ArgumentCaptor<PaymentResult> resultCaptor = ArgumentCaptor.forClass(PaymentResult.class);
        verify(paymentHandoffService).resolvePayment(eq(43), resultCaptor.capture());
        PaymentResult result = resultCaptor.getValue();
        assertThat(result.outcome().name()).isEqualTo("DECLINED");
        assertThat(result.gatewayReference()).isEqualTo("pi_2");
        assertThat(result.failureCategory()).isEqualTo(PaymentFailureCategory.INSUFFICIENT_FUNDS);
        assertThat(result.gatewayFailureMessage()).isEqualTo("Your card has insufficient funds.");

        ArgumentCaptor<StripeWebhookEvent> eventCaptor = ArgumentCaptor.forClass(StripeWebhookEvent.class);
        verify(stripeWebhookEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("payment_intent.payment_failed");
    }
}
