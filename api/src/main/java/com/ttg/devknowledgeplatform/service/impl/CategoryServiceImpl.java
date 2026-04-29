package com.ttg.devknowledgeplatform.service.impl;

import com.ttg.devknowledgeplatform.common.entity.Category;
import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.ErrorCode;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.admin.CategoryResponse;
import com.ttg.devknowledgeplatform.dto.admin.CategoryTreeNodeResponse;
import com.ttg.devknowledgeplatform.dto.admin.CreateCategoryRequest;
import com.ttg.devknowledgeplatform.dto.admin.UpdateCategoryRequest;
import com.ttg.devknowledgeplatform.repository.CategoryRepository;
import com.ttg.devknowledgeplatform.repository.spec.CategorySpecification;
import com.ttg.devknowledgeplatform.service.CategoryService;
import com.ttg.devknowledgeplatform.service.SlugService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final SlugService slugService;

    @Override
    public CategoryResponse create(CreateCategoryRequest request) {
        String name = normalizeName(request.getName());
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new ApiException(ErrorCode.CATEGORY_NAME_CONFLICT,
                    "A category with name '" + name + "' already exists");
        }
        Category parent = resolveParent(request.getParentId());
        String slug = slugService.generateUniqueCategorySlug(name);

        Category category = new Category();
        category.setName(name);
        category.setSlug(slug);
        category.setParent(parent);

        Category saved = categoryRepository.save(category);
        log.info("Created category id={} slug={} parentId={}", saved.getId(), slug,
                parent != null ? parent.getId() : null);
        return toResponse(saved);
    }

    @Override
    public CategoryResponse update(Integer id, UpdateCategoryRequest request) {
        Category category = findById(id);
        String name = normalizeName(request.getName());

        if (!category.getName().equalsIgnoreCase(name)) {
            if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
                throw new ApiException(ErrorCode.CATEGORY_NAME_CONFLICT,
                        "A category with name '" + name + "' already exists");
            }
            category.setName(name);
            category.setSlug(slugService.generateUniqueCategorySlug(name, id));
        }

        Category newParent = resolveParent(request.getParentId());
        validateParentAssignment(category, newParent);
        category.setParent(newParent);

        Category updated = categoryRepository.save(category);
        log.info("Updated category id={}", id);
        return toResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getById(Integer id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<CategoryResponse> list(
            Pageable pageable, Integer parentId, Boolean rootOnly, String q) {

        validateListFilters(parentId, rootOnly);
        Specification<Category> spec = CategorySpecification.withFilters(parentId, rootOnly, q);
        Page<CategoryResponse> page = categoryRepository.findAll(spec, pageable).map(this::toResponse);
        return PagedResponse.from(page);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryTreeNodeResponse> listTree() {
        List<Category> all = categoryRepository.findAll();
        Map<Integer, CategoryTreeNodeResponse> nodes = new HashMap<>();
        for (Category c : all) {
            nodes.put(c.getId(), CategoryTreeNodeResponse.builder()
                    .id(c.getId())
                    .name(c.getName())
                    .slug(c.getSlug())
                    .parentId(c.getParent() != null ? c.getParent().getId() : null)
                    .children(new ArrayList<>())
                    .build());
        }

        List<CategoryTreeNodeResponse> roots = new ArrayList<>();
        for (Category c : all) {
            CategoryTreeNodeResponse node = nodes.get(c.getId());
            if (c.getParent() == null) {
                roots.add(node);
            } else {
                CategoryTreeNodeResponse parentNode = nodes.get(c.getParent().getId());
                if (parentNode != null) {
                    parentNode.getChildren().add(node);
                } else {
                    roots.add(node);
                }
            }
        }

        sortTreeNodes(roots);
        return roots;
    }

    private static void validateListFilters(Integer parentId, Boolean rootOnly) {
        if (Boolean.TRUE.equals(rootOnly) && parentId != null) {
            throw new ApiException(
                    ErrorCode.CATEGORY_LIST_FILTER_CONFLICT,
                    "Use only one of rootOnly=true or parentId=...");
        }
    }

    private Category findById(Integer id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.CATEGORY_NOT_FOUND,
                        "Category not found with id: " + id));
    }

    private Category resolveParent(Integer parentId) {
        if (parentId == null) {
            return null;
        }
        return categoryRepository.findById(parentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.CATEGORY_NOT_FOUND,
                        "Parent category not found with id: " + parentId));
    }

    /**
     * Rejects self-parent and assigning a descendant as parent (cycle).
     */
    private static void validateParentAssignment(Category category, Category newParent) {
        if (newParent == null) {
            return;
        }
        if (newParent.getId().equals(category.getId())) {
            throw new ApiException(
                    ErrorCode.CATEGORY_CYCLIC_PARENT,
                    "A category cannot be its own parent");
        }
        Category walk = newParent;
        while (walk != null) {
            if (walk.getId().equals(category.getId())) {
                throw new ApiException(
                        ErrorCode.CATEGORY_CYCLIC_PARENT,
                        "Cannot set parent to a descendant category");
            }
            walk = walk.getParent();
        }
    }

    private static void sortTreeNodes(List<CategoryTreeNodeResponse> nodes) {
        nodes.sort(Comparator.comparing(CategoryTreeNodeResponse::getName, String.CASE_INSENSITIVE_ORDER));
        for (CategoryTreeNodeResponse node : nodes) {
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                sortTreeNodes(node.getChildren());
            }
        }
    }

    private static String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.trim();
    }

    private CategoryResponse toResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                .createdAt(category.getDteCreation())
                .updatedAt(category.getDteLastModification())
                .build();
    }
}
