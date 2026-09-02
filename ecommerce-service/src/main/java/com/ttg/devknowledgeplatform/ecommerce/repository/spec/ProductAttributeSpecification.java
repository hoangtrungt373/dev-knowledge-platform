package com.ttg.devknowledgeplatform.ecommerce.repository.spec;

import com.ttg.devknowledgeplatform.ecommerce.entity.ProductAttribute;

import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

public class ProductAttributeSpecification {

    private ProductAttributeSpecification() {}

    public static Specification<ProductAttribute> withFilters(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) {
                return cb.conjunction();
            }
            String pattern = "%" + q.toLowerCase(Locale.ROOT) + "%";
            return cb.like(cb.lower(root.get("name")), pattern);
        };
    }
}
