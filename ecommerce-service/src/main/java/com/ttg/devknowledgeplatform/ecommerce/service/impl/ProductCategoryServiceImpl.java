package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.common.exception.Validator;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductCategory;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductCategoryRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.spec.ProductCategorySpecification;
import com.ttg.devknowledgeplatform.ecommerce.service.ProductCategoryService;
import com.ttg.devknowledgeplatform.ecommerce.service.ProductCategoryTreeNode;
import com.ttg.devknowledgeplatform.infra.service.SlugService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Sort;
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
public class ProductCategoryServiceImpl implements ProductCategoryService {

    private final ProductCategoryRepository productCategoryRepository;
    private final SlugService slugService;

    @Override
    public ProductCategory create(String name, Integer parentId) {
        String normalizedName = normalizeName(name);
        Validator.isFalse(productCategoryRepository.existsByNameIgnoreCase(normalizedName),
                EcommerceErrorCode.PRODUCT_CATEGORY_NAME_CONFLICT, normalizedName);
        ProductCategory parent = resolveParent(parentId);
        String slug = slugService.generateUniqueSlug(
                normalizedName, productCategoryRepository::existsBySlug, EcommerceErrorCode.PRODUCT_CATEGORY_SLUG_CONFLICT);

        ProductCategory category = new ProductCategory();
        category.setName(normalizedName);
        category.setSlug(slug);
        category.setParent(parent);

        ProductCategory saved = productCategoryRepository.save(category);
        log.info("Created product category id={} slug={} parentId={}", saved.getId(), slug,
                parent != null ? parent.getId() : null);
        return saved;
    }

    @Override
    public ProductCategory update(Integer id, String name, Integer parentId) {
        ProductCategory category = findById(id);
        String normalizedName = normalizeName(name);

        if (!category.getName().equalsIgnoreCase(normalizedName)) {
            Validator.isFalse(productCategoryRepository.existsByNameIgnoreCaseAndIdNot(normalizedName, id),
                    EcommerceErrorCode.PRODUCT_CATEGORY_NAME_CONFLICT, normalizedName);
            category.setName(normalizedName);
            category.setSlug(slugService.generateUniqueSlug(
                    normalizedName, productCategoryRepository::existsBySlugAndIdNot, id,
                    EcommerceErrorCode.PRODUCT_CATEGORY_SLUG_CONFLICT));
        }

        ProductCategory newParent = resolveParent(parentId);
        validateParentAssignment(category, newParent);
        category.setParent(newParent);

        ProductCategory updated = productCategoryRepository.save(category);
        log.info("Updated product category id={}", id);
        return updated;
    }

    @Override
    public ProductCategory getById(Integer id) {
        return findById(id);
    }

    @Override
    public List<ProductCategory> list(String q) {
        Specification<ProductCategory> spec = ProductCategorySpecification.withFilters(q);
        return productCategoryRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "name"));
    }

    @Override
    public List<ProductCategoryTreeNode> listTree() {
        List<ProductCategory> all = productCategoryRepository.findAll();
        Map<Integer, ProductCategoryTreeNode> nodes = new HashMap<>();
        for (ProductCategory c : all) {
            nodes.put(c.getId(), new ProductCategoryTreeNode(c));
        }

        List<ProductCategoryTreeNode> roots = new ArrayList<>();
        for (ProductCategory c : all) {
            ProductCategoryTreeNode node = nodes.get(c.getId());
            if (c.getParent() == null) {
                roots.add(node);
            } else {
                ProductCategoryTreeNode parentNode = nodes.get(c.getParent().getId());
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

    private ProductCategory findById(Integer id) {
        return Validator.notFound(productCategoryRepository.findById(id), EcommerceErrorCode.PRODUCT_CATEGORY_NOT_FOUND, id);
    }

    private ProductCategory resolveParent(Integer parentId) {
        if (parentId == null) {
            return null;
        }
        return Validator.notFound(productCategoryRepository.findById(parentId), EcommerceErrorCode.PRODUCT_CATEGORY_NOT_FOUND, parentId);
    }

    /**
     * Rejects self-parent and assigning a descendant as parent (cycle).
     */
    private static void validateParentAssignment(ProductCategory category, ProductCategory newParent) {
        if (newParent == null) {
            return;
        }
        Validator.isFalse(newParent.getId().equals(category.getId()),
                EcommerceErrorCode.PRODUCT_CATEGORY_CYCLIC_PARENT);
        ProductCategory walk = newParent;
        while (walk != null) {
            Validator.isFalse(walk.getId().equals(category.getId()),
                    EcommerceErrorCode.PRODUCT_CATEGORY_CYCLIC_PARENT);
            walk = walk.getParent();
        }
    }

    private static void sortTreeNodes(List<ProductCategoryTreeNode> nodes) {
        nodes.sort(Comparator.comparing(n -> n.category().getName(), String.CASE_INSENSITIVE_ORDER));
        for (ProductCategoryTreeNode node : nodes) {
            if (!node.children().isEmpty()) {
                sortTreeNodes(node.children());
            }
        }
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim();
    }
}
