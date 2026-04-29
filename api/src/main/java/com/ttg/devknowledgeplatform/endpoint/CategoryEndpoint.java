package com.ttg.devknowledgeplatform.endpoint;

import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.admin.CategoryResponse;
import com.ttg.devknowledgeplatform.dto.admin.CategoryTreeNodeResponse;
import com.ttg.devknowledgeplatform.dto.admin.CreateCategoryRequest;
import com.ttg.devknowledgeplatform.dto.admin.UpdateCategoryRequest;
import com.ttg.devknowledgeplatform.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
@Slf4j
public class CategoryEndpoint {

    private final CategoryService categoryService;

    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest request) {
        CategoryResponse response = categoryService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> update(
            @PathVariable Integer id, @Valid @RequestBody UpdateCategoryRequest request) {
        CategoryResponse response = categoryService.update(id, request);
        return ResponseEntity.ok(response);
    }

    /** Full tree: roots with nested {@code children}, sorted by name at each level. */
    @GetMapping("/tree")
    public ResponseEntity<List<CategoryTreeNodeResponse>> tree() {
        return ResponseEntity.ok(categoryService.listTree());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(categoryService.getById(id));
    }

    /** Flat, paginated list. Optional: {@code rootOnly=true}, or {@code parentId} for direct children; do not pass both. */
    @GetMapping
    public ResponseEntity<PagedResponse<CategoryResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer parentId,
            @RequestParam(required = false) Boolean rootOnly,
            @RequestParam(required = false) String q) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        PagedResponse<CategoryResponse> response =
                categoryService.list(pageable, parentId, rootOnly, q);
        return ResponseEntity.ok(response);
    }
}
