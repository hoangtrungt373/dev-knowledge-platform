package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.ecommerce.entity.Coupon;
import com.ttg.devknowledgeplatform.ecommerce.entity.CouponRedemption;
import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponTarget;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponType;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.repository.CouponRedemptionRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.CouponRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CouponRedemptionServiceImpl} — Phase 2 of the Coupon feature:
 * eligibility resolution, discount arithmetic, and redemption persistence.
 * {@code CheckoutServiceImplTest} covers how {@code CheckoutServiceImpl} wires this service into
 * checkout's own totals; these tests only cover this service's own logic in isolation.
 */
@ExtendWith(MockitoExtension.class)
class CouponRedemptionServiceImplTest {

    private static final String OWNER_UUID = "user-uuid-1";

    @Mock
    private CouponRepository couponRepository;
    @Mock
    private CouponRedemptionRepository couponRedemptionRepository;

    @InjectMocks
    private CouponRedemptionServiceImpl service;

    private Coupon coupon;

    @BeforeEach
    void setUp() {
        coupon = new Coupon();
        coupon.setId(1);
        coupon.setCode("SAVE10");
        coupon.setTarget(CouponTarget.SUBTOTAL);
        coupon.setType(CouponType.FIXED_AMOUNT);
        coupon.setValue(new BigDecimal("10.00"));
        coupon.setActive(true);
        // Every condition/limit check is lenient-safe (no-op) unless a specific test sets one —
        // Resolve's own tests below set exactly the field each case cares about.
        lenient().when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.of(coupon));
    }

    @Nested
    class Resolve {

        @Test
        void returnsTheCouponWhenEverythingIsEligible() {
            Coupon result = service.resolve("save10", CouponTarget.SUBTOTAL, OWNER_UUID, new BigDecimal("50.00"));

            assertThat(result).isEqualTo(coupon);
        }

        @Test
        void normalizesTheCodeBeforeLookup() {
            service.resolve("  save10  ", CouponTarget.SUBTOTAL, OWNER_UUID, new BigDecimal("50.00"));

            verify(couponRepository).findByCode("SAVE10");
        }

        @Test
        void throwsWhenCodeDoesNotExist() {
            when(couponRepository.findByCode("NOPE")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.resolve("NOPE", CouponTarget.SUBTOTAL, OWNER_UUID, BigDecimal.TEN))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void rejectsAnInactiveCoupon() {
            coupon.setActive(false);

            assertThatThrownBy(() -> service.resolve("SAVE10", CouponTarget.SUBTOTAL, OWNER_UUID, BigDecimal.TEN))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.COUPON_INACTIVE);
        }

        @Test
        void rejectsATargetMismatch() {
            assertThatThrownBy(() -> service.resolve("SAVE10", CouponTarget.SHIPPING_FEE, OWNER_UUID, BigDecimal.TEN))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.COUPON_TARGET_MISMATCH);
        }

        @Test
        void rejectsACouponNotYetActive() {
            coupon.setStartAt(Instant.now().plus(1, ChronoUnit.DAYS));

            assertThatThrownBy(() -> service.resolve("SAVE10", CouponTarget.SUBTOTAL, OWNER_UUID, BigDecimal.TEN))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.COUPON_NOT_YET_ACTIVE);
        }

        @Test
        void allowsACouponExactlyAtItsStartMoment() {
            Instant now = Instant.now();
            coupon.setStartAt(now.minusSeconds(1));

            Coupon result = service.resolve("SAVE10", CouponTarget.SUBTOTAL, OWNER_UUID, BigDecimal.TEN);

            assertThat(result).isEqualTo(coupon);
        }

        @Test
        void rejectsAnExpiredCoupon() {
            coupon.setEndAt(Instant.now().minus(1, ChronoUnit.DAYS));

            assertThatThrownBy(() -> service.resolve("SAVE10", CouponTarget.SUBTOTAL, OWNER_UUID, BigDecimal.TEN))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.COUPON_EXPIRED);
        }

        @Test
        void rejectsASubtotalBelowTheMinimum() {
            coupon.setMinSubtotal(new BigDecimal("50.00"));

            assertThatThrownBy(() -> service.resolve("SAVE10", CouponTarget.SUBTOTAL, OWNER_UUID, new BigDecimal("49.99")))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.COUPON_MIN_SUBTOTAL_NOT_MET);
        }

        @Test
        void allowsASubtotalExactlyAtTheMinimum() {
            coupon.setMinSubtotal(new BigDecimal("50.00"));

            Coupon result = service.resolve("SAVE10", CouponTarget.SUBTOTAL, OWNER_UUID, new BigDecimal("50.00"));

            assertThat(result).isEqualTo(coupon);
        }

        @Test
        void rejectsWhenTheGlobalRedemptionLimitIsReached() {
            coupon.setMaxRedemptions(3);
            when(couponRedemptionRepository.countByCouponId(1)).thenReturn(3L);

            assertThatThrownBy(() -> service.resolve("SAVE10", CouponTarget.SUBTOTAL, OWNER_UUID, BigDecimal.TEN))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.COUPON_REDEMPTION_LIMIT_REACHED);
        }

        @Test
        void allowsRedemptionWhenBelowTheGlobalLimit() {
            coupon.setMaxRedemptions(3);
            when(couponRedemptionRepository.countByCouponId(1)).thenReturn(2L);

            Coupon result = service.resolve("SAVE10", CouponTarget.SUBTOTAL, OWNER_UUID, BigDecimal.TEN);

            assertThat(result).isEqualTo(coupon);
        }

        @Test
        void rejectsWhenThePerUserRedemptionLimitIsReached() {
            coupon.setMaxRedemptionsPerUser(1);
            when(couponRedemptionRepository.countByCouponIdAndOwnerUuid(1, OWNER_UUID)).thenReturn(1L);

            assertThatThrownBy(() -> service.resolve("SAVE10", CouponTarget.SUBTOTAL, OWNER_UUID, BigDecimal.TEN))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.COUPON_ALREADY_REDEEMED_BY_USER);
        }
    }

    @Nested
    class CalculateDiscount {

        @Test
        void computesAPercentageOfTheBaseAmount() {
            coupon.setType(CouponType.PERCENTAGE);
            coupon.setValue(new BigDecimal("20"));

            BigDecimal discount = service.calculateDiscount(coupon, new BigDecimal("50.00"));

            assertThat(discount).isEqualByComparingTo("10.00");
        }

        @Test
        void usesTheFixedAmountDirectly() {
            coupon.setType(CouponType.FIXED_AMOUNT);
            coupon.setValue(new BigDecimal("7.50"));

            BigDecimal discount = service.calculateDiscount(coupon, new BigDecimal("50.00"));

            assertThat(discount).isEqualByComparingTo("7.50");
        }

        @Test
        void clampsAFixedAmountThatWouldExceedTheBaseAmount() {
            coupon.setType(CouponType.FIXED_AMOUNT);
            coupon.setValue(new BigDecimal("50.00"));

            BigDecimal discount = service.calculateDiscount(coupon, new BigDecimal("5.00"));

            assertThat(discount).isEqualByComparingTo("5.00");
        }

        @Test
        void capsAPercentageDiscountAtMaxDiscountAmount() {
            // "20% off, capped at $20" on a $500 subtotal — the raw 20% ($100) must be capped
            // down to $20, not left uncapped.
            coupon.setType(CouponType.PERCENTAGE);
            coupon.setValue(new BigDecimal("20"));
            coupon.setMaxDiscountAmount(new BigDecimal("20.00"));

            BigDecimal discount = service.calculateDiscount(coupon, new BigDecimal("500.00"));

            assertThat(discount).isEqualByComparingTo("20.00");
        }

        @Test
        void leavesAPercentageDiscountBelowMaxDiscountAmountUntouched() {
            coupon.setType(CouponType.PERCENTAGE);
            coupon.setValue(new BigDecimal("20"));
            coupon.setMaxDiscountAmount(new BigDecimal("50.00"));

            BigDecimal discount = service.calculateDiscount(coupon, new BigDecimal("100.00"));

            assertThat(discount).isEqualByComparingTo("20.00"); // 20% of 100 = 20, below the 50 cap
        }

        @Test
        void capsAFixedAmountDiscountBelowItsOwnValueToo() {
            // A cap under the fixed value is a valid, if unusual, admin choice — the cap applies
            // uniformly to both CouponType values, not just PERCENTAGE.
            coupon.setType(CouponType.FIXED_AMOUNT);
            coupon.setValue(new BigDecimal("30.00"));
            coupon.setMaxDiscountAmount(new BigDecimal("10.00"));

            BigDecimal discount = service.calculateDiscount(coupon, new BigDecimal("100.00"));

            assertThat(discount).isEqualByComparingTo("10.00");
        }

        @Test
        void aMaxDiscountAmountCapNeverOverridesTheBaseAmountClamp() {
            // The base-amount clamp still applies after the cap — a $20 cap on a $5 base amount
            // must still land at $5, not $20.
            coupon.setType(CouponType.PERCENTAGE);
            coupon.setValue(new BigDecimal("100"));
            coupon.setMaxDiscountAmount(new BigDecimal("20.00"));

            BigDecimal discount = service.calculateDiscount(coupon, new BigDecimal("5.00"));

            assertThat(discount).isEqualByComparingTo("5.00");
        }
    }

    @Nested
    class Redeem {

        @Test
        void persistsARedemptionRow() {
            Order order = new Order();
            order.setId(42);
            ArgumentCaptor<CouponRedemption> captor = ArgumentCaptor.forClass(CouponRedemption.class);
            when(couponRedemptionRepository.save(any(CouponRedemption.class))).thenAnswer(invocation -> invocation.getArgument(0));

            service.redeem(coupon, order, OWNER_UUID, new BigDecimal("10.00"));

            verify(couponRedemptionRepository).save(captor.capture());
            CouponRedemption redemption = captor.getValue();
            assertThat(redemption.getCoupon()).isEqualTo(coupon);
            assertThat(redemption.getOrder()).isEqualTo(order);
            assertThat(redemption.getOwnerUuid()).isEqualTo(OWNER_UUID);
            assertThat(redemption.getDiscountAmount()).isEqualByComparingTo("10.00");
        }
    }

    @Nested
    class ListAvailable {

        private Coupon candidate(String code) {
            Coupon c = new Coupon();
            c.setId(2);
            c.setCode(code);
            c.setTarget(CouponTarget.SUBTOTAL);
            c.setType(CouponType.PERCENTAGE);
            c.setValue(new BigDecimal("15"));
            c.setActive(true);
            return c;
        }

        @Test
        void includesACurrentlyActiveCouponWithNoConditions() {
            Coupon c = candidate("SPRING15");
            when(couponRepository.findAllByTargetAndActiveTrueOrderByValueDesc(CouponTarget.SUBTOTAL))
                    .thenReturn(List.of(c));

            List<Coupon> result = service.listAvailable(CouponTarget.SUBTOTAL, OWNER_UUID);

            assertThat(result).containsExactly(c);
        }

        @Test
        void excludesACouponNotYetStarted() {
            Coupon c = candidate("FUTURE10");
            c.setStartAt(Instant.now().plus(1, ChronoUnit.DAYS));
            when(couponRepository.findAllByTargetAndActiveTrueOrderByValueDesc(CouponTarget.SUBTOTAL))
                    .thenReturn(List.of(c));

            assertThat(service.listAvailable(CouponTarget.SUBTOTAL, OWNER_UUID)).isEmpty();
        }

        @Test
        void includesACouponExactlyAtItsStartMoment() {
            Coupon c = candidate("JUSTSTARTED");
            c.setStartAt(Instant.now().minusSeconds(1));
            when(couponRepository.findAllByTargetAndActiveTrueOrderByValueDesc(CouponTarget.SUBTOTAL))
                    .thenReturn(List.of(c));

            assertThat(service.listAvailable(CouponTarget.SUBTOTAL, OWNER_UUID)).containsExactly(c);
        }

        @Test
        void excludesAnExpiredCoupon() {
            Coupon c = candidate("EXPIRED10");
            c.setEndAt(Instant.now().minus(1, ChronoUnit.DAYS));
            when(couponRepository.findAllByTargetAndActiveTrueOrderByValueDesc(CouponTarget.SUBTOTAL))
                    .thenReturn(List.of(c));

            assertThat(service.listAvailable(CouponTarget.SUBTOTAL, OWNER_UUID)).isEmpty();
        }

        @Test
        void excludesACouponAtItsGlobalRedemptionLimit() {
            Coupon c = candidate("CAPPED");
            c.setMaxRedemptions(3);
            when(couponRepository.findAllByTargetAndActiveTrueOrderByValueDesc(CouponTarget.SUBTOTAL))
                    .thenReturn(List.of(c));
            when(couponRedemptionRepository.countByCouponId(2)).thenReturn(3L);

            assertThat(service.listAvailable(CouponTarget.SUBTOTAL, OWNER_UUID)).isEmpty();
        }

        @Test
        void includesACouponBelowItsGlobalRedemptionLimit() {
            Coupon c = candidate("CAPPED");
            c.setMaxRedemptions(3);
            when(couponRepository.findAllByTargetAndActiveTrueOrderByValueDesc(CouponTarget.SUBTOTAL))
                    .thenReturn(List.of(c));
            when(couponRedemptionRepository.countByCouponId(2)).thenReturn(2L);

            assertThat(service.listAvailable(CouponTarget.SUBTOTAL, OWNER_UUID)).containsExactly(c);
        }

        @Test
        void excludesACouponTheCallerHasAlreadyExhaustedTheirOwnLimitOn() {
            Coupon c = candidate("ONEPERUSER");
            c.setMaxRedemptionsPerUser(1);
            when(couponRepository.findAllByTargetAndActiveTrueOrderByValueDesc(CouponTarget.SUBTOTAL))
                    .thenReturn(List.of(c));
            when(couponRedemptionRepository.countByCouponIdAndOwnerUuid(2, OWNER_UUID)).thenReturn(1L);

            assertThat(service.listAvailable(CouponTarget.SUBTOTAL, OWNER_UUID)).isEmpty();
        }

        @Test
        void queriesTheRepositoryByTheRequestedTarget() {
            when(couponRepository.findAllByTargetAndActiveTrueOrderByValueDesc(CouponTarget.SHIPPING_FEE))
                    .thenReturn(List.of());

            service.listAvailable(CouponTarget.SHIPPING_FEE, OWNER_UUID);

            verify(couponRepository).findAllByTargetAndActiveTrueOrderByValueDesc(CouponTarget.SHIPPING_FEE);
        }
    }
}
