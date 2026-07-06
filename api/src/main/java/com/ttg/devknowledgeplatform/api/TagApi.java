package com.ttg.devknowledgeplatform.api;

import com.ttg.devknowledgeplatform.content.enums.TagStatus;
import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.content.CreateTagRequest;
import com.ttg.devknowledgeplatform.dto.content.TagResponse;
import com.ttg.devknowledgeplatform.dto.content.UpdateTagRequest;
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
 * HTTP contract for the admin tag management API.
 *
 * <p>Defines URL mappings, parameter bindings, and validation constraints for tag CRUD operations.
 * The implementation ({@link com.ttg.devknowledgeplatform.api.impl.TagController}) carries no HTTP annotations.
 */
@RequestMapping("/api/v1/admin/tags")
public interface TagApi {

    /**
     * Creates a new tag.
     *
     * @param request validated creation payload
     * @return {@code 201} with the created tag
     */
    @PostMapping
    ResponseEntity<TagResponse> create(@Valid @RequestBody CreateTagRequest request);

    /**
     * Updates an existing tag by its primary key.
     *
     * @param id      tag primary key
     * @param request validated update payload
     * @return {@code 200} with the updated tag
     */
    @PutMapping("/{id}")
    ResponseEntity<TagResponse> update(
            @PathVariable Integer id, @Valid @RequestBody UpdateTagRequest request);

    /**
     * Deletes a tag by its primary key.
     *
     * @param id tag primary key
     * @return {@code 204 No Content}
     */
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Integer id);

    /**
     * Returns a single tag by its primary key.
     *
     * @param id tag primary key
     * @return {@code 200} with the tag
     */
    @GetMapping("/{id}")
    ResponseEntity<TagResponse> getById(@PathVariable Integer id);

    /**
     * Returns a paginated, optionally filtered list of tags.
     *
     * @param page    zero-based page number (default 0)
     * @param size    page size (default 20)
     * @param sortBy  field to sort by; allowed values: {@code id}, {@code name}, {@code status}, {@code dteCreation} (default {@code id})
     * @param sortDir sort direction: {@code asc} or {@code desc} (default {@code desc})
     * @param status  optional status filter
     * @param q       optional full-text search query
     * @return {@code 200} with a paged list of tags
     */
    @GetMapping
    ResponseEntity<PagedResponse<TagResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) TagStatus status,
            @RequestParam(required = false) String q);
}
