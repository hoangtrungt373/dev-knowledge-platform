package com.ttg.devknowledgeplatform.content.api;

import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.content.dto.CategoryResponse;
import com.ttg.devknowledgeplatform.content.dto.CategoryTreeNodeResponse;
import com.ttg.devknowledgeplatform.content.dto.CreateCategoryRequest;
import com.ttg.devknowledgeplatform.content.dto.UpdateCategoryRequest;
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

import java.util.List;

/**
 * HTTP contract for the admin category management API.
 *
 * <p>Defines URL mappings, parameter bindings, and validation constraints for category
 * CRUD operations, including tree-view and paginated flat-list endpoints. The implementation
 * ({@link com.ttg.devknowledgeplatform.content.api.impl.CategoryController}) carries no HTTP annotations.
 */
@RequestMapping("/api/v1/admin/categories")
public interface CategoryApi {

    /**
     * Creates a new category.
     *
     * @param request validated creation payload
     * @return {@code 201} with the created category
     */
    @PostMapping
    ResponseEntity<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest request);

    /**
     * Updates an existing category by its primary key.
     *
     * @param id      category primary key
     * @param request validated update payload
     * @return {@code 200} with the updated category
     */
    @PutMapping("/{id}")
    ResponseEntity<CategoryResponse> update(
            @PathVariable Integer id, @Valid @RequestBody UpdateCategoryRequest request);

    /**
     * Deletes a category by its primary key.
     *
     * @param id category primary key
     * @return {@code 204 No Content}
     */
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Integer id);

    /** Full tree: roots with nested {@code children}, sorted by name at each level. */
    @GetMapping("/tree")
    ResponseEntity<List<CategoryTreeNodeResponse>> tree();

    /**
     * Returns a single category by its primary key.
     *
     * @param id category primary key
     * @return {@code 200} with the category
     */
    @GetMapping("/{id}")
    ResponseEntity<CategoryResponse> getById(@PathVariable Integer id);

    /**
     * Flat, paginated list. Optional: {@code rootOnly=true}, or {@code parentId} for direct children;
     * do not pass both.
     *
     * @param page     zero-based page number (default 0)
     * @param size     page size (default 20)
     * @param sortBy   field to sort by; allowed values: {@code id}, {@code name}, {@code dteCreation} (default {@code id})
     * @param sortDir  sort direction: {@code asc} or {@code desc} (default {@code desc})
     * @param parentId optional parent category ID filter
     * @param rootOnly optional flag to return only root categories
     * @param q        optional full-text search query
     * @return {@code 200} with a paged list of categories
     */
    @GetMapping
    ResponseEntity<PagedResponse<CategoryResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) Integer parentId,
            @RequestParam(required = false) Boolean rootOnly,
            @RequestParam(required = false) String q);
}
