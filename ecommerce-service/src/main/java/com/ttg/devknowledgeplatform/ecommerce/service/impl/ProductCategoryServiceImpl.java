package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.common.exception.CommonErrorCode;
import com.ttg.devknowledgeplatform.common.exception.Validator;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductAttribute;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductCategory;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductCategoryAttribute;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductAttributeRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductCategoryRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.spec.ProductCategorySpecification;
import com.ttg.devknowledgeplatform.ecommerce.service.ProductCategoryService;
import com.ttg.devknowledgeplatform.ecommerce.service.ProductCategoryTreeNode;
import com.ttg.devknowledgeplatform.ecommerce.util.NameNormalizer;
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Throwable.class)
public class ProductCategoryServiceImpl implements ProductCategoryService {

    private final ProductCategoryRepository productCategoryRepository;
    private final ProductAttributeRepository productAttributeRepository;
    private final SlugService slugService;

    @Override
    public ProductCategory create(String name, Integer parentId, List<AttributeAssignmentInput> attributes) {
        String normalizedName = NameNormalizer.normalize(name);
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
        // Always applied, even when the request omits attributes entirely (null -> List.of()) —
        // unlike update, a brand-new category has no prior schema to "leave unchanged", so there's
        // no three-state semantics to preserve here (see applyCategoryAttributes' own Javadoc).
        applyCategoryAttributes(saved, attributes == null ? List.of() : attributes);

        log.info("Created product category id={} slug={} parentId={}", saved.getId(), slug,
                parent != null ? parent.getId() : null);
        return saved;
    }

    @Override
    public ProductCategory update(Integer id, String name, Integer parentId, List<AttributeAssignmentInput> attributes) {
        ProductCategory category = findById(id);
        String normalizedName = NameNormalizer.normalize(name);

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

        if (attributes != null) {
            applyCategoryAttributes(category, attributes);
        }

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

    /**
     * Clears and rebuilds {@code category.categoryAttributes} from {@code attributes} — mirrors
     * {@code ProductServiceImpl.applyTagIds} exactly, except each element also carries a
     * {@code required} flag, and list position (not a caller-supplied number) becomes each new
     * row's {@link ProductCategoryAttribute#getDisplayOrder()}.
     *
     * <p>Called from {@link #create} unconditionally (an empty/{@code null} list just means "no
     * attributes yet") and from {@link #update} only when {@code attributes != null} — see each
     * call site's own comment for the three-state semantics {@code update} preserves.
     */
    private void applyCategoryAttributes(ProductCategory category, List<AttributeAssignmentInput> attributes) {
        Validator.isFalse(attributes.stream().anyMatch(Objects::isNull),
                CommonErrorCode.VALIDATION_FIELD_INVALID, "attributes must not contain null");
        if (attributes.isEmpty()) {
            category.getCategoryAttributes().clear();
            return;
        }

        List<Integer> attributeIds = attributes.stream().map(AttributeAssignmentInput::attributeId).toList();
        Set<Integer> uniqueIds = new LinkedHashSet<>(attributeIds);
        Validator.isTrue(uniqueIds.size() == attributeIds.size(),
                EcommerceErrorCode.PRODUCT_CATEGORY_ATTRIBUTE_DUPLICATE, findDuplicate(attributeIds));

        Map<Integer, ProductAttribute> found = productAttributeRepository.findAllById(uniqueIds).stream()
                .collect(Collectors.toMap(ProductAttribute::getId, a -> a));
        Validator.isTrue(found.size() == uniqueIds.size(), EcommerceErrorCode.PRODUCT_ATTRIBUTE_NOT_FOUND,
                "One or more product attributes were not found");

        category.getCategoryAttributes().clear();
        for (int i = 0; i < attributes.size(); i++) {
            AttributeAssignmentInput input = attributes.get(i);
            ProductCategoryAttribute assignment = new ProductCategoryAttribute();
            assignment.setCategory(category);
            assignment.setAttribute(found.get(input.attributeId()));
            assignment.setRequired(input.required());
            assignment.setDisplayOrder(i);
            category.getCategoryAttributes().add(assignment);
        }
    }

    private static Integer findDuplicate(List<Integer> ids) {
        Set<Integer> seen = new HashSet<>();
        for (Integer id : ids) {
            if (!seen.add(id)) {
                return id;
            }
        }
        return null;
    }

    private static void sortTreeNodes(List<ProductCategoryTreeNode> nodes) {
        nodes.sort(Comparator.comparing(n -> n.category().getName(), String.CASE_INSENSITIVE_ORDER));
        for (ProductCategoryTreeNode node : nodes) {
            if (!node.children().isEmpty()) {
                sortTreeNodes(node.children());
            }
        }
    }
}
