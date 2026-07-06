package com.ttg.devknowledgeplatform.social.repository.spec;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.jpa.domain.Specification;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.social.entity.UserBlock;

public class UserSpecification {

    private UserSpecification() {}

    /**
     * Excludes the viewer, and any user blocked in either direction relative to the viewer.
     * When {@code q} looks like an email address it is matched exactly (prevents scraping the
     * user directory by partial email); otherwise it's matched fuzzily against username/name.
     */
    public static Specification<User> search(String q, Integer viewerId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.notEqual(root.get("id"), viewerId));

            if (q != null && !q.isBlank()) {
                String trimmed = q.trim();
                if (trimmed.contains("@")) {
                    predicates.add(cb.equal(cb.lower(root.get("email")), trimmed.toLowerCase()));
                } else {
                    String pattern = "%" + trimmed.toLowerCase() + "%";
                    predicates.add(cb.or(
                            cb.like(cb.lower(root.get("username")), pattern),
                            cb.like(cb.lower(root.get("firstName")), pattern),
                            cb.like(cb.lower(root.get("lastName")), pattern)
                    ));
                }
            }

            predicates.add(cb.not(cb.exists(notBlockedEitherDirection(root, query, cb, viewerId))));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Subquery<Integer> notBlockedEitherDirection(
            Root<User> root, CriteriaQuery<?> query, CriteriaBuilder cb, Integer viewerId) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<UserBlock> blockRoot = subquery.from(UserBlock.class);
        subquery.select(cb.literal(1));
        subquery.where(cb.or(
                cb.and(cb.equal(blockRoot.get("blocker").get("id"), viewerId), cb.equal(blockRoot.get("blocked"), root)),
                cb.and(cb.equal(blockRoot.get("blocked").get("id"), viewerId), cb.equal(blockRoot.get("blocker"), root))
        ));
        return subquery;
    }
}
