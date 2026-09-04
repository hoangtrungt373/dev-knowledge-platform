package com.ttg.devknowledgeplatform.ecommerce.payment;

import com.ttg.devknowledgeplatform.ecommerce.enums.PaymentFailureCategory;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MockPaymentGateway} — pins down its deterministic magic-amount decline
 * (US-4.1), the one piece of real logic this placeholder strategy has (everything else always
 * succeeds unconditionally, same as Epic 3's original {@code NoOpPaymentGatewayPort}).
 */
class MockPaymentGatewayTest {

    private final MockPaymentGateway gateway = new MockPaymentGateway();

    @Test
    void chargeSucceedsForAnyAmountOtherThanTheMagicDeclineSentinel() {
        PaymentResult result = gateway.charge("order-1", new BigDecimal("25.00"));

        assertThat(result.outcome()).isEqualTo(PaymentOutcome.SUCCEEDED);
        assertThat(result.gatewayReference()).isEqualTo("mock-order-1");
        assertThat(result.failureCategory()).isNull();
    }

    @Test
    void chargeDeclinesExactlyTheMagicAmount() {
        PaymentResult result = gateway.charge("order-2", MockPaymentGateway.MAGIC_DECLINE_AMOUNT);

        assertThat(result.outcome()).isEqualTo(PaymentOutcome.DECLINED);
        assertThat(result.failureCategory()).isEqualTo(PaymentFailureCategory.CARD_DECLINED);
        assertThat(result.gatewayFailureMessage()).isNotBlank();
    }

    @Test
    void checkStatusAlwaysSucceeds() {
        assertThat(gateway.checkStatus("order-1").outcome()).isEqualTo(PaymentOutcome.SUCCEEDED);
    }

    @Test
    void refundAlwaysSucceeds() {
        RefundResult result = gateway.refund("mock-order-1", new BigDecimal("25.00"));

        assertThat(result.outcome()).isEqualTo(RefundOutcome.SUCCEEDED);
        assertThat(result.gatewayReference()).isEqualTo("mock-refund-mock-order-1");
    }

    @Test
    void cancelUnconfirmedAlwaysReportsCancelled() {
        PaymentCancellationResult result = gateway.cancelUnconfirmed("mock-order-1");

        assertThat(result.outcome()).isEqualTo(CancellationOutcome.CANCELLED);
        assertThat(result.resolvedResult()).isNull();
    }
}
