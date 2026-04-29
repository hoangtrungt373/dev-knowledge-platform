package com.ttg.devknowledgeplatform.repository.spec;

import com.ttg.devknowledgeplatform.common.entity.Category;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CategorySpecification {

    private CategorySpecification() {}

    public static Specification<Category> withFilters(Integer parentId, Boolean rootOnly, String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (Boolean.TRUE.equals(rootOnly)) {
                predicates.add(cb.isNull(root.get("parent")));
            } else if (parentId != null) {
                Join<Category, Category> parent = root.join("parent", JoinType.INNER);
                predicates.add(cb.equal(parent.get("id"), parentId));
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
