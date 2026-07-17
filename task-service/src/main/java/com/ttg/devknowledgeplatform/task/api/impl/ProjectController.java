package com.ttg.devknowledgeplatform.task.api.impl;

import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.task.api.ProjectApi;
import com.ttg.devknowledgeplatform.task.dto.CreateProjectRequest;
import com.ttg.devknowledgeplatform.task.dto.ProjectResponse;
import com.ttg.devknowledgeplatform.task.dto.UpdateProjectRequest;
import com.ttg.devknowledgeplatform.task.entity.Project;
import com.ttg.devknowledgeplatform.task.mapper.ProjectMapper;
import com.ttg.devknowledgeplatform.task.service.ProjectCommands;
import com.ttg.devknowledgeplatform.task.service.ProjectService;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link ProjectApi}.
 */
@RestController
@RequiredArgsConstructor
public class ProjectController implements ProjectApi {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "dteCreation");

    private final ProjectService projectService;
    private final ProjectMapper projectMapper;

    @Override
    public ResponseEntity<ProjectResponse> create(Integer userId, CreateProjectRequest request) {
        ProjectCommands.Create command = new ProjectCommands.Create(request.getName(), request.getDescription());
        Project created = projectService.createProject(userId, command);
        return ResponseEntity.status(HttpStatus.CREATED).body(projectMapper.toResponse(created));
    }

    @Override
    public ResponseEntity<ProjectResponse> getById(Integer userId, Integer id) {
        return ResponseEntity.ok(projectMapper.toResponse(projectService.getProject(userId, id)));
    }

    @Override
    public ResponseEntity<PagedResponse<ProjectResponse>> list(
            Integer userId, int page, int size, String sortBy, String sortDir) {
        Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir));
        var result = projectService.listProjects(userId, pageable).map(projectMapper::toResponse);
        return ResponseEntity.ok(PagedResponse.from(result));
    }

    @Override
    public ResponseEntity<ProjectResponse> update(Integer userId, Integer id, UpdateProjectRequest request) {
        ProjectCommands.Update command = new ProjectCommands.Update(request.getName(), request.getDescription());
        Project updated = projectService.updateProject(userId, id, command);
        return ResponseEntity.ok(projectMapper.toResponse(updated));
    }

    @Override
    public ResponseEntity<ProjectResponse> archive(Integer userId, Integer id) {
        Project archived = projectService.archiveProject(userId, id);
        return ResponseEntity.ok(projectMapper.toResponse(archived));
    }

    private Sort buildSort(String sortBy, String sortDir) {
        String field = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "id";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }
}
