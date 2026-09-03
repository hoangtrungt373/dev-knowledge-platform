package com.ttg.devknowledgeplatform.ecommerce.shipping;

import com.ttg.devknowledgeplatform.ecommerce.service.CartLine;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * A single fee regardless of cart contents, externalized via
 * {@code app.ecommerce.checkout.flat-shipping-fee}. This was the only {@link ShippingFeeCalculator}
 * in the context when this seam was first built, was demoted once
 * {@link FreeOverThresholdShippingFeeCalculator} became the active bean, and is **the active bean
 * again now, per request** — that automatic threshold-based waiver turned out to conflict with the
 * Coupon feature's own {@code SHIPPING_FEE}-target coupons (a shopper whose cart already qualified
 * for free shipping could "apply" a shipping coupon that visibly did nothing, yet still consumed a
 * real redemption — see {@link FreeOverThresholdShippingFeeCalculator}'s own Javadoc for the full
 * incident). Coupons are now the only mechanism that discounts a shipping fee at all, so there's no
 * second, automatic waiver left to collide with them. {@link FreeOverThresholdShippingFeeCalculator}
 * stays in the codebase as a reference implementation/an easy strategy to switch back to, but
 * deliberately carries **no `@Component`** anymore, so it doesn't compete with this class for
 * Spring's single-bean-per-interface wiring — the same "don't leave two ambiguous candidates
 * wired in at once" discipline this reactor's other single-active-strategy seams follow (e.g.
 * {@code payment.PaymentGatewayPort}'s own {@code MockPaymentGateway}/{@code StripePaymentGateway}
 * pair, gated by a property instead of a manual {@code @Component} swap). Re-add {@code @Component}
 * there (and remove it from this class) to switch back.
 */
@Component
public class FlatRateShippingFeeCalculator implements ShippingFeeCalculator {

    @Value("${app.ecommerce.checkout.flat-shipping-fee:5.00}")
    private BigDecimal flatShippingFee;

    @Override
    public ShippingFeeQuote calculate(List<CartLine> lines, BigDecimal subtotal) {
        return new ShippingFeeQuote(flatShippingFee, flatShippingFee);
    }
}
