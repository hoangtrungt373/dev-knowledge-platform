package com.ttg.devknowledgeplatform.ecommerce.repository.spec;

import com.ttg.devknowledgeplatform.ecommerce.entity.Product;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProductSpecification {

    private ProductSpecification() {}

    public static Specification<Product> withFilters(Integer productCategoryId, Boolean active, String q) {
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

            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
