package com.ttg.devknowledgeplatform.ecommerce.repository.spec;

import com.ttg.devknowledgeplatform.ecommerce.entity.Coupon;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponTarget;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CouponSpecification {

    private CouponSpecification() {}

    public static Specification<Coupon> withFilters(String q, Boolean active, CouponTarget target) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("code")), "%" + q.toLowerCase(Locale.ROOT) + "%"));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (target != null) {
                predicates.add(cb.equal(root.get("target"), target));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
