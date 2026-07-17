package com.ttg.devknowledgeplatform.task.api;

import java.time.Instant;

import com.ttg.devknowledgeplatform.common.annotation.CurrentUserId;
import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.task.dto.ChangeTaskStatusRequest;
import com.ttg.devknowledgeplatform.task.dto.CreateTaskRequest;
import com.ttg.devknowledgeplatform.task.dto.TaskResponse;
import com.ttg.devknowledgeplatform.task.dto.UpdateTaskRequest;
import com.ttg.devknowledgeplatform.task.enums.TaskPriority;
import com.ttg.devknowledgeplatform.task.enums.TaskStatus;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * HTTP contract for personal task management. The implementation
 * ({@link com.ttg.devknowledgeplatform.task.api.impl.TaskController}) carries no HTTP
 * annotations.
 */
@RequestMapping("/api/v1/tasks")
public interface TaskApi {

    /**
     * Creates a new task owned by the caller.
     *
     * @return {@code 201} with the created task
     */
    @PostMapping
    ResponseEntity<TaskResponse> create(
            @CurrentUserId Integer userId, @Valid @RequestBody CreateTaskRequest request);

    /**
     * Fetches a task owned by the caller.
     *
     * @return {@code 200} with the task
     */
    @GetMapping("/{id}")
    ResponseEntity<TaskResponse> getById(@CurrentUserId Integer userId, @PathVariable Integer id);

    /**
     * Lists the caller's tasks, optionally narrowed by project, status, priority, and due-date range.
     *
     * @param page       zero-based page number (default 0)
     * @param size       page size (default 20)
     * @param sortBy     field to sort by; allowed values: {@code id}, {@code dteCreation} (default {@code id})
     * @param sortDir    sort direction: {@code asc} or {@code desc} (default {@code desc})
     * @param projectId  optional project filter
     * @param status     optional status filter
     * @param priority   optional priority filter
     * @param dueBefore  optional upper bound on due date (inclusive)
     * @param dueAfter   optional lower bound on due date (inclusive)
     */
    @GetMapping
    ResponseEntity<PagedResponse<TaskResponse>> list(
            @CurrentUserId Integer userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) Integer projectId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) Instant dueBefore,
            @RequestParam(required = false) Instant dueAfter);

    /**
     * Replaces a task's fields — see {@link UpdateTaskRequest}'s Javadoc for replace semantics.
     *
     * @return {@code 200} with the updated task
     */
    @PutMapping("/{id}")
    ResponseEntity<TaskResponse> update(
            @CurrentUserId Integer userId, @PathVariable Integer id, @Valid @RequestBody UpdateTaskRequest request);

    /**
     * Moves a task to a new status.
     *
     * @return {@code 200} with the updated task
     */
    @PostMapping("/{id}/status")
    ResponseEntity<TaskResponse> changeStatus(
            @CurrentUserId Integer userId, @PathVariable Integer id, @Valid @RequestBody ChangeTaskStatusRequest request);

    /**
     * Deletes a task.
     *
     * @return {@code 204 No Content}
     */
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@CurrentUserId Integer userId, @PathVariable Integer id);
}
