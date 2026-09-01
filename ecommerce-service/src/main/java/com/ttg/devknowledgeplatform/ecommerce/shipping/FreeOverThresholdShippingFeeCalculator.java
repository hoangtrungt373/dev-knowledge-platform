package com.ttg.devknowledgeplatform.ecommerce.shipping;

import com.ttg.devknowledgeplatform.ecommerce.service.CartLine;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Free shipping once {@code subtotal} reaches {@code freeShippingThreshold}; the same flat fee
 * {@link FlatRateShippingFeeCalculator} always charged otherwise — the classic "free shipping over
 * $X" promotion most real storefronts run, and this reactor's own answer to the "a single flat fee
 * isn't good enough" concern that motivated the {@link ShippingFeeCalculator} seam in the first
 * place.
 *
 * <p><b>The now-active {@link ShippingFeeCalculator} bean, per request.</b>
 * {@link FlatRateShippingFeeCalculator} stays in the codebase as a reference implementation/an easy
 * strategy to switch back to, but its own {@code @Component} was removed so it no longer competes
 * with this class for Spring's single-bean-per-interface wiring — same "don't leave two ambiguous
 * candidates wired in at once" reasoning {@code payment.NoOpPaymentGatewayPort}'s own Javadoc
 * documents for that seam's future real-adapter swap.
 *
 * <p>Both thresholds are externalized the same {@code @Value}-on-a-field way every other tunable
 * business value in this module already is: {@code app.ecommerce.checkout.free-shipping-threshold}
 * (default {@code 50.00}) is new; {@code app.ecommerce.checkout.flat-shipping-fee} (default
 * {@code 5.00}) is the pre-existing property, reused here as "the fee charged below the threshold"
 * rather than introducing a second, redundant property for the same number.
 */
@Component
public class FreeOverThresholdShippingFeeCalculator implements ShippingFeeCalculator {

    @Value("${app.ecommerce.checkout.free-shipping-threshold:50.00}")
    private BigDecimal freeShippingThreshold;

    @Value("${app.ecommerce.checkout.flat-shipping-fee:5.00}")
    private BigDecimal belowThresholdFee;

    @Override
    public ShippingFeeQuote calculate(List<CartLine> lines, BigDecimal subtotal) {
        boolean qualifiesForFreeShipping = subtotal.compareTo(freeShippingThreshold) >= 0;
        BigDecimal fee = qualifiesForFreeShipping ? BigDecimal.ZERO : belowThresholdFee;
        return new ShippingFeeQuote(fee, belowThresholdFee);
    }
}
