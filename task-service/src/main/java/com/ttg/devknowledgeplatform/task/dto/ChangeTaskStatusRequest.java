package com.ttg.devknowledgeplatform.task.dto;

import com.ttg.devknowledgeplatform.task.enums.TaskStatus;

import jakarta.validation.constraints.NotNull;

import lombok.Data;

@Data
public class ChangeTaskStatusRequest {

    @NotNull(message = "Status is required")
    private TaskStatus status;
}
