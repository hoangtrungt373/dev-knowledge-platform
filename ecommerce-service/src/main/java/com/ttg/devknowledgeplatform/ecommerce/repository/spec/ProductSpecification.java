package com.ttg.devknowledgeplatform.ecommerce.repository.spec;

import com.ttg.devknowledgeplatform.ecommerce.entity.Product;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductTagAssignment;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ProductSpecification {

    private ProductSpecification() {}

    public static Specification<Product> withFilters(Integer productCategoryId, Boolean active, String q) {
        return withFilters(productCategoryId, active, q, null);
    }

    /**
     * @param tagIds optional filter — matches a product tagged with *any* of the given ids
     *               (an OR match, not "must have all of them"). {@code null}/empty means no filter.
     *               Joins to {@code ProductTagAssignment}, so {@code query.distinct(true)} is
     *               required to avoid a duplicate row per matching assignment on a product tagged
     *               with more than one of the requested ids.
     */
    public static Specification<Product> withFilters(Integer productCategoryId, Boolean active, String q, Set<Integer> tagIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (productCategoryId != null) {
                predicates.add(cb.equal(root.get("productCategory").get("id"), productCategoryId));
            }

            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }

            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("slug")), pattern)
                ));
            }

            if (tagIds != null && !tagIds.isEmpty()) {
                Join<Product, ProductTagAssignment> assignments = root.join("productTagAssignments");
                predicates.add(assignments.get("productTag").get("id").in(tagIds));
                query.distinct(true);
            }

            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
