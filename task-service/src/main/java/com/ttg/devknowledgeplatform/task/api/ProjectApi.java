package com.ttg.devknowledgeplatform.task.api;

import com.ttg.devknowledgeplatform.common.annotation.CurrentUserId;
import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.task.dto.CreateProjectRequest;
import com.ttg.devknowledgeplatform.task.dto.ProjectResponse;
import com.ttg.devknowledgeplatform.task.dto.UpdateProjectRequest;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * HTTP contract for personal project management. The implementation
 * ({@link com.ttg.devknowledgeplatform.task.api.impl.ProjectController}) carries no HTTP
 * annotations.
 */
@RequestMapping("/api/v1/projects")
public interface ProjectApi {

    /**
     * Creates a new project owned by the caller.
     *
     * @return {@code 201} with the created project
     */
    @PostMapping
    ResponseEntity<ProjectResponse> create(
            @CurrentUserId Integer userId, @Valid @RequestBody CreateProjectRequest request);

    /**
     * Fetches a project owned by the caller.
     *
     * @return {@code 200} with the project
     */
    @GetMapping("/{id}")
    ResponseEntity<ProjectResponse> getById(@CurrentUserId Integer userId, @PathVariable Integer id);

    /**
     * Lists every project owned by the caller.
     *
     * @param page    zero-based page number (default 0)
     * @param size    page size (default 20)
     * @param sortBy  field to sort by; allowed values: {@code id}, {@code dteCreation} (default {@code id})
     * @param sortDir sort direction: {@code asc} or {@code desc} (default {@code desc})
     */
    @GetMapping
    ResponseEntity<PagedResponse<ProjectResponse>> list(
            @CurrentUserId Integer userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir);

    /**
     * Updates a project's name/description.
     *
     * @return {@code 200} with the updated project
     */
    @PutMapping("/{id}")
    ResponseEntity<ProjectResponse> update(
            @CurrentUserId Integer userId, @PathVariable Integer id, @Valid @RequestBody UpdateProjectRequest request);

    /**
     * Marks a project {@code ARCHIVED}.
     *
     * @return {@code 200} with the archived project
     */
    @PostMapping("/{id}/archive")
    ResponseEntity<ProjectResponse> archive(@CurrentUserId Integer userId, @PathVariable Integer id);
}
