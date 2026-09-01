package com.ttg.devknowledgeplatform.ecommerce.mapper;

import com.ttg.devknowledgeplatform.ecommerce.dto.AvailableCouponResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.CouponResponse;
import com.ttg.devknowledgeplatform.ecommerce.entity.Coupon;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public interface CouponMapper {

    @Mapping(source = "dteCreation", target = "createdAt")
    @Mapping(source = "dteLastModification", target = "updatedAt")
    CouponResponse toResponse(Coupon coupon);

    /**
     * Hand-written, not MapStruct-generated — {@code eligible}/{@code discountAmount} are passed
     * in already computed (by {@code CouponRedemptionService#listAvailableRanked}), not derived
     * here: neither is a plain {@link Coupon} field, and computing either needs service-layer logic
     * (a live subtotal comparison, {@code calculateDiscount}) a mapper shouldn't reach for itself —
     * same reason {@code CartMapper}/{@code CheckoutMapper} hand-write their own aggregate-computing
     * methods instead of a generated per-field mapping, just with the computation itself pushed one
     * layer further out.
     *
     * @param eligible       whether the caller's live subtotal meets this coupon's own
     *                       {@code minSubtotal} — see {@code CouponRedemptionService.RankedCoupon}
     * @param discountAmount what this coupon would actually deduct from this order right now —
     *                       see {@link AvailableCouponResponse#getDiscountAmount()}'s own Javadoc
     */
    default AvailableCouponResponse toAvailableResponse(Coupon coupon, boolean eligible, BigDecimal discountAmount) {
        return AvailableCouponResponse.builder()
                .code(coupon.getCode())
                .target(coupon.getTarget())
                .type(coupon.getType())
                .value(coupon.getValue())
                .minSubtotal(coupon.getMinSubtotal())
                .maxDiscountAmount(coupon.getMaxDiscountAmount())
                .description(coupon.getDescription())
                .imageUrl(coupon.getImageUrl())
                .endAt(coupon.getEndAt())
                .eligible(eligible)
                .discountAmount(discountAmount)
                .build();
    }
}
