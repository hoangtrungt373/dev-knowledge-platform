package com.ttg.devknowledgeplatform.content.repository.spec;

import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.ContentType;
import com.ttg.devknowledgeplatform.content.entity.ContentItem;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Filter builder for {@code ContentItem} itself (as opposed to {@code ArticleSpecification}/
 * {@code QuestionAnswerSpecification}, which filter through a join onto {@code ContentItem}) —
 * backs the internal indexing API's paginated listing, which {@code ai-service}'s admin
 * embedding-index screen queries directly by title/type/status/id.
 */
public class ContentItemSpecification {

    private ContentItemSpecification() {}

    public static Specification<ContentItem> withFilters(
            String q, ContentType type, ContentStatus status, List<Integer> ids, List<Integer> excludeIds) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("title")), pattern));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (ids != null && !ids.isEmpty()) {
                predicates.add(root.get("id").in(ids));
            }
            if (excludeIds != null && !excludeIds.isEmpty()) {
                predicates.add(cb.not(root.get("id").in(excludeIds)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
