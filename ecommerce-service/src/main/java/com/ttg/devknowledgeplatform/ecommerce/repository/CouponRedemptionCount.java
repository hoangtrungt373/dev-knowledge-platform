package com.ttg.devknowledgeplatform.ecommerce.repository;

/**
 * A Spring Data JPA interface projection for one coupon's redemption count — backs
 * {@code CouponRedemptionRepository#countGroupedByCouponId}/{@code #countGroupedByCouponIdForOwner},
 * which batch-resolve every candidate coupon's count in one grouped query instead of one
 * {@code COUNT} query per coupon (the N+1 pattern {@code CouponRedemptionServiceImpl#listAvailable}
 * used to have — see that method's own updated Javadoc for the incident).
 */
public interface CouponRedemptionCount {

    Integer getCouponId();

    Long getTotal();
}
