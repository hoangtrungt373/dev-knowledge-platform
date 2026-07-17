package com.ttg.devknowledgeplatform.task.service;

import com.ttg.devknowledgeplatform.task.enums.TaskPriority;

import java.time.Instant;

/**
 * Plain input records for {@link TaskService}, without any REST/validation concerns — those
 * belong to the REST layer, which translates a request DTO into one of these before calling the
 * service. Mirrors {@code content-service}'s {@code ArticleCommands}.
 *
 * <p>{@code Update} fully replaces {@code projectId}/{@code contentItemId}: a {@code null} value
 * means "no project"/"no content link", not "leave unchanged".
 */
public final class TaskCommands {

    private TaskCommands() {}

    public record Create(
            String title,
            String description,
            Integer projectId,
            TaskPriority priority,
            Instant dueDate,
            Integer contentItemId) {
    }

    public record Update(
            String title,
            String description,
            Integer projectId,
            TaskPriority priority,
            Instant dueDate,
            Integer contentItemId) {
    }
}
