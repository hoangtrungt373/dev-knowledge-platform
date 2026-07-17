package com.ttg.devknowledgeplatform.task.service;

import com.ttg.devknowledgeplatform.task.enums.TaskPriority;
import com.ttg.devknowledgeplatform.task.enums.TaskStatus;

import java.time.Instant;

/**
 * Optional narrowing filters for {@link TaskService#listTasks}. Every field is nullable — a
 * {@code null} field means "don't filter on this". The caller's own tasks are always the scope;
 * this record carries no owner id since that's a separate, non-optional parameter.
 */
public record TaskFilter(
        Integer projectId,
        TaskStatus status,
        TaskPriority priority,
        Instant dueBefore,
        Instant dueAfter) {
}
