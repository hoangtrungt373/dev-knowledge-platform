package com.ttg.devknowledgeplatform.ecommerce.repository;

import com.ttg.devknowledgeplatform.ecommerce.entity.CouponRedemption;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Integer> {

    /** Backs {@code CouponServiceImpl.delete}'s {@code COUPON_IN_USE} guard — a coupon that has
     * ever been redeemed can't be deleted, mirroring {@code ProductTagAssignmentRepository.existsByProductTagId}'s
     * own in-use guard. */
    boolean existsByCouponId(Integer couponId);

    /** Backs {@code Coupon.maxRedemptions} enforcement in {@code CouponRedemptionServiceImpl#resolve}
     * (Phase 2) — a single coupon's own redemption-time check, not a candidate-list scan, so there's
     * no N+1 concern here the way {@code #countGroupedByCouponId} exists to fix for
     * {@code #listAvailable}. */
    long countByCouponId(Integer couponId);

    /** Backs {@code Coupon.maxRedemptionsPerUser} enforcement in {@code CouponRedemptionServiceImpl
     * #resolve} (Phase 2) — same one-coupon-at-a-time shape as {@link #countByCouponId}. */
    long countByCouponIdAndOwnerUuid(Integer couponId, String ownerUuid);

    /**
     * Batch-resolves every coupon in {@code couponIds}' own global redemption count in one grouped
     * query — written for {@code CouponRedemptionServiceImpl#listAvailable}, which used to call
     * {@link #countByCouponId} once per candidate coupon (an N+1 query pattern hit every time the
     * shopper opens the coupon-picker dialog); a coupon with zero redemptions simply has no row in
     * the result, which the caller must treat as a count of zero.
     */
    @Query("SELECT r.coupon.id AS couponId, COUNT(r) AS total FROM CouponRedemption r "
            + "WHERE r.coupon.id IN :couponIds GROUP BY r.coupon.id")
    List<CouponRedemptionCount> countGroupedByCouponId(@Param("couponIds") Collection<Integer> couponIds);

    /** Same batching fix as {@link #countGroupedByCouponId}, for the per-user redemption count. */
    @Query("SELECT r.coupon.id AS couponId, COUNT(r) AS total FROM CouponRedemption r "
            + "WHERE r.coupon.id IN :couponIds AND r.ownerUuid = :ownerUuid GROUP BY r.coupon.id")
    List<CouponRedemptionCount> countGroupedByCouponIdForOwner(
            @Param("couponIds") Collection<Integer> couponIds, @Param("ownerUuid") String ownerUuid);
}
