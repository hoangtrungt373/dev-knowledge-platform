package com.ttg.devknowledgeplatform.ecommerce.shipping;

import com.ttg.devknowledgeplatform.ecommerce.service.CartLine;

import java.math.BigDecimal;
import java.util.List;

/**
 * The seam between checkout and however this reactor currently prices shipping — a GoF
 * <b>Strategy</b> (Behavioral), mirroring {@code payment.PaymentGatewayPort}'s own "interface
 * today, swap the implementation later" shape: {@code CheckoutServiceImpl} depends on this
 * interface only, never a concrete pricing rule, so a future strategy (free-over-threshold,
 * weight-tiered, a real carrier-rate API call) can replace {@link FlatRateShippingFeeCalculator}
 * — or sit alongside it, selected by a future admin setting/A-B test — without touching checkout
 * itself.
 *
 * <p>Deliberately takes the checkout-eligible cart lines and their precomputed subtotal, not the
 * full {@link com.ttg.devknowledgeplatform.ecommerce.service.Cart} or an
 * {@link com.ttg.devknowledgeplatform.ecommerce.entity.Order} — the exact inputs a near-term
 * rule-based strategy actually needs (free-over-threshold from {@code subtotal}; weight-tiered
 * from a future {@code ProductVariant.weight} summed across {@code lines}). {@code
 * CheckoutServiceImpl.preview} has no resolved shipping
 * {@link com.ttg.devknowledgeplatform.ecommerce.entity.Address} available at all today (the
 * shopper hasn't chosen one yet at preview time), so a genuinely zone/carrier-based strategy that
 * needs a destination is out of scope for this seam as it stands — widen this method's signature
 * (and thread an address through {@code preview} too) when that's actually built, rather than
 * assuming it already works.
 */
public interface ShippingFeeCalculator {

    /**
     * @param lines    the checkout-eligible cart lines — already filtered to {@code available} and
     *                 any {@code selectedVariantIds} (see
     *                 {@code CheckoutServiceImpl.requireCheckoutableCart})
     * @param subtotal the sum of {@code lines}' own line totals, precomputed by the caller so every
     *                 strategy doesn't need to re-derive it independently
     * @return the shipping fee to charge — never {@code null}
     */
    BigDecimal calculate(List<CartLine> lines, BigDecimal subtotal);
}
