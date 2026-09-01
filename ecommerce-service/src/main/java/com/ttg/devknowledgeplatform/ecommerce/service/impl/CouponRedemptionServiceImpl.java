package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.common.exception.Validator;
import com.ttg.devknowledgeplatform.ecommerce.entity.Coupon;
import com.ttg.devknowledgeplatform.ecommerce.entity.CouponRedemption;
import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponTarget;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponType;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.repository.CouponRedemptionRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.CouponRepository;
import com.ttg.devknowledgeplatform.ecommerce.service.CouponRedemptionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Implementation of {@link CouponRedemptionService}.
 *
 * <p>{@link #resolve}'s redemption-count checks (the {@code countByCouponId}/
 * {@code countByCouponIdAndOwnerUuid} queries) are a plain re-check inside the caller's own
 * transaction, not an atomic claim-style {@code UPDATE} the way
 * {@code ProductVariantRepository.reserve} guards stock — a deliberate v1 simplification: a coupon
 * being redeemed once too often under a concurrent-request race is much lower-stakes than
 * overselling physical stock, so the extra mechanism wasn't judged worth it yet. Revisit with a
 * real atomic claim if coupon abuse at this exact race window ever becomes a genuine problem.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Throwable.class)
public class CouponRedemptionServiceImpl implements CouponRedemptionService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository couponRedemptionRepository;

    @Override
    public Coupon resolve(String code, CouponTarget target, String ownerUuid, BigDecimal subtotal) {
        String normalized = normalizeCode(code);
        Coupon coupon = Validator.notFound(couponRepository.findByCode(normalized), EcommerceErrorCode.COUPON_NOT_FOUND, normalized);

        Validator.isTrue(coupon.isActive(), EcommerceErrorCode.COUPON_INACTIVE, normalized);
        Validator.isTrue(coupon.getTarget() == target, EcommerceErrorCode.COUPON_TARGET_MISMATCH, normalized, target);

        Instant now = Instant.now();
        if (coupon.getStartAt() != null) {
            Validator.isTrue(!now.isBefore(coupon.getStartAt()), EcommerceErrorCode.COUPON_NOT_YET_ACTIVE, normalized);
        }
        if (coupon.getEndAt() != null) {
            Validator.isTrue(!now.isAfter(coupon.getEndAt()), EcommerceErrorCode.COUPON_EXPIRED, normalized);
        }
        if (coupon.getMinSubtotal() != null) {
            Validator.isTrue(subtotal.compareTo(coupon.getMinSubtotal()) >= 0,
                    EcommerceErrorCode.COUPON_MIN_SUBTOTAL_NOT_MET, normalized, coupon.getMinSubtotal());
        }
        if (coupon.getMaxRedemptions() != null) {
            Validator.isTrue(couponRedemptionRepository.countByCouponId(coupon.getId()) < coupon.getMaxRedemptions(),
                    EcommerceErrorCode.COUPON_REDEMPTION_LIMIT_REACHED, normalized);
        }
        if (coupon.getMaxRedemptionsPerUser() != null) {
            Validator.isTrue(
                    couponRedemptionRepository.countByCouponIdAndOwnerUuid(coupon.getId(), ownerUuid) < coupon.getMaxRedemptionsPerUser(),
                    EcommerceErrorCode.COUPON_ALREADY_REDEEMED_BY_USER, normalized);
        }
        return coupon;
    }

    @Override
    public BigDecimal calculateDiscount(Coupon coupon, BigDecimal baseAmount) {
        BigDecimal raw = coupon.getType() == CouponType.PERCENTAGE
                ? baseAmount.multiply(coupon.getValue()).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP)
                : coupon.getValue();
        BigDecimal clamped = raw.min(baseAmount);
        return clamped.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : clamped;
    }

    @Override
    public void redeem(Coupon coupon, Order order, String ownerUuid, BigDecimal discountAmount) {
        CouponRedemption redemption = new CouponRedemption();
        redemption.setCoupon(coupon);
        redemption.setOrder(order);
        redemption.setOwnerUuid(ownerUuid);
        redemption.setDiscountAmount(discountAmount);
        couponRedemptionRepository.save(redemption);
        log.info("Redeemed coupon id={} code={} for order id={} discountAmount={}",
                coupon.getId(), coupon.getCode(), order.getId(), discountAmount);
    }

    private static String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }
}
