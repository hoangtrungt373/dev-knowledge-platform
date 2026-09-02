package com.ttg.devknowledgeplatform.ecommerce.service;

import com.ttg.devknowledgeplatform.ecommerce.entity.ProductCategory;

import java.util.List;

/**
 * Manages the product-category taxonomy, including its optional parent/child hierarchy
 * (self-referential adjacency list, mirroring {@code content-service}'s {@code CategoryService}),
 * and each category's own {@code ProductAttribute} schema assignment (the "Option B" global
 * attribute registry follow-up).
 *
 * <p>Returns entities rather than REST DTOs — {@code api}'s {@code ProductCategoryMapper} does
 * the entity-to-response mapping, matching {@code content-service}'s {@code CategoryService}.
 */
public interface ProductCategoryService {

    /**
     * One requested {@code ProductCategory} → {@code ProductAttribute} assignment — mirrors
     * {@code api}'s {@code CategoryAttributeAssignmentRequest} field-for-field but without any
     * REST/validation concerns, same pattern as {@code ProductCommands}. Carries no
     * {@code displayOrder} of its own — an assignment's order is its position in the submitted
     * list, applied by {@code ProductCategoryServiceImpl.applyCategoryAttributes}.
     */
    record AttributeAssignmentInput(Integer attributeId, boolean required) {
    }

    /**
     * Creates a new product category.
     *
     * @param name       the category name
     * @param parentId   the parent category's primary key, or {@code null} for a root category
     * @param attributes this category's attribute schema, in display order; {@code null} means
     *                   none assigned yet (a brand-new category has no prior state to "leave
     *                   unchanged", so unlike {@code update} there is no three-state semantics here)
     * @return the created category
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if the name conflicts with an existing category,
     *         or {@code attributes} contains a duplicate {@code attributeId}
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if {@code parentId} or any
     *         {@code attributeId} does not exist
     */
    ProductCategory create(String name, Integer parentId, List<AttributeAssignmentInput> attributes);

    /**
     * Renames an existing product category and/or reassigns its parent, regenerating its slug if
     * the name changed, and/or replaces its attribute schema.
     *
     * @param id         the category's primary key
     * @param name       the new name
     * @param parentId   the new parent category's primary key, or {@code null} to make this a root category
     * @param attributes this category's new, complete attribute schema, in display order;
     *                   {@code null} leaves the existing schema untouched (mirrors
     *                   {@code ProductCommands.Update.tagIds}'s own three-state semantics) —
     *                   pass an empty list to clear it
     * @return the updated category
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if {@code id}, {@code parentId},
     *         or any {@code attributeId} does not exist
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if the new name conflicts with another category,
     *         if {@code parentId} would make the category its own ancestor (a cycle), or if
     *         {@code attributes} contains a duplicate {@code attributeId}
     */
    ProductCategory update(Integer id, String name, Integer parentId, List<AttributeAssignmentInput> attributes);

    /**
     * Returns a single product category by its primary key.
     *
     * @param id the category's primary key
     * @return the matching category
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if not found
     */
    ProductCategory getById(Integer id);

    /**
     * Returns every product category matching an optional name/slug substring filter, sorted by
     * name. Unpaginated — this taxonomy is expected to stay small even with a hierarchy.
     *
     * @param q case-insensitive name/slug substring filter; {@code null} or blank returns all
     * @return matching categories, sorted by name
     */
    List<ProductCategory> list(String q);

    /**
     * Returns every product category as a tree of root categories with nested children, each
     * level sorted by name.
     *
     * @return the full category hierarchy
     */
    List<ProductCategoryTreeNode> listTree();
}
