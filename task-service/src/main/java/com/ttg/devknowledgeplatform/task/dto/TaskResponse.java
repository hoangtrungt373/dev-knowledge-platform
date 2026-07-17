package com.ttg.devknowledgeplatform.task.dto;

import com.ttg.devknowledgeplatform.task.enums.TaskPriority;
import com.ttg.devknowledgeplatform.task.enums.TaskStatus;

import java.time.Instant;

/**
 * REST response for a {@link com.ttg.devknowledgeplatform.task.entity.Task}. {@code projectId}/
 * {@code contentItemId} are flat references, not nested objects — a client that needs full
 * project/content details already has the dedicated endpoint for that resource.
 */
public record TaskResponse(
        Integer id,
        Integer projectId,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        Instant dueDate,
        Integer contentItemId,
        Instant createdAt) {
}
