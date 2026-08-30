package com.ttg.devknowledgeplatform.ecommerce.repository.spec;

import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic filtering for the admin order-fulfillment queue (US-3.7/3.8) — same shape as
 * {@code ProductCategorySpecification}/{@code ProductSpecification}, this module's established
 * Specification-pattern convention, even for a single optional filter.
 */
public class OrderSpecification {

    private OrderSpecification() {
    }

    public static Specification<Order> withFilters(OrderStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
