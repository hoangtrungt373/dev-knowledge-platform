package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.common.exception.Validator;
import com.ttg.devknowledgeplatform.ecommerce.entity.Coupon;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponTarget;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponType;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.repository.CouponRedemptionRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.CouponRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.spec.CouponSpecification;
import com.ttg.devknowledgeplatform.ecommerce.service.CouponCommands;
import com.ttg.devknowledgeplatform.ecommerce.service.CouponService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Throwable.class)
public class CouponServiceImpl implements CouponService {

    private static final BigDecimal MAX_PERCENTAGE = new BigDecimal("100");

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository couponRedemptionRepository;

    @Override
    public Coupon create(CouponCommands.Create command) {
        String code = normalizeCode(command.code());
        Validator.isFalse(couponRepository.existsByCode(code), EcommerceErrorCode.COUPON_CODE_CONFLICT, code);
        validateValue(command.type(), command.value());
        validateDateRange(command.startAt(), command.endAt());

        Coupon coupon = new Coupon();
        coupon.setCode(code);
        coupon.setTarget(command.target());
        coupon.setType(command.type());
        coupon.setValue(command.value());
        coupon.setActive(command.active());
        coupon.setStartAt(command.startAt());
        coupon.setEndAt(command.endAt());
        coupon.setMinSubtotal(command.minSubtotal());
        coupon.setMaxRedemptions(command.maxRedemptions());
        coupon.setMaxRedemptionsPerUser(command.maxRedemptionsPerUser());

        Coupon saved = couponRepository.save(coupon);
        log.info("Created coupon id={} code={}", saved.getId(), code);
        return saved;
    }

    @Override
    public Coupon update(Integer id, CouponCommands.Update command) {
        Coupon coupon = findById(id);
        validateValue(command.type(), command.value());
        validateDateRange(command.startAt(), command.endAt());

        coupon.setTarget(command.target());
        coupon.setType(command.type());
        coupon.setValue(command.value());
        coupon.setActive(command.active());
        coupon.setStartAt(command.startAt());
        coupon.setEndAt(command.endAt());
        coupon.setMinSubtotal(command.minSubtotal());
        coupon.setMaxRedemptions(command.maxRedemptions());
        coupon.setMaxRedemptionsPerUser(command.maxRedemptionsPerUser());

        Coupon saved = couponRepository.save(coupon);
        log.info("Updated coupon id={}", id);
        return saved;
    }

    @Override
    public Coupon getById(Integer id) {
        return findById(id);
    }

    @Override
    public Page<Coupon> list(Pageable pageable, String q, Boolean active, CouponTarget target) {
        Specification<Coupon> spec = CouponSpecification.withFilters(q, active, target);
        return couponRepository.findAll(spec, pageable);
    }

    @Override
    public void delete(Integer id) {
        Coupon coupon = findById(id);
        Validator.isFalse(couponRedemptionRepository.existsByCouponId(id), EcommerceErrorCode.COUPON_IN_USE, id);
        couponRepository.delete(coupon);
        log.info("Deleted coupon id={}", id);
    }

    private Coupon findById(Integer id) {
        return Validator.notFound(couponRepository.findById(id), EcommerceErrorCode.COUPON_NOT_FOUND, id);
    }

    private static String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    private static void validateValue(CouponType type, BigDecimal value) {
        Validator.isTrue(value != null && value.compareTo(BigDecimal.ZERO) > 0,
                EcommerceErrorCode.COUPON_INVALID_VALUE, "Value must be greater than zero");
        if (type == CouponType.PERCENTAGE) {
            Validator.isTrue(value.compareTo(MAX_PERCENTAGE) <= 0,
                    EcommerceErrorCode.COUPON_INVALID_VALUE, "A percentage value cannot exceed 100");
        }
    }

    private static void validateDateRange(Instant startAt, Instant endAt) {
        if (startAt != null && endAt != null) {
            Validator.isTrue(endAt.isAfter(startAt), EcommerceErrorCode.COUPON_INVALID_DATE_RANGE);
        }
    }
}
