package com.ttg.devknowledgeplatform.task.repository.spec;

import com.ttg.devknowledgeplatform.task.entity.Task;
import com.ttg.devknowledgeplatform.task.enums.TaskPriority;
import com.ttg.devknowledgeplatform.task.enums.TaskStatus;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic filtering for {@link Task} lists — every list query is scoped to a single owner, with
 * optional narrowing by project, status, priority, and due-date range.
 */
public class TaskSpecification {

    private TaskSpecification() {}

    public static Specification<Task> withFilters(
            Integer ownerId,
            Integer projectId,
            TaskStatus status,
            TaskPriority priority,
            Instant dueBefore,
            Instant dueAfter) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("owner").get("id"), ownerId));

            if (projectId != null) {
                predicates.add(cb.equal(root.get("project").get("id"), projectId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (dueBefore != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dueDate"), dueBefore));
            }
            if (dueAfter != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dueDate"), dueAfter));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
