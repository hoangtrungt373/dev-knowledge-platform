package com.ttg.devknowledgeplatform.api.impl;

import com.ttg.devknowledgeplatform.api.CategoryApi;
import com.ttg.devknowledgeplatform.content.entity.Category;
import com.ttg.devknowledgeplatform.content.service.CategoryService;
import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.admin.CategoryResponse;
import com.ttg.devknowledgeplatform.dto.admin.CategoryTreeNodeResponse;
import com.ttg.devknowledgeplatform.dto.admin.CreateCategoryRequest;
import com.ttg.devknowledgeplatform.dto.admin.UpdateCategoryRequest;
import com.ttg.devknowledgeplatform.mapper.CategoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
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
    private final CategoryMapper categoryMapper;

    @Override
    public ResponseEntity<CategoryResponse> create(CreateCategoryRequest request) {
        Category category = categoryService.create(request.getName(), request.getParentId());
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryMapper.toResponse(category));
    }

    @Override
    public ResponseEntity<CategoryResponse> update(Integer id, UpdateCategoryRequest request) {
        Category category = categoryService.update(id, request.getName(), request.getParentId());
        return ResponseEntity.ok(categoryMapper.toResponse(category));
    }

    @Override
    public ResponseEntity<Void> delete(Integer id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<CategoryTreeNodeResponse>> tree() {
        List<CategoryTreeNodeResponse> tree = categoryService.listTree().stream()
                .map(categoryMapper::toTreeNodeResponse)
                .toList();
        return ResponseEntity.ok(tree);
    }

    @Override
    public ResponseEntity<CategoryResponse> getById(Integer id) {
        return ResponseEntity.ok(categoryMapper.toResponse(categoryService.getById(id)));
    }

    @Override
    public ResponseEntity<PagedResponse<CategoryResponse>> list(
            int page, int size, String sortBy, String sortDir,
            Integer parentId, Boolean rootOnly, String q) {
        Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir));
        Page<CategoryResponse> responses = categoryService.list(pageable, parentId, rootOnly, q)
                .map(categoryMapper::toResponse);
        return ResponseEntity.ok(PagedResponse.from(responses));
    }

    private Sort buildSort(String sortBy, String sortDir) {
        String field = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "id";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }
}
