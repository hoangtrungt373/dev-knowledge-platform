package com.ttg.devknowledgeplatform.task.service.impl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ttg.devknowledgeplatform.common.exception.Validator;
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

    @Override
    public Project createProject(String ownerUuid, ProjectCommands.Create command) {
        Project project = Project.builder()
                .name(command.name())
                .description(command.description())
                .ownerUuid(ownerUuid)
                .status(ProjectStatus.ACTIVE)
                .build();
        Project saved = projectRepository.save(project);
        log.info("User {} created project {}", ownerUuid, saved.getId());
        return saved;
    }

    @Override
    public Project getProject(String ownerUuid, Integer projectId) {
        return resolveOwnedProject(ownerUuid, projectId);
    }

    @Override
    public Page<Project> listProjects(String ownerUuid, Pageable pageable) {
        return projectRepository.findByOwnerUuid(ownerUuid, pageable);
    }

    @Override
    public Project updateProject(String ownerUuid, Integer projectId, ProjectCommands.Update command) {
        Project project = resolveOwnedProject(ownerUuid, projectId);
        project.setName(command.name());
        project.setDescription(command.description());
        log.info("User {} updated project {}", ownerUuid, projectId);
        return projectRepository.save(project);
    }

    @Override
    public Project archiveProject(String ownerUuid, Integer projectId) {
        Project project = resolveOwnedProject(ownerUuid, projectId);
        project.setStatus(ProjectStatus.ARCHIVED);
        log.info("User {} archived project {}", ownerUuid, projectId);
        return projectRepository.save(project);
    }

    private Project resolveOwnedProject(String ownerUuid, Integer projectId) {
        Project project = Validator.notFound(projectRepository.findById(projectId), TaskErrorCode.PROJECT_NOT_FOUND);
        Validator.isTrue(project.getOwnerUuid().equals(ownerUuid), TaskErrorCode.PROJECT_NOT_FOUND);
        return project;
    }
}
