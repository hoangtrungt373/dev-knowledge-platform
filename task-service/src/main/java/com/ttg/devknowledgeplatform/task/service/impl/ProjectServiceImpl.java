package com.ttg.devknowledgeplatform.task.service.impl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.common.exception.CommonErrorCode;
import com.ttg.devknowledgeplatform.common.repository.UserRepository;
import com.ttg.devknowledgeplatform.task.entity.Project;
import com.ttg.devknowledgeplatform.task.enums.ProjectStatus;
import com.ttg.devknowledgeplatform.task.exception.TaskErrorCode;
import com.ttg.devknowledgeplatform.task.repository.ProjectRepository;
import com.ttg.devknowledgeplatform.task.service.ProjectCommands;
import com.ttg.devknowledgeplatform.task.service.ProjectService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Throwable.class)
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    @Override
    public Project createProject(Integer ownerId, ProjectCommands.Create command) {
        User owner = resolveUser(ownerId);
        Project project = Project.builder()
                .name(command.name())
                .description(command.description())
                .owner(owner)
                .status(ProjectStatus.ACTIVE)
                .build();
        Project saved = projectRepository.save(project);
        log.info("User {} created project {}", ownerId, saved.getId());
        return saved;
    }

    @Override
    public Project getProject(Integer ownerId, Integer projectId) {
        return resolveOwnedProject(ownerId, projectId);
    }

    @Override
    public Page<Project> listProjects(Integer ownerId, Pageable pageable) {
        return projectRepository.findByOwner(resolveUser(ownerId), pageable);
    }

    @Override
    public Project updateProject(Integer ownerId, Integer projectId, ProjectCommands.Update command) {
        Project project = resolveOwnedProject(ownerId, projectId);
        project.setName(command.name());
        project.setDescription(command.description());
        log.info("User {} updated project {}", ownerId, projectId);
        return projectRepository.save(project);
    }

    @Override
    public Project archiveProject(Integer ownerId, Integer projectId) {
        Project project = resolveOwnedProject(ownerId, projectId);
        project.setStatus(ProjectStatus.ARCHIVED);
        log.info("User {} archived project {}", ownerId, projectId);
        return projectRepository.save(project);
    }

    private Project resolveOwnedProject(Integer ownerId, Integer projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException(TaskErrorCode.PROJECT_NOT_FOUND));
        if (!project.getOwner().getId().equals(ownerId)) {
            throw new ResourceNotFoundException(TaskErrorCode.PROJECT_NOT_FOUND);
        }
        return project;
    }

    private User resolveUser(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(CommonErrorCode.USER_NOT_FOUND));
    }
}
