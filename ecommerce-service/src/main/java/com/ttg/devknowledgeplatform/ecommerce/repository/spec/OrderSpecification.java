package com.ttg.devknowledgeplatform.ecommerce.repository.spec;

import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Dynamic filtering for the admin order-fulfillment queue (US-3.7/3.8) and the shopper-facing
 * order-history status tabs (post-Epic-3 follow-up) — same shape as
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

    /**
     * A shopper's own orders (always filtered to {@code ownerUuid}), optionally narrowed to one of
     * several statuses — the GUI's grouped status tabs (e.g. "To Pay" = {@code PENDING} +
     * {@code PAYMENT_PROCESSING}) need an {@code IN} filter, not {@link #withFilters}'s single-value
     * equality, since one tab can map to more than one raw {@link OrderStatus}.
     *
     * @param ownerUuid the caller's Keycloak UUID — always applied, never optional
     * @param statuses  optional status set; {@code null}/empty matches every status ("All")
     */
    public static Specification<Order> withOwnerAndStatuses(String ownerUuid, Collection<OrderStatus> statuses) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("ownerUuid"), ownerUuid));

            if (statuses != null && !statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
