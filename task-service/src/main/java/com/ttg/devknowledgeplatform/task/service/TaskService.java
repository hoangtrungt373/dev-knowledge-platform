package com.ttg.devknowledgeplatform.task.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.ttg.devknowledgeplatform.task.entity.Task;
import com.ttg.devknowledgeplatform.task.enums.TaskStatus;

/**
 * Owns {@link Task} CRUD and status transitions. MVP is single-user: every method is scoped to a
 * caller-supplied {@code ownerId}, and a task belonging to a different owner is treated
 * identically to a nonexistent one (see {@code TaskErrorCode.TASK_NOT_FOUND}'s Javadoc).
 *
 * <p>Returns entities rather than REST DTOs — a future {@code TaskMapper} does the
 * entity-to-response mapping, same split as {@code social-service}'s {@code FriendService}.
 */
public interface TaskService {

    /**
     * Creates a new task owned by {@code ownerId}. {@code command.projectId()} (if not
     * {@code null}) must reference a project owned by the same user; {@code command.contentItemId()}
     * (if not {@code null}) must reference an existing {@code content-service} content item.
     */
    Task createTask(Integer ownerId, TaskCommands.Create command);

    /** Fetches a task, verifying it's owned by {@code ownerId}. */
    Task getTask(Integer ownerId, Integer taskId);

    /** Lists {@code ownerId}'s tasks, narrowed by {@code filter} (every field optional). */
    Page<Task> listTasks(Integer ownerId, TaskFilter filter, Pageable pageable);

    /** Replaces a task's fields — see {@link TaskCommands.Update}'s Javadoc for replace semantics. */
    Task updateTask(Integer ownerId, Integer taskId, TaskCommands.Update command);

    /**
     * Moves a task to {@code newStatus}. Only rejects a no-op transition (see
     * {@code TaskStatus.canTransitionTo}) — otherwise any status may move to any other.
     */
    Task changeStatus(Integer ownerId, Integer taskId, TaskStatus newStatus);

    /** Deletes a task. */
    void deleteTask(Integer ownerId, Integer taskId);
}
