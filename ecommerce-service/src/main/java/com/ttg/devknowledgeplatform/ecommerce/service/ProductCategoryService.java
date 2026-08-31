package com.ttg.devknowledgeplatform.ecommerce.service;

import com.ttg.devknowledgeplatform.ecommerce.entity.ProductCategory;

import java.util.List;

/**
 * Manages the product-category taxonomy, including its optional parent/child hierarchy
 * (self-referential adjacency list, mirroring {@code content-service}'s {@code CategoryService}).
 *
 * <p>Returns entities rather than REST DTOs — {@code api}'s {@code ProductCategoryMapper} does
 * the entity-to-response mapping, matching {@code content-service}'s {@code CategoryService}.
 */
public interface ProductCategoryService {

    /**
     * Creates a new product category.
     *
     * @param name     the category name
     * @param parentId the parent category's primary key, or {@code null} for a root category
     * @return the created category
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if the name conflicts with an existing category
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if {@code parentId} does not exist
     */
    ProductCategory create(String name, Integer parentId);

    /**
     * Renames an existing product category and/or reassigns its parent, regenerating its slug if
     * the name changed.
     *
     * @param id       the category's primary key
     * @param name     the new name
     * @param parentId the new parent category's primary key, or {@code null} to make this a root category
     * @return the updated category
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if {@code id} or {@code parentId} does not exist
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if the new name conflicts with another category,
     *         or if {@code parentId} would make the category its own ancestor (a cycle)
     */
    ProductCategory update(Integer id, String name, Integer parentId);

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
