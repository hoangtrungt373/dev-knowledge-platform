package com.ttg.devknowledgeplatform.ecommerce.repository;

import com.ttg.devknowledgeplatform.ecommerce.entity.Coupon;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponTarget;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Integer>, JpaSpecificationExecutor<Coupon> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Integer id);

    /** Looked up by checkout (Phase 2) when a shopper enters a code — {@code code} is always the
     * normalized-uppercase form persisted at create time, so this is a plain equality lookup, not
     * a case-insensitive one. */
    Optional<Coupon> findByCode(String code);

    /** Candidate rows for the shopper-facing coupon picker ({@code CouponRedemptionService
     * #listAvailable}) — active/target are the only two conditions cheap enough to push into the
     * query itself; date-range and redemption-limit filtering happen afterward in Java, mirroring
     * {@code resolve}'s own per-coupon checks (see that method's Javadoc). Biggest discount first,
     * a reasonable default for a picker with no other sort the GUI has asked for yet. */
    List<Coupon> findAllByTargetAndActiveTrueOrderByValueDesc(CouponTarget target);
}
