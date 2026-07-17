package com.ttg.devknowledgeplatform.task.dto;

import com.ttg.devknowledgeplatform.task.enums.TaskPriority;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

import java.time.Instant;

@Data
public class CreateTaskRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    private String description;

    private Integer projectId;

    private TaskPriority priority = TaskPriority.MEDIUM;

    private Instant dueDate;

    private Integer contentItemId;
}
