package com.ttg.devknowledgeplatform.task.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.ttg.devknowledgeplatform.task.entity.Project;

/**
 * Owns {@link Project} CRUD. MVP is single-user: every method is scoped to a caller-supplied
 * {@code ownerUuid}, and a project belonging to a different owner is treated identically to a
 * nonexistent one (see {@code TaskErrorCode.PROJECT_NOT_FOUND}'s Javadoc).
 *
 * <p>Returns entities rather than REST DTOs — a future {@code ProjectMapper} does the
 * entity-to-response mapping, same split as {@code social-service}'s {@code FriendService}.
 */
public interface ProjectService {

    /** Creates a new project owned by {@code ownerUuid}. */
    Project createProject(String ownerUuid, ProjectCommands.Create command);

    /** Fetches a project, verifying it's owned by {@code ownerUuid}. */
    Project getProject(String ownerUuid, Integer projectId);

    /** Lists every project owned by {@code ownerUuid}. */
    Page<Project> listProjects(String ownerUuid, Pageable pageable);

    /** Updates a project's name/description. */
    Project updateProject(String ownerUuid, Integer projectId, ProjectCommands.Update command);

    /** Marks a project {@code ARCHIVED}. */
    Project archiveProject(String ownerUuid, Integer projectId);
}
