package com.ttg.devknowledgeplatform.ecommerce.api.impl;

import com.ttg.devknowledgeplatform.ecommerce.api.CouponPickerApi;
import com.ttg.devknowledgeplatform.ecommerce.dto.AvailableCouponResponse;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponTarget;
import com.ttg.devknowledgeplatform.ecommerce.mapper.CouponMapper;
import com.ttg.devknowledgeplatform.ecommerce.service.CouponRedemptionService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * Implementation of {@link CouponPickerApi}. Deliberately thin — picks which amount each coupon's
 * discount is computed against (the one piece of routing this endpoint's own caller has to decide,
 * mirroring the identical {@code target}-based choice {@code CheckoutServiceImpl.resolveDiscounts}
 * already makes for the real checkout path), then hands everything else — eligibility, the actual
 * discount computation, and the "what's best for this order" sort — to
 * {@link CouponRedemptionService#listAvailableRanked}, per this reactor's own "business logic
 * belongs in the service layer, not the controller" convention.
 */
@RestController
@RequiredArgsConstructor
public class CouponPickerController implements CouponPickerApi {

    private final CouponRedemptionService couponRedemptionService;
    private final CouponMapper couponMapper;

    @Override
    public ResponseEntity<List<AvailableCouponResponse>> listAvailable(
            String userUuid, CouponTarget target, BigDecimal subtotal, BigDecimal shippingFee) {
        BigDecimal baseAmount = target == CouponTarget.SHIPPING_FEE
                ? (shippingFee != null ? shippingFee : BigDecimal.ZERO)
                : subtotal;

        List<AvailableCouponResponse> responses = couponRedemptionService
                .listAvailableRanked(target, userUuid, subtotal, baseAmount).stream()
                .map(ranked -> couponMapper.toAvailableResponse(ranked.coupon(), ranked.eligible(), ranked.discountAmount()))
                .toList();
        return ResponseEntity.ok(responses);
    }
}
