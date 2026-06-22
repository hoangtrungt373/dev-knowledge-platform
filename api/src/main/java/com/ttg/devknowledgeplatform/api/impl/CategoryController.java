package com.ttg.devknowledgeplatform.api.impl;

import com.ttg.devknowledgeplatform.api.CategoryApi;
import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.admin.CategoryResponse;
import com.ttg.devknowledgeplatform.dto.admin.CategoryTreeNodeResponse;
import com.ttg.devknowledgeplatform.dto.admin.CreateCategoryRequest;
import com.ttg.devknowledgeplatform.dto.admin.UpdateCategoryRequest;
import com.ttg.devknowledgeplatform.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * Implementation of {@link CategoryApi}.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class CategoryController implements CategoryApi {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "name", "dteCreation");

    private final CategoryService categoryService;

    @Override
    public ResponseEntity<CategoryResponse> create(CreateCategoryRequest request) {
        CategoryResponse response = categoryService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<CategoryResponse> update(Integer id, UpdateCategoryRequest request) {
        CategoryResponse response = categoryService.update(id, request);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> delete(Integer id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<CategoryTreeNodeResponse>> tree() {
        return ResponseEntity.ok(categoryService.listTree());
    }

    @Override
    public ResponseEntity<CategoryResponse> getById(Integer id) {
        return ResponseEntity.ok(categoryService.getById(id));
    }

    @Override
    public ResponseEntity<PagedResponse<CategoryResponse>> list(
            int page, int size, String sortBy, String sortDir,
            Integer parentId, Boolean rootOnly, String q) {
        Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir));
        PagedResponse<CategoryResponse> response = categoryService.list(pageable, parentId, rootOnly, q);
        return ResponseEntity.ok(response);
    }

    private Sort buildSort(String sortBy, String sortDir) {
        String field = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "id";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }
}
