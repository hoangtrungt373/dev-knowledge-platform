package com.ttg.devknowledgeplatform.content.service.impl;

import com.ttg.devknowledgeplatform.common.exception.Validator;
import com.ttg.devknowledgeplatform.content.entity.Category;
import com.ttg.devknowledgeplatform.content.exception.ContentErrorCode;
import com.ttg.devknowledgeplatform.content.repository.CategoryRepository;
import com.ttg.devknowledgeplatform.content.repository.ContentItemRepository;
import com.ttg.devknowledgeplatform.content.repository.spec.CategorySpecification;
import com.ttg.devknowledgeplatform.content.service.CategoryService;
import com.ttg.devknowledgeplatform.content.service.CategoryTreeNode;
import com.ttg.devknowledgeplatform.infra.service.SlugService;
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
@Transactional(rollbackFor = Throwable.class)
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ContentItemRepository contentItemRepository;
    private final SlugService slugService;

    @Override
    public Category create(String name, Integer parentId) {
        String normalizedName = normalizeName(name);
        Validator.isFalse(categoryRepository.existsByNameIgnoreCase(normalizedName), ContentErrorCode.CATEGORY_NAME_CONFLICT, normalizedName);
        Category parent = resolveParent(parentId);
        String slug = slugService.generateUniqueSlug(normalizedName, categoryRepository::existsBySlug, ContentErrorCode.CATEGORY_SLUG_CONFLICT);

        Category category = new Category();
        category.setName(normalizedName);
        category.setSlug(slug);
        category.setParent(parent);

        Category saved = categoryRepository.save(category);
        log.info("Created category id={} slug={} parentId={}", saved.getId(), slug,
                parent != null ? parent.getId() : null);
        return saved;
    }

    @Override
    public Category update(Integer id, String name, Integer parentId) {
        Category category = findById(id);
        String normalizedName = normalizeName(name);

        if (!category.getName().equalsIgnoreCase(normalizedName)) {
            Validator.isFalse(categoryRepository.existsByNameIgnoreCaseAndIdNot(normalizedName, id), ContentErrorCode.CATEGORY_NAME_CONFLICT, normalizedName);
            category.setName(normalizedName);
            category.setSlug(slugService.generateUniqueSlug(normalizedName, categoryRepository::existsBySlugAndIdNot, id, ContentErrorCode.CATEGORY_SLUG_CONFLICT));
        }

        Category newParent = resolveParent(parentId);
        validateParentAssignment(category, newParent);
        category.setParent(newParent);

        Category updated = categoryRepository.save(category);
        log.info("Updated category id={}", id);
        return updated;
    }

    @Override
    public Category getById(Integer id) {
        return findById(id);
    }

    @Override
    public Page<Category> list(Pageable pageable, Integer parentId, Boolean rootOnly, String q) {
        validateListFilters(parentId, rootOnly);
        Specification<Category> spec = CategorySpecification.withFilters(parentId, rootOnly, q);
        return categoryRepository.findAll(spec, pageable);
    }

    @Override
    public List<CategoryTreeNode> listTree() {
        List<Category> all = categoryRepository.findAll();
        Map<Integer, CategoryTreeNode> nodes = new HashMap<>();
        for (Category c : all) {
            nodes.put(c.getId(), new CategoryTreeNode(c));
        }

        List<CategoryTreeNode> roots = new ArrayList<>();
        for (Category c : all) {
            CategoryTreeNode node = nodes.get(c.getId());
            if (c.getParent() == null) {
                roots.add(node);
            } else {
                CategoryTreeNode parentNode = nodes.get(c.getParent().getId());
                if (parentNode != null) {
                    parentNode.children().add(node);
                } else {
                    roots.add(node);
                }
            }
        }

        sortTreeNodes(roots);
        return roots;
    }

    @Override
    public void delete(Integer id) {
        Category category = findById(id);
        Validator.isFalse(categoryRepository.existsByParentId(id), ContentErrorCode.CATEGORY_HAS_CHILDREN, id);
        Validator.isFalse(contentItemRepository.existsByCategoryId(id), ContentErrorCode.CATEGORY_IN_USE, id);
        categoryRepository.delete(category);
        log.info("Deleted category id={}", id);
    }

    private static void validateListFilters(Integer parentId, Boolean rootOnly) {
        Validator.isFalse(Boolean.TRUE.equals(rootOnly) && parentId != null,
                ContentErrorCode.CATEGORY_LIST_FILTER_CONFLICT,
                "Use only one of rootOnly=true or parentId=...");
    }

    private Category findById(Integer id) {
        return Validator.notFound(categoryRepository.findById(id), ContentErrorCode.CATEGORY_NOT_FOUND, id);
    }

    private Category resolveParent(Integer parentId) {
        if (parentId == null) {
            return null;
        }
        return Validator.notFound(categoryRepository.findById(parentId), ContentErrorCode.CATEGORY_NOT_FOUND, parentId);
    }

    /**
     * Rejects self-parent and assigning a descendant as parent (cycle).
     */
    private static void validateParentAssignment(Category category, Category newParent) {
        if (newParent == null) {
            return;
        }
        Validator.isFalse(newParent.getId().equals(category.getId()),
                ContentErrorCode.CATEGORY_CYCLIC_PARENT, "A category cannot be its own parent");
        Category walk = newParent;
        while (walk != null) {
            Validator.isFalse(walk.getId().equals(category.getId()),
                    ContentErrorCode.CATEGORY_CYCLIC_PARENT, "Cannot set parent to a descendant category");
            walk = walk.getParent();
        }
    }

    private static void sortTreeNodes(List<CategoryTreeNode> nodes) {
        nodes.sort(Comparator.comparing(n -> n.category().getName(), String.CASE_INSENSITIVE_ORDER));
        for (CategoryTreeNode node : nodes) {
            if (!node.children().isEmpty()) {
                sortTreeNodes(node.children());
            }
        }
    }

    private static String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.trim();
    }

}
