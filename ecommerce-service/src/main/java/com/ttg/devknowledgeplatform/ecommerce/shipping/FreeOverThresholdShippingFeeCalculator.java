package com.ttg.devknowledgeplatform.ecommerce.shipping;

import com.ttg.devknowledgeplatform.ecommerce.service.CartLine;

import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.util.List;

/**
 * Free shipping once {@code subtotal} reaches {@code freeShippingThreshold}; the same flat fee
 * {@link FlatRateShippingFeeCalculator} always charged otherwise — the classic "free shipping over
 * $X" promotion most real storefronts run, and this reactor's own answer to the "a single flat fee
 * isn't good enough" concern that motivated the {@link ShippingFeeCalculator} seam in the first
 * place.
 *
 * <p><b>Demoted back out of being the active bean, per request — {@code @Component} removed.</b>
 * This automatic, code-free waiver turned out to conflict with the Coupon feature's own
 * {@code SHIPPING_FEE}-target coupons once both existed at once: a shopper whose cart already
 * qualified for this threshold would see a {@code SHIPPING_FEE} coupon "apply" successfully (and
 * consume a real {@link com.ttg.devknowledgeplatform.ecommerce.entity.CouponRedemption}, including
 * any {@code maxRedemptions}/{@code maxRedemptionsPerUser} cap) for zero actual benefit, since
 * {@code CouponRedemptionServiceImpl.calculateDiscount} clamps to the shipping fee it's discounting
 * off of — already {@code 0} by the time a coupon-based discount is even computed. Rather than add
 * cross-mechanism guard logic (reject a coupon when this threshold already zeroed the fee), the
 * simpler fix was to stop running two independent shipping-discount mechanisms at once: coupons are
 * now the only way a shipping fee gets discounted, so there's no longer a case where a coupon can
 * apply to an already-{@code 0} fee. {@link FlatRateShippingFeeCalculator} is the active bean again
 * — see that class's own Javadoc. This class stays in the codebase as a reference implementation/an
 * easy strategy to switch back to (re-add {@code @Component} here, and remove it from whichever
 * strategy is active at that point) if a genuinely automatic, code-free promotion is wanted again
 * later — the same "don't leave two ambiguous candidates wired in at once" discipline this
 * reactor's other single-active-strategy seams follow (e.g. {@code payment.PaymentGatewayPort}'s
 * own {@code MockPaymentGateway}/{@code StripePaymentGateway} pair, gated by a property instead of
 * a manual {@code @Component} swap).
 *
 * <p>Both thresholds are externalized the same {@code @Value}-on-a-field way every other tunable
 * business value in this module already is: {@code app.ecommerce.checkout.free-shipping-threshold}
 * (default {@code 50.00}); {@code app.ecommerce.checkout.flat-shipping-fee} (default {@code 5.00})
 * is the pre-existing property, reused here as "the fee charged below the threshold" rather than
 * introducing a second, redundant property for the same number.
 */
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
