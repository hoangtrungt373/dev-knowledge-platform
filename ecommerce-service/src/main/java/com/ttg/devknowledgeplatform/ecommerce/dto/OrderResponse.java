package com.ttg.devknowledgeplatform.ecommerce.dto;

import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.enums.PaymentFailureCategory;
import com.ttg.devknowledgeplatform.ecommerce.enums.PaymentStatus;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * REST response shape for a shopper's order (Epic 3 Phase 5, US-3.5) — current status, its lines,
 * and its full transition history in one shape, reused for both the list-mine and get-by-id
 * endpoints (same "one response DTO for list and detail" convention {@code ProductResponse}
 * already established in this module).
 *
 * <p>{@link #paymentStatus}/{@link #paymentFailureCategory}/{@link #paymentFailureMessage} (Epic 4
 * Phase 7, US-4.7) are a live lookup of this order's own {@code Payment} row, resolved by
 * {@code mapper.OrderMapper} — all {@code null} until a payment attempt has actually started
 * (a {@code PENDING} order that never called {@code POST /{id}/pay} has no {@code Payment} row at
 * all). {@link #paymentFailureMessage} is a small, non-technical, server-owned string
 * ({@code PaymentFailureCategory#getShopperMessage()}) — <b>never</b> the gateway's own raw decline
 * string, which this reactor never sends to a client at all.
 */
@Data
@Builder
public class OrderResponse {

    private Integer id;
    private OrderStatus status;
    private Boolean cancelRequested;
    private AddressResponse shippingAddress;
    private BigDecimal subtotal;
    /** What a {@code subtotalCouponCode} (Coupon feature, Phase 2) deducted from {@code subtotal}
     * — zero when none was applied. {@code subtotal} itself is never reduced. */
    private BigDecimal subtotalDiscountAmount;
    /** The subtotal-targeting coupon code applied to this order, if any — {@code null} if none. */
    private String subtotalCouponCode;
    private BigDecimal shippingFee;
    /** What {@code shippingFee} would have been absent any promotional waiver — equal to
     * {@code shippingFee} whenever nothing was waived; see {@code Order.originalShippingFee}'s
     * own Javadoc. */
    private BigDecimal originalShippingFee;
    /** The shipping-fee-targeting coupon code applied to this order, if any — {@code null} if
     * none (this is independent of any automatic free-shipping waiver, which has no code). */
    private String shippingCouponCode;
    private BigDecimal total;
    private PaymentStatus paymentStatus;
    private PaymentFailureCategory paymentFailureCategory;
    private String paymentFailureMessage;
    private List<OrderLineResponse> lines;
    private List<OrderStatusHistoryResponse> statusHistory;
}
