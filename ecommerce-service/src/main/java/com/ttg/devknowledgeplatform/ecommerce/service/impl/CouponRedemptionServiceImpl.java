package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.common.exception.Validator;
import com.ttg.devknowledgeplatform.ecommerce.entity.Coupon;
import com.ttg.devknowledgeplatform.ecommerce.entity.CouponRedemption;
import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponTarget;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponType;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.repository.CouponRedemptionCount;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        // maxDiscountAmount is an independent cap on top of the raw calculation — applied before
        // the base-amount clamp below, and uniformly to both CouponType values (see Coupon's own
        // Javadoc for why a capped FIXED_AMOUNT coupon is a valid, if unusual, admin choice too).
        BigDecimal capped = coupon.getMaxDiscountAmount() != null ? raw.min(coupon.getMaxDiscountAmount()) : raw;
        BigDecimal clamped = capped.min(baseAmount);
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

    /**
     * Bug fix: this used to call {@code couponRedemptionRepository.countByCouponId}/
     * {@code countByCouponIdAndOwnerUuid} once per candidate coupon inside the filter chain — an
     * N+1 query pattern hit every time a shopper opens the coupon-picker dialog. Both counts are
     * now resolved in at most one grouped query each (never more, regardless of how many candidate
     * coupons there are), and only when at least one candidate actually has that kind of limit set
     * at all — a coupon list with no redemption limits configured still costs zero count queries,
     * exactly as before this fix.
     */
    @Override
    public List<Coupon> listAvailable(CouponTarget target, String ownerUuid) {
        Instant now = Instant.now();
        List<Coupon> candidates = couponRepository.findAllByTargetAndActiveTrueOrderByValueDesc(target).stream()
                .filter(c -> c.getStartAt() == null || !now.isBefore(c.getStartAt()))
                .filter(c -> c.getEndAt() == null || !now.isAfter(c.getEndAt()))
                .toList();
        if (candidates.isEmpty()) {
            return candidates;
        }

        List<Integer> globalLimitedIds = candidates.stream()
                .filter(c -> c.getMaxRedemptions() != null).map(Coupon::getId).toList();
        Map<Integer, Long> globalCounts = globalLimitedIds.isEmpty()
                ? Map.of()
                : toCountMap(couponRedemptionRepository.countGroupedByCouponId(globalLimitedIds));

        List<Integer> perUserLimitedIds = candidates.stream()
                .filter(c -> c.getMaxRedemptionsPerUser() != null).map(Coupon::getId).toList();
        Map<Integer, Long> perUserCounts = perUserLimitedIds.isEmpty()
                ? Map.of()
                : toCountMap(couponRedemptionRepository.countGroupedByCouponIdForOwner(perUserLimitedIds, ownerUuid));

        return candidates.stream()
                .filter(c -> c.getMaxRedemptions() == null
                        || globalCounts.getOrDefault(c.getId(), 0L) < c.getMaxRedemptions())
                .filter(c -> c.getMaxRedemptionsPerUser() == null
                        || perUserCounts.getOrDefault(c.getId(), 0L) < c.getMaxRedemptionsPerUser())
                .toList();
    }

    private static Map<Integer, Long> toCountMap(List<CouponRedemptionCount> counts) {
        return counts.stream().collect(Collectors.toMap(CouponRedemptionCount::getCouponId, CouponRedemptionCount::getTotal));
    }

    @Override
    public List<RankedCoupon> listAvailableRanked(
            CouponTarget target, String ownerUuid, BigDecimal subtotal, BigDecimal baseAmount) {
        return listAvailable(target, ownerUuid).stream()
                .map(coupon -> new RankedCoupon(
                        coupon,
                        coupon.getMinSubtotal() == null || subtotal.compareTo(coupon.getMinSubtotal()) >= 0,
                        calculateDiscount(coupon, baseAmount)))
                .sorted(Comparator.comparing(RankedCoupon::eligible).reversed()
                        .thenComparing(RankedCoupon::discountAmount, Comparator.reverseOrder()))
                .toList();
    }

    private static String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }
}
