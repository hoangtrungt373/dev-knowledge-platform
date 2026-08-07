package com.ttg.devknowledgeplatform.task.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.ttg.devknowledgeplatform.task.entity.Task;
import com.ttg.devknowledgeplatform.task.enums.TaskStatus;

import java.util.List;

/**
 * Owns {@link Task} CRUD and status transitions. MVP is single-user: every method is scoped to a
 * caller-supplied {@code ownerUuid}, and a task belonging to a different owner is treated
 * identically to a nonexistent one (see {@code TaskErrorCode.TASK_NOT_FOUND}'s Javadoc).
 *
 * <p>Returns entities rather than REST DTOs — a future {@code TaskMapper} does the
 * entity-to-response mapping, same split as {@code social-service}'s {@code FriendService}.
 */
public interface TaskService {

    /**
     * Creates a new task owned by {@code ownerUuid}. {@code command.projectId()} (if not
     * {@code null}) must reference a project owned by the same user; {@code command.parentTaskId()}
     * (if not {@code null}) must reference a top-level task owned by the same user (subtask
     * nesting is capped at one level).
     */
    Task createTask(String ownerUuid, TaskCommands.Create command);

    /** Fetches a task, verifying it's owned by {@code ownerUuid}. */
    Task getTask(String ownerUuid, Integer taskId);

    /**
     * Lists {@code ownerUuid}'s top-level tasks, narrowed by {@code filter} (every field optional).
     * Subtasks never appear here — fetch a task's subtasks via {@link #listSubtasks}.
     */
    Page<Task> listTasks(String ownerUuid, TaskFilter filter, Pageable pageable);

    /**
     * Lists {@code parentTaskId}'s subtasks (ownership-checked transitively via the parent).
     * Unpaginated — subtask nesting is capped at one level, so counts are expected to stay small.
     */
    List<Task> listSubtasks(String ownerUuid, Integer parentTaskId);

    /** Replaces a task's fields — see {@link TaskCommands.Update}'s Javadoc for replace semantics. */
    Task updateTask(String ownerUuid, Integer taskId, TaskCommands.Update command);

    /**
     * Moves a task to {@code newStatus}. Only rejects a no-op transition (see
     * {@code TaskStatus.canTransitionTo}) — otherwise any status may move to any other.
     */
    Task changeStatus(String ownerUuid, Integer taskId, TaskStatus newStatus);

    /** Deletes a task. */
    void deleteTask(String ownerUuid, Integer taskId);
}
