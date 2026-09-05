package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
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
import static org.mockito.Mockito.never;
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

        /** A minimal {@link CouponRedemptionCount} row — the grouped batch query's own return shape. */
        private CouponRedemptionCount count(Integer couponId, long total) {
            return new CouponRedemptionCount() {
                @Override
                public Integer getCouponId() {
                    return couponId;
                }

                @Override
                public Long getTotal() {
                    return total;
                }
            };
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
            when(couponRedemptionRepository.countGroupedByCouponId(List.of(2))).thenReturn(List.of(count(2, 3L)));

            assertThat(service.listAvailable(CouponTarget.SUBTOTAL, OWNER_UUID)).isEmpty();
        }

        @Test
        void includesACouponBelowItsGlobalRedemptionLimit() {
            Coupon c = candidate("CAPPED");
            c.setMaxRedemptions(3);
            when(couponRepository.findAllByTargetAndActiveTrueOrderByValueDesc(CouponTarget.SUBTOTAL))
                    .thenReturn(List.of(c));
            when(couponRedemptionRepository.countGroupedByCouponId(List.of(2))).thenReturn(List.of(count(2, 2L)));

            assertThat(service.listAvailable(CouponTarget.SUBTOTAL, OWNER_UUID)).containsExactly(c);
        }

        @Test
        void treatsACouponMissingFromTheGroupedCountResultAsZeroRedemptions() {
            // A coupon with no redemptions at all has no GROUP BY row — must not be misread as "at
            // its limit" (or throw) just because the grouped query has nothing to say about it.
            Coupon c = candidate("NEVERREDEEMED");
            c.setMaxRedemptions(3);
            when(couponRepository.findAllByTargetAndActiveTrueOrderByValueDesc(CouponTarget.SUBTOTAL))
                    .thenReturn(List.of(c));
            when(couponRedemptionRepository.countGroupedByCouponId(List.of(2))).thenReturn(List.of());

            assertThat(service.listAvailable(CouponTarget.SUBTOTAL, OWNER_UUID)).containsExactly(c);
        }

        @Test
        void excludesACouponTheCallerHasAlreadyExhaustedTheirOwnLimitOn() {
            Coupon c = candidate("ONEPERUSER");
            c.setMaxRedemptionsPerUser(1);
            when(couponRepository.findAllByTargetAndActiveTrueOrderByValueDesc(CouponTarget.SUBTOTAL))
                    .thenReturn(List.of(c));
            when(couponRedemptionRepository.countGroupedByCouponIdForOwner(List.of(2), OWNER_UUID))
                    .thenReturn(List.of(count(2, 1L)));

            assertThat(service.listAvailable(CouponTarget.SUBTOTAL, OWNER_UUID)).isEmpty();
        }

        @Test
        void neverCallsEitherGroupedCountQueryWhenNoCandidateHasAnyRedemptionLimit() {
            // Regression coverage for the fix's own "zero limits configured -> zero count queries"
            // guarantee — preserves the original short-circuit behavior, not just the N+1 fix.
            when(couponRepository.findAllByTargetAndActiveTrueOrderByValueDesc(CouponTarget.SUBTOTAL))
                    .thenReturn(List.of(candidate("PLAIN1"), candidate("PLAIN2")));

            assertThat(service.listAvailable(CouponTarget.SUBTOTAL, OWNER_UUID)).hasSize(2);
            verify(couponRedemptionRepository, never()).countGroupedByCouponId(any());
            verify(couponRedemptionRepository, never()).countGroupedByCouponIdForOwner(any(), any());
        }

        @Test
        void batchesBothRedemptionCountLookupsIntoExactlyOneCallEachRegardlessOfCandidateCount() {
            // The actual N+1 fix: N candidate coupons, each with both limits set, must still cost
            // exactly one countGroupedByCouponId call and one countGroupedByCouponIdForOwner call.
            Coupon first = candidate("FIRST");
            first.setId(10);
            first.setMaxRedemptions(5);
            first.setMaxRedemptionsPerUser(1);
            Coupon second = candidate("SECOND");
            second.setId(11);
            second.setMaxRedemptions(5);
            second.setMaxRedemptionsPerUser(1);
            when(couponRepository.findAllByTargetAndActiveTrueOrderByValueDesc(CouponTarget.SUBTOTAL))
                    .thenReturn(List.of(first, second));
            when(couponRedemptionRepository.countGroupedByCouponId(List.of(10, 11)))
                    .thenReturn(List.of(count(10, 1L), count(11, 1L)));
            when(couponRedemptionRepository.countGroupedByCouponIdForOwner(List.of(10, 11), OWNER_UUID))
                    .thenReturn(List.of());

            assertThat(service.listAvailable(CouponTarget.SUBTOTAL, OWNER_UUID)).containsExactly(first, second);
            verify(couponRedemptionRepository).countGroupedByCouponId(List.of(10, 11));
            verify(couponRedemptionRepository).countGroupedByCouponIdForOwner(List.of(10, 11), OWNER_UUID);
        }

        @Test
        void queriesTheRepositoryByTheRequestedTarget() {
            when(couponRepository.findAllByTargetAndActiveTrueOrderByValueDesc(CouponTarget.SHIPPING_FEE))
                    .thenReturn(List.of());

            service.listAvailable(CouponTarget.SHIPPING_FEE, OWNER_UUID);

            verify(couponRepository).findAllByTargetAndActiveTrueOrderByValueDesc(CouponTarget.SHIPPING_FEE);
        }
    }

    @Nested
    class ListAvailableRanked {

        private Coupon coupon(String code, CouponType type, String value) {
            Coupon c = new Coupon();
            c.setId(3);
            c.setCode(code);
            c.setTarget(CouponTarget.SUBTOTAL);
            c.setType(type);
            c.setValue(new BigDecimal(value));
            c.setActive(true);
            return c;
        }

        @Test
        void ranksByRealDiscountAmountNotByDeclaredValue() {
            // BIGVALUE declares "50%" but is capped at $5 off; SMALLVALUE is a plain "$10 off" —
            // SMALLVALUE actually saves more on this $100 order, so it must rank first despite its
            // smaller declared value.
            Coupon bigValue = coupon("BIGVALUE", CouponType.PERCENTAGE, "50");
            bigValue.setMaxDiscountAmount(new BigDecimal("5.00"));
            Coupon smallValue = coupon("SMALLVALUE", CouponType.FIXED_AMOUNT, "10.00");
            when(couponRepository.findAllByTargetAndActiveTrueOrderByValueDesc(CouponTarget.SUBTOTAL))
                    .thenReturn(List.of(bigValue, smallValue));

            List<CouponRedemptionService.RankedCoupon> result = service.listAvailableRanked(
                    CouponTarget.SUBTOTAL, OWNER_UUID, new BigDecimal("100.00"), new BigDecimal("100.00"));

            assertThat(result).extracting(CouponRedemptionService.RankedCoupon::coupon)
                    .containsExactly(smallValue, bigValue);
            assertThat(result.get(0).discountAmount()).isEqualByComparingTo("10.00");
            assertThat(result.get(1).discountAmount()).isEqualByComparingTo("5.00");
        }

        @Test
        void alwaysRanksEligibleCouponsBeforeIneligibleOnesRegardlessOfDiscountSize() {
            Coupon ineligibleButBigger = coupon("LOCKED20", CouponType.FIXED_AMOUNT, "20.00");
            ineligibleButBigger.setMinSubtotal(new BigDecimal("200.00"));
            Coupon eligibleButSmaller = coupon("UNLOCKED5", CouponType.FIXED_AMOUNT, "5.00");
            when(couponRepository.findAllByTargetAndActiveTrueOrderByValueDesc(CouponTarget.SUBTOTAL))
                    .thenReturn(List.of(ineligibleButBigger, eligibleButSmaller));

            List<CouponRedemptionService.RankedCoupon> result = service.listAvailableRanked(
                    CouponTarget.SUBTOTAL, OWNER_UUID, new BigDecimal("50.00"), new BigDecimal("50.00"));

            assertThat(result).extracting(CouponRedemptionService.RankedCoupon::coupon)
                    .containsExactly(eligibleButSmaller, ineligibleButBigger);
            assertThat(result.get(0).eligible()).isTrue();
            assertThat(result.get(1).eligible()).isFalse();
        }

        @Test
        void computesEligibleAgainstTheGivenSubtotalIndependentlyOfBaseAmount() {
            // A SHIPPING_FEE coupon: minSubtotal is always checked against subtotal, never the
            // shipping-fee baseAmount its own discount is computed against (see resolve()'s own
            // Javadoc for the identical rule at redemption time).
            Coupon c = coupon("FREESHIP", CouponType.FIXED_AMOUNT, "5.00");
            c.setTarget(CouponTarget.SHIPPING_FEE);
            c.setMinSubtotal(new BigDecimal("30.00"));
            when(couponRepository.findAllByTargetAndActiveTrueOrderByValueDesc(CouponTarget.SHIPPING_FEE))
                    .thenReturn(List.of(c));

            List<CouponRedemptionService.RankedCoupon> result = service.listAvailableRanked(
                    CouponTarget.SHIPPING_FEE, OWNER_UUID, new BigDecimal("35.00"), new BigDecimal("5.00"));

            assertThat(result.get(0).eligible()).isTrue();
            assertThat(result.get(0).discountAmount()).isEqualByComparingTo("5.00");
        }
    }
}
