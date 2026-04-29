package com.ttg.devknowledgeplatform.repository.spec;

import com.ttg.devknowledgeplatform.common.entity.ContentItem;
import com.ttg.devknowledgeplatform.common.entity.InterviewQuestion;
import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.InterviewQuestionDifficulty;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class InterviewQuestionSpecification {

    private InterviewQuestionSpecification() {}

    public static Specification<InterviewQuestion> withFilters(
            InterviewQuestionDifficulty difficulty,
            ContentStatus status,
            Boolean isCommon,
            String q) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Use fetch join on data queries, plain join on count queries to avoid JPA errors
            Join<InterviewQuestion, ContentItem> contentItem;
            if (Long.class != query.getResultType()) {
                Fetch<InterviewQuestion, ContentItem> fetch = root.fetch("contentItem", JoinType.INNER);
                contentItem = (Join<InterviewQuestion, ContentItem>) fetch;
            } else {
                contentItem = root.join("contentItem", JoinType.INNER);
            }

            if (difficulty != null) {
                predicates.add(cb.equal(root.get("difficulty"), difficulty));
            }
            if (status != null) {
                predicates.add(cb.equal(contentItem.get("status"), status));
            }
            if (isCommon != null) {
                predicates.add(cb.equal(root.get("isCommon"), isCommon));
            }
            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(contentItem.get("title")), pattern),
                        cb.like(cb.lower(root.get("questionBody")), pattern)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}