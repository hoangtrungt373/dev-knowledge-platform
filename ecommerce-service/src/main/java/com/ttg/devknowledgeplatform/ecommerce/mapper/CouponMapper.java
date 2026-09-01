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
     * Hand-written, not MapStruct-generated — {@code eligible} needs the caller's live cart
     * {@code subtotal}, which isn't a {@link Coupon} field at all, the same reason
     * {@code CartMapper}/{@code CheckoutMapper} hand-write their own aggregate-computing methods
     * instead of a generated per-field mapping.
     *
     * @param subtotal the caller's current cart subtotal (see {@code CouponPickerApi#listAvailable})
     */
    default AvailableCouponResponse toAvailableResponse(Coupon coupon, BigDecimal subtotal) {
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
                .eligible(coupon.getMinSubtotal() == null || subtotal.compareTo(coupon.getMinSubtotal()) >= 0)
                .build();
    }
}
