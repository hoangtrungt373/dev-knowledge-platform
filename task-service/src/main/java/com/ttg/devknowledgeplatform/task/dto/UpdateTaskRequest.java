package com.ttg.devknowledgeplatform.task.dto;

import com.ttg.devknowledgeplatform.task.enums.TaskPriority;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

import java.time.Instant;

/**
 * Fully replaces a task's mutable fields — a {@code null} {@code projectId}/{@code contentItemId}
 * means "no project"/"no content link", not "leave unchanged" (same semantics as
 * {@code TaskCommands.Update}).
 */
@Data
public class UpdateTaskRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    private String description;

    private Integer projectId;

    private TaskPriority priority = TaskPriority.MEDIUM;

    private Instant dueDate;

    private Integer contentItemId;
}
