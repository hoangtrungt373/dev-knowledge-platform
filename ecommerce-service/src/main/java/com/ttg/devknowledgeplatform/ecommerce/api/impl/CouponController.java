package com.ttg.devknowledgeplatform.ecommerce.api.impl;

import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.ecommerce.api.CouponApi;
import com.ttg.devknowledgeplatform.ecommerce.dto.CouponResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.CreateCouponRequest;
import com.ttg.devknowledgeplatform.ecommerce.dto.UpdateCouponRequest;
import com.ttg.devknowledgeplatform.ecommerce.entity.Coupon;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponTarget;
import com.ttg.devknowledgeplatform.ecommerce.mapper.CouponMapper;
import com.ttg.devknowledgeplatform.ecommerce.service.CouponCommands;
import com.ttg.devknowledgeplatform.ecommerce.service.CouponService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * Implementation of {@link CouponApi}.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class CouponController implements CouponApi {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "code", "dteCreation");

    private final CouponService couponService;
    private final CouponMapper couponMapper;

    @Override
    public ResponseEntity<CouponResponse> create(CreateCouponRequest request) {
        Coupon coupon = couponService.create(new CouponCommands.Create(
                request.getCode(), request.getTarget(), request.getType(), request.getValue(), request.isActive(),
                request.getStartAt(), request.getEndAt(), request.getMinSubtotal(),
                request.getMaxRedemptions(), request.getMaxRedemptionsPerUser(),
                request.getMaxDiscountAmount(), request.getDescription(), request.getImageUrl()));
        return ResponseEntity.status(HttpStatus.CREATED).body(couponMapper.toResponse(coupon));
    }

    @Override
    public ResponseEntity<CouponResponse> update(Integer id, UpdateCouponRequest request) {
        Coupon coupon = couponService.update(id, new CouponCommands.Update(
                request.getTarget(), request.getType(), request.getValue(), request.isActive(),
                request.getStartAt(), request.getEndAt(), request.getMinSubtotal(),
                request.getMaxRedemptions(), request.getMaxRedemptionsPerUser(),
                request.getMaxDiscountAmount(), request.getDescription(), request.getImageUrl()));
        return ResponseEntity.ok(couponMapper.toResponse(coupon));
    }

    @Override
    public ResponseEntity<Void> delete(Integer id) {
        couponService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<CouponResponse> getById(Integer id) {
        return ResponseEntity.ok(couponMapper.toResponse(couponService.getById(id)));
    }

    @Override
    public ResponseEntity<PagedResponse<CouponResponse>> list(
            int page, int size, String sortBy, String sortDir, String q, Boolean active, CouponTarget target) {
        Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir));
        Page<CouponResponse> responses = couponService.list(pageable, q, active, target).map(couponMapper::toResponse);
        return ResponseEntity.ok(PagedResponse.from(responses));
    }

    private Sort buildSort(String sortBy, String sortDir) {
        String field = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "id";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }
}
