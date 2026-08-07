package com.ttg.devknowledgeplatform.task.api.impl;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.task.api.TaskApi;
import com.ttg.devknowledgeplatform.task.dto.ChangeTaskStatusRequest;
import com.ttg.devknowledgeplatform.task.dto.CreateTaskRequest;
import com.ttg.devknowledgeplatform.task.dto.TaskResponse;
import com.ttg.devknowledgeplatform.task.dto.UpdateTaskRequest;
import com.ttg.devknowledgeplatform.task.entity.Task;
import com.ttg.devknowledgeplatform.task.enums.TaskPriority;
import com.ttg.devknowledgeplatform.task.enums.TaskStatus;
import com.ttg.devknowledgeplatform.task.mapper.TaskMapper;
import com.ttg.devknowledgeplatform.task.service.TaskCommands;
import com.ttg.devknowledgeplatform.task.service.TaskFilter;
import com.ttg.devknowledgeplatform.task.service.TaskService;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link TaskApi}.
 */
@RestController
@RequiredArgsConstructor
public class TaskController implements TaskApi {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "dteCreation");

    private final TaskService taskService;
    private final TaskMapper taskMapper;

    @Override
    public ResponseEntity<TaskResponse> create(String ownerUuid, CreateTaskRequest request) {
        TaskCommands.Create command = new TaskCommands.Create(
                request.getTitle(), request.getDescription(), request.getProjectId(),
                request.getPriority(), request.getDueDate(), request.getParentTaskId());
        Task created = taskService.createTask(ownerUuid, command);
        return ResponseEntity.status(HttpStatus.CREATED).body(taskMapper.toResponse(created));
    }

    @Override
    public ResponseEntity<TaskResponse> getById(String ownerUuid, Integer id) {
        return ResponseEntity.ok(taskMapper.toResponse(taskService.getTask(ownerUuid, id)));
    }

    @Override
    public ResponseEntity<PagedResponse<TaskResponse>> list(
            String ownerUuid, int page, int size, String sortBy, String sortDir,
            Integer projectId, TaskStatus status, TaskPriority priority, Instant dueBefore, Instant dueAfter) {
        Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir));
        TaskFilter filter = new TaskFilter(projectId, status, priority, dueBefore, dueAfter);
        var result = taskService.listTasks(ownerUuid, filter, pageable).map(taskMapper::toResponse);
        return ResponseEntity.ok(PagedResponse.from(result));
    }

    @Override
    public ResponseEntity<List<TaskResponse>> listSubtasks(String ownerUuid, Integer id) {
        List<TaskResponse> subtasks = taskService.listSubtasks(ownerUuid, id).stream()
                .map(taskMapper::toResponse)
                .toList();
        return ResponseEntity.ok(subtasks);
    }

    @Override
    public ResponseEntity<TaskResponse> update(String ownerUuid, Integer id, UpdateTaskRequest request) {
        TaskCommands.Update command = new TaskCommands.Update(
                request.getTitle(), request.getDescription(), request.getProjectId(),
                request.getPriority(), request.getDueDate(), request.getParentTaskId());
        Task updated = taskService.updateTask(ownerUuid, id, command);
        return ResponseEntity.ok(taskMapper.toResponse(updated));
    }

    @Override
    public ResponseEntity<TaskResponse> changeStatus(String ownerUuid, Integer id, ChangeTaskStatusRequest request) {
        Task updated = taskService.changeStatus(ownerUuid, id, request.getStatus());
        return ResponseEntity.ok(taskMapper.toResponse(updated));
    }

    @Override
    public ResponseEntity<Void> delete(String ownerUuid, Integer id) {
        taskService.deleteTask(ownerUuid, id);
        return ResponseEntity.noContent().build();
    }

    private Sort buildSort(String sortBy, String sortDir) {
        String field = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "id";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }
}
