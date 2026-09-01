package com.ttg.devknowledgeplatform.ecommerce.shipping;

import com.ttg.devknowledgeplatform.ecommerce.service.CartLine;

import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.util.List;

/**
 * A single fee regardless of cart contents, externalized via
 * {@code app.ecommerce.checkout.flat-shipping-fee}. This was the only {@link ShippingFeeCalculator}
 * in the context when this seam was first built; {@link FreeOverThresholdShippingFeeCalculator} is
 * the active bean now, per request — this class is kept as a reference implementation/an easy
 * strategy to switch back to, but deliberately carries **no `@Component`** anymore, so it doesn't
 * compete with the active strategy for Spring's single-bean-per-interface wiring (see
 * {@link FreeOverThresholdShippingFeeCalculator}'s own Javadoc for the same "don't leave two
 * ambiguous candidates wired in at once" reasoning {@code payment.NoOpPaymentGatewayPort}'s Javadoc
 * already documents for that seam). Re-add {@code @Component} (and remove it from whichever
 * strategy is currently active) to switch back to plain flat-rate shipping.
 */
public class FlatRateShippingFeeCalculator implements ShippingFeeCalculator {

    @Value("${app.ecommerce.checkout.flat-shipping-fee:5.00}")
    private BigDecimal flatShippingFee;

    @Override
    public ShippingFeeQuote calculate(List<CartLine> lines, BigDecimal subtotal) {
        return new ShippingFeeQuote(flatShippingFee, flatShippingFee);
    }
}
