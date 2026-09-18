package com.ttg.devknowledgeplatform.devpractice.repository.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.enums.Difficulty;

import jakarta.persistence.criteria.Predicate;

/**
 * Dynamic filtering for {@link Problem} listings — mirrors {@code content-service}'s
 * {@code ArticleSpecification} shape (a static builder combining only the predicates the caller
 * actually supplied).
 */
public final class ProblemSpecification {

    private ProblemSpecification() {
    }

    public static Specification<Problem> withFilters(Difficulty difficulty, ContentStatus status, String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (difficulty != null) {
                predicates.add(cb.equal(root.get("difficulty"), difficulty));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (q != null && !q.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("title")), "%" + q.toLowerCase(Locale.ROOT) + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
