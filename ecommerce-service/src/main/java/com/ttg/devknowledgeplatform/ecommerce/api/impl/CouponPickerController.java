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
 * Implementation of {@link CouponPickerApi}.
 */
@RestController
@RequiredArgsConstructor
public class CouponPickerController implements CouponPickerApi {

    private final CouponRedemptionService couponRedemptionService;
    private final CouponMapper couponMapper;

    @Override
    public ResponseEntity<List<AvailableCouponResponse>> listAvailable(
            String userUuid, CouponTarget target, BigDecimal subtotal) {
        List<AvailableCouponResponse> responses = couponRedemptionService.listAvailable(target, userUuid).stream()
                .map(coupon -> couponMapper.toAvailableResponse(coupon, subtotal))
                .toList();
        return ResponseEntity.ok(responses);
    }
}
