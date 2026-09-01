package com.ttg.devknowledgeplatform.ecommerce.repository;

import com.ttg.devknowledgeplatform.ecommerce.entity.Coupon;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Integer>, JpaSpecificationExecutor<Coupon> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Integer id);

    /** Looked up by checkout (Phase 2) when a shopper enters a code — {@code code} is always the
     * normalized-uppercase form persisted at create time, so this is a plain equality lookup, not
     * a case-insensitive one. */
    Optional<Coupon> findByCode(String code);
}
