package com.ttg.devknowledgeplatform.task.service.impl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ttg.devknowledgeplatform.common.exception.Validator;
import com.ttg.devknowledgeplatform.task.entity.Project;
import com.ttg.devknowledgeplatform.task.entity.Task;
import com.ttg.devknowledgeplatform.task.enums.TaskStatus;
import com.ttg.devknowledgeplatform.task.exception.TaskErrorCode;
import com.ttg.devknowledgeplatform.task.repository.ProjectRepository;
import com.ttg.devknowledgeplatform.task.repository.TaskRepository;
import com.ttg.devknowledgeplatform.task.repository.spec.TaskSpecification;
import com.ttg.devknowledgeplatform.task.service.TaskCommands;
import com.ttg.devknowledgeplatform.task.service.TaskFilter;
import com.ttg.devknowledgeplatform.task.service.TaskService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Throwable.class)
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;

    @Override
    public Task createTask(String ownerUuid, TaskCommands.Create command) {
        Task task = Task.builder()
                .ownerUuid(ownerUuid)
                .project(resolveOwnedProjectOrNull(ownerUuid, command.projectId()))
                .title(command.title())
                .description(command.description())
                .status(TaskStatus.TODO)
                .priority(command.priority())
                .dueDate(command.dueDate())
                .build();
        Task parent = resolveOwnedParentTaskOrNull(ownerUuid, command.parentTaskId());
        validateParentAssignment(task, parent);
        task.setParentTask(parent);
        Task saved = taskRepository.save(task);
        log.info("User {} created task {}", ownerUuid, saved.getId());
        return saved;
    }

    @Override
    public Task getTask(String ownerUuid, Integer taskId) {
        return resolveOwnedTask(ownerUuid, taskId);
    }

    @Override
    public Page<Task> listTasks(String ownerUuid, TaskFilter filter, Pageable pageable) {
        Specification<Task> spec = TaskSpecification.withFilters(
                ownerUuid, filter.projectId(), filter.status(), filter.priority(),
                filter.dueBefore(), filter.dueAfter());
        return taskRepository.findAll(spec, pageable);
    }

    @Override
    public List<Task> listSubtasks(String ownerUuid, Integer parentTaskId) {
        Task parent = resolveOwnedTask(ownerUuid, parentTaskId);
        return new ArrayList<>(parent.getSubtasks());
    }

    @Override
    public Task updateTask(String ownerUuid, Integer taskId, TaskCommands.Update command) {
        Task task = resolveOwnedTask(ownerUuid, taskId);
        task.setProject(resolveOwnedProjectOrNull(ownerUuid, command.projectId()));
        task.setTitle(command.title());
        task.setDescription(command.description());
        task.setPriority(command.priority());
        task.setDueDate(command.dueDate());
        Task parent = resolveOwnedParentTaskOrNull(ownerUuid, command.parentTaskId());
        validateParentAssignment(task, parent);
        task.setParentTask(parent);
        log.info("User {} updated task {}", ownerUuid, taskId);
        return taskRepository.save(task);
    }

    @Override
    public Task changeStatus(String ownerUuid, Integer taskId, TaskStatus newStatus) {
        Task task = resolveOwnedTask(ownerUuid, taskId);
        Validator.isTrue(task.getStatus().canTransitionTo(newStatus), TaskErrorCode.TASK_INVALID_STATUS_TRANSITION);
        task.setStatus(newStatus);
        log.info("User {} moved task {} to {}", ownerUuid, taskId, newStatus);
        return taskRepository.save(task);
    }

    @Override
    public void deleteTask(String ownerUuid, Integer taskId) {
        Task task = resolveOwnedTask(ownerUuid, taskId);
        taskRepository.delete(task);
        log.info("User {} deleted task {}", ownerUuid, taskId);
    }

    private Task resolveOwnedTask(String ownerUuid, Integer taskId) {
        Task task = Validator.notFound(taskRepository.findById(taskId), TaskErrorCode.TASK_NOT_FOUND);
        Validator.isTrue(task.getOwnerUuid().equals(ownerUuid), TaskErrorCode.TASK_NOT_FOUND);
        return task;
    }

    /**
     * A one-line ownership comparison, not shared multi-step logic — reaches {@link ProjectRepository}
     * directly rather than delegating to {@code ProjectService}, unlike e.g. {@code social-service}'s
     * {@code DmServiceImpl} reusing {@code FriendService.getRelationshipStatus} for real shared logic.
     */
    private Project resolveOwnedProjectOrNull(String ownerUuid, Integer projectId) {
        if (projectId == null) {
            return null;
        }
        Project project = Validator.notFound(projectRepository.findById(projectId), TaskErrorCode.PROJECT_NOT_FOUND);
        Validator.isTrue(project.getOwnerUuid().equals(ownerUuid), TaskErrorCode.PROJECT_NOT_FOUND);
        return project;
    }

    private Task resolveOwnedParentTaskOrNull(String ownerUuid, Integer parentTaskId) {
        if (parentTaskId == null) {
            return null;
        }
        Task parent = Validator.notFound(taskRepository.findById(parentTaskId), TaskErrorCode.TASK_NOT_FOUND);
        Validator.isTrue(parent.getOwnerUuid().equals(ownerUuid), TaskErrorCode.TASK_NOT_FOUND);
        return parent;
    }

    /**
     * Rejects self-parent, a parent that is itself a subtask, and assigning a parent to a task
     * that already has subtasks of its own — subtask nesting is capped at one level, unlike
     * {@code content-service}'s {@code Category} tree (see {@code Task}'s Javadoc).
     */
    private static void validateParentAssignment(Task task, Task newParent) {
        if (newParent == null) {
            return;
        }
        Validator.isFalse(newParent.getId() != null && newParent.getId().equals(task.getId()),
                TaskErrorCode.TASK_INVALID_PARENT, "A task cannot be its own parent");
        Validator.isNull(newParent.getParentTask(), TaskErrorCode.TASK_INVALID_PARENT, "Chosen parent is itself a subtask");
        Validator.isTrue(task.getSubtasks().isEmpty(),
                TaskErrorCode.TASK_INVALID_PARENT, "This task already has its own subtasks");
    }
}
