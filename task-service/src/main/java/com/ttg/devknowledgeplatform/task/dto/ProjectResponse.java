package com.ttg.devknowledgeplatform.task.dto;

import com.ttg.devknowledgeplatform.task.enums.ProjectStatus;

import java.time.Instant;

/**
 * REST response for a {@link com.ttg.devknowledgeplatform.task.entity.Project}.
 */
public record ProjectResponse(
        Integer id,
        String name,
        String description,
        ProjectStatus status,
        Instant createdAt) {
}
