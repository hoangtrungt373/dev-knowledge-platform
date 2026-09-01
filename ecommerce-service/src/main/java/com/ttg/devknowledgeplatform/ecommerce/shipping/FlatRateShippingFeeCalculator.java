package com.ttg.devknowledgeplatform.ecommerce.shipping;

import com.ttg.devknowledgeplatform.ecommerce.service.CartLine;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * The only {@link ShippingFeeCalculator} in the context today — a single fee regardless of cart
 * contents, externalized via {@code app.ecommerce.checkout.flat-shipping-fee} (moved here from
 * {@code CheckoutServiceImpl}'s own field, its home before this seam existed). Same
 * {@code @Value}-on-a-field convention {@code CartServiceImpl}'s own {@code cartTtl} already
 * established for a tunable business value.
 *
 * <p>Add a second {@link ShippingFeeCalculator} implementation (free-over-threshold, weight-tiered,
 * a real carrier-rate call) once one is actually needed — see this interface's own Javadoc for the
 * inputs each of those would need and the one it doesn't have (a resolved shipping address).
 */
@Component
public class FlatRateShippingFeeCalculator implements ShippingFeeCalculator {

    @Value("${app.ecommerce.checkout.flat-shipping-fee:5.00}")
    private BigDecimal flatShippingFee;

    @Override
    public BigDecimal calculate(List<CartLine> lines, BigDecimal subtotal) {
        return flatShippingFee;
    }
}
