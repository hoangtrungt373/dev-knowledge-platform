package com.ttg.devknowledgeplatform.devpractice.repository.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;

import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.entity.ProblemTagAssignment;
import com.ttg.devknowledgeplatform.devpractice.enums.Difficulty;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Dynamic filtering for {@link Problem} listings — mirrors {@code content-service}'s
 * {@code ArticleSpecification} shape (a static builder combining only the predicates the caller
 * actually supplied).
 */
public final class ProblemSpecification {

    private ProblemSpecification() {
    }

    /**
     * @param tagIds optional — matches a problem tagged with <em>any</em> of these ids (OR, like
     *               {@code ecommerce-service}'s product tag filter). Expressed as an EXISTS subquery
     *               rather than a join: a join yields one row per matching assignment, so a problem
     *               with two requested tags would appear twice and need {@code query.distinct(true)},
     *               which then also has to apply to the count query and constrains ORDER BY.
     *               EXISTS never duplicates a row in the first place.
     */
    public static Specification<Problem> withFilters(
            Difficulty difficulty, ContentStatus status, String q, Set<Integer> tagIds) {
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
            if (tagIds != null && !tagIds.isEmpty()) {
                Subquery<Integer> tagged = query.subquery(Integer.class);
                Root<ProblemTagAssignment> assignment = tagged.from(ProblemTagAssignment.class);
                tagged.select(assignment.get("id")).where(
                        cb.equal(assignment.get("problem"), root),
                        assignment.get("problemTag").get("id").in(tagIds));
                predicates.add(cb.exists(tagged));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
