package com.ttg.devknowledgeplatform.ecommerce.shipping;

import java.math.BigDecimal;

/**
 * The result of a {@link ShippingFeeCalculator#calculate} call — the fee actually charged, plus
 * what would have been charged absent any promotional waiver. {@code originalFee} lets a caller
 * (the checkout preview response, ultimately the GUI) show "was $5.00, now free" messaging without
 * knowing which concrete strategy produced it — {@link FlatRateShippingFeeCalculator} simply
 * reports {@code originalFee == fee} (it has no discount concept at all), while
 * {@link FreeOverThresholdShippingFeeCalculator} reports the below-threshold fee as
 * {@code originalFee} even when {@code fee} is waived to zero. A future percentage-off-shipping
 * strategy would fit this same shape without needing a third field.
 *
 * @param fee         the amount to actually charge — never {@code null}
 * @param originalFee what would have been charged with no promotional waiver applied — never
 *                    {@code null}; equal to {@code fee} whenever nothing was waived
 */
public record ShippingFeeQuote(BigDecimal fee, BigDecimal originalFee) {
}
