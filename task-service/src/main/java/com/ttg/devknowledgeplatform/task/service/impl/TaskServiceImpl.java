package com.ttg.devknowledgeplatform.task.service.impl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.common.exception.CommonErrorCode;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.common.repository.UserRepository;
import com.ttg.devknowledgeplatform.content.entity.ContentItem;
import com.ttg.devknowledgeplatform.content.repository.ContentItemRepository;
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

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Throwable.class)
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ContentItemRepository contentItemRepository;

    @Override
    public Task createTask(Integer ownerId, TaskCommands.Create command) {
        User owner = resolveUser(ownerId);
        Task task = Task.builder()
                .owner(owner)
                .project(resolveOwnedProjectOrNull(ownerId, command.projectId()))
                .title(command.title())
                .description(command.description())
                .status(TaskStatus.TODO)
                .priority(command.priority())
                .dueDate(command.dueDate())
                .contentItem(resolveContentItemOrNull(command.contentItemId()))
                .build();
        Task saved = taskRepository.save(task);
        log.info("User {} created task {}", ownerId, saved.getId());
        return saved;
    }

    @Override
    public Task getTask(Integer ownerId, Integer taskId) {
        return resolveOwnedTask(ownerId, taskId);
    }

    @Override
    public Page<Task> listTasks(Integer ownerId, TaskFilter filter, Pageable pageable) {
        Specification<Task> spec = TaskSpecification.withFilters(
                ownerId, filter.projectId(), filter.status(), filter.priority(),
                filter.dueBefore(), filter.dueAfter());
        return taskRepository.findAll(spec, pageable);
    }

    @Override
    public Task updateTask(Integer ownerId, Integer taskId, TaskCommands.Update command) {
        Task task = resolveOwnedTask(ownerId, taskId);
        task.setProject(resolveOwnedProjectOrNull(ownerId, command.projectId()));
        task.setTitle(command.title());
        task.setDescription(command.description());
        task.setPriority(command.priority());
        task.setDueDate(command.dueDate());
        task.setContentItem(resolveContentItemOrNull(command.contentItemId()));
        log.info("User {} updated task {}", ownerId, taskId);
        return taskRepository.save(task);
    }

    @Override
    public Task changeStatus(Integer ownerId, Integer taskId, TaskStatus newStatus) {
        Task task = resolveOwnedTask(ownerId, taskId);
        if (!task.getStatus().canTransitionTo(newStatus)) {
            throw new BusinessException(TaskErrorCode.TASK_INVALID_STATUS_TRANSITION);
        }
        task.setStatus(newStatus);
        log.info("User {} moved task {} to {}", ownerId, taskId, newStatus);
        return taskRepository.save(task);
    }

    @Override
    public void deleteTask(Integer ownerId, Integer taskId) {
        Task task = resolveOwnedTask(ownerId, taskId);
        taskRepository.delete(task);
        log.info("User {} deleted task {}", ownerId, taskId);
    }

    private Task resolveOwnedTask(Integer ownerId, Integer taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException(TaskErrorCode.TASK_NOT_FOUND));
        if (!task.getOwner().getId().equals(ownerId)) {
            throw new ResourceNotFoundException(TaskErrorCode.TASK_NOT_FOUND);
        }
        return task;
    }

    /**
     * A one-line ownership comparison, not shared multi-step logic — reaches {@link ProjectRepository}
     * directly rather than delegating to {@code ProjectService}, unlike e.g. {@code social-service}'s
     * {@code DmServiceImpl} reusing {@code FriendService.getRelationshipStatus} for real shared logic.
     */
    private Project resolveOwnedProjectOrNull(Integer ownerId, Integer projectId) {
        if (projectId == null) {
            return null;
        }
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException(TaskErrorCode.PROJECT_NOT_FOUND));
        if (!project.getOwner().getId().equals(ownerId)) {
            throw new ResourceNotFoundException(TaskErrorCode.PROJECT_NOT_FOUND);
        }
        return project;
    }

    private ContentItem resolveContentItemOrNull(Integer contentItemId) {
        if (contentItemId == null) {
            return null;
        }
        return contentItemRepository.findById(contentItemId)
                .orElseThrow(() -> new ResourceNotFoundException(TaskErrorCode.TASK_CONTENT_ITEM_NOT_FOUND));
    }

    private User resolveUser(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(CommonErrorCode.USER_NOT_FOUND));
    }
}
