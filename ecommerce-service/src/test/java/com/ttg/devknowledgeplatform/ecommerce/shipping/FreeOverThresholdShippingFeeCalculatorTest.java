package com.ttg.devknowledgeplatform.ecommerce.shipping;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FreeOverThresholdShippingFeeCalculator} — pinning down the
 * free-over-threshold boundary (at, above, below), and that {@code originalFee} always reports the
 * below-threshold fee even once it's waived, so a future strategy swap has an explicit contrast
 * point — same reasoning {@link com.ttg.devknowledgeplatform.ecommerce.payment.MockPaymentGatewayTest}
 * follows for that seam's own placeholder strategy.
 */
class FreeOverThresholdShippingFeeCalculatorTest {

    private static final BigDecimal THRESHOLD = new BigDecimal("50.00");
    private static final BigDecimal BELOW_THRESHOLD_FEE = new BigDecimal("5.00");

    private final FreeOverThresholdShippingFeeCalculator calculator = new FreeOverThresholdShippingFeeCalculator();

    {
        ReflectionTestUtils.setField(calculator, "freeShippingThreshold", THRESHOLD);
        ReflectionTestUtils.setField(calculator, "belowThresholdFee", BELOW_THRESHOLD_FEE);
    }

    @Test
    void chargesTheFlatFeeBelowTheThreshold() {
        ShippingFeeQuote quote = calculator.calculate(List.of(), new BigDecimal("49.99"));

        assertThat(quote.fee()).isEqualByComparingTo(BELOW_THRESHOLD_FEE);
        assertThat(quote.originalFee()).isEqualByComparingTo(BELOW_THRESHOLD_FEE);
    }

    @Test
    void isFreeExactlyAtTheThresholdButStillReportsTheOriginalFee() {
        ShippingFeeQuote quote = calculator.calculate(List.of(), THRESHOLD);

        assertThat(quote.fee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(quote.originalFee()).isEqualByComparingTo(BELOW_THRESHOLD_FEE);
    }

    @Test
    void isFreeAboveTheThresholdButStillReportsTheOriginalFee() {
        ShippingFeeQuote quote = calculator.calculate(List.of(), new BigDecimal("50.01"));

        assertThat(quote.fee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(quote.originalFee()).isEqualByComparingTo(BELOW_THRESHOLD_FEE);
    }
}
