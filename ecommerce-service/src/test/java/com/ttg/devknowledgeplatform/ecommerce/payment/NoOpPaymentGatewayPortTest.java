package com.ttg.devknowledgeplatform.ecommerce.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link NoOpPaymentGatewayPort} — pinning down its "always approves instantly"
 * placeholder behavior, so a future real adapter's own test suite has an explicit contrast point.
 */
class NoOpPaymentGatewayPortTest {

    private final NoOpPaymentGatewayPort gateway = new NoOpPaymentGatewayPort();

    @Test
    void chargeAlwaysSucceeds() {
        assertThat(gateway.charge("order-1", new BigDecimal("25.00"))).isEqualTo(PaymentOutcome.SUCCEEDED);
    }

    @Test
    void checkStatusAlwaysSucceeds() {
        assertThat(gateway.checkStatus("order-1")).isEqualTo(PaymentOutcome.SUCCEEDED);
    }
}
