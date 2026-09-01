package com.ttg.devknowledgeplatform.ecommerce.repository;

import com.ttg.devknowledgeplatform.ecommerce.entity.CouponRedemption;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Integer> {

    /** Backs {@code CouponServiceImpl.delete}'s {@code COUPON_IN_USE} guard — a coupon that has
     * ever been redeemed can't be deleted, mirroring {@code ProductTagAssignmentRepository.existsByProductTagId}'s
     * own in-use guard. */
    boolean existsByCouponId(Integer couponId);

    /** Backs {@code Coupon.maxRedemptions} enforcement (Phase 2). */
    long countByCouponId(Integer couponId);

    /** Backs {@code Coupon.maxRedemptionsPerUser} enforcement (Phase 2). */
    long countByCouponIdAndOwnerUuid(Integer couponId, String ownerUuid);
}
