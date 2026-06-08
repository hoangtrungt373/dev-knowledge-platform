package com.ttg.devknowledgeplatform.repository.spec;

import com.ttg.devknowledgeplatform.common.entity.Article;
import com.ttg.devknowledgeplatform.common.entity.ContentItem;
import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.ContentType;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class ArticleSpecification {

    private ArticleSpecification() {}

    public static Specification<Article> withFilters(
            ContentType type,
            ContentStatus status,
            String q) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            Join<Article, ContentItem> contentItem;
            if (Long.class != query.getResultType()) {
                Fetch<Article, ContentItem> fetch = root.fetch("contentItem", JoinType.INNER);
                contentItem = (Join<Article, ContentItem>) fetch;
            } else {
                contentItem = root.join("contentItem", JoinType.INNER);
            }

            // Articles are always ARTICLE or BLOG_POST; restrict by specific type if provided
            if (type != null) {
                predicates.add(cb.equal(contentItem.get("type"), type));
            } else {
                predicates.add(contentItem.get("type").in(ContentType.ARTICLE, ContentType.BLOG_POST));
            }

            if (status != null) {
                predicates.add(cb.equal(contentItem.get("status"), status));
            }
            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(contentItem.get("title")), pattern),
                        cb.like(cb.lower(root.get("body")), pattern)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
