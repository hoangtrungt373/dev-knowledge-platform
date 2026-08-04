package com.ttg.devknowledgeplatform.ecommerce.service;

import com.ttg.devknowledgeplatform.ecommerce.entity.ProductCategory;

import java.util.List;

/**
 * Manages the flat product-category taxonomy (see {@code ProductCategory}'s Javadoc for why this
 * is a distinct entity from {@code content-service}'s hierarchical {@code Category}).
 *
 * <p>Returns entities rather than REST DTOs — {@code api}'s {@code ProductCategoryMapper} does
 * the entity-to-response mapping, matching {@code content-service}'s {@code CategoryService}.
 */
public interface ProductCategoryService {

    /**
     * Creates a new product category.
     *
     * @param name the category name
     * @return the created category
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if the name conflicts with an existing category
     */
    ProductCategory create(String name);

    /**
     * Renames an existing product category, regenerating its slug.
     *
     * @param id   the category's primary key
     * @param name the new name
     * @return the updated category
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if {@code id} does not exist
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if the new name conflicts with another category
     */
    ProductCategory update(Integer id, String name);

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
     * name. Unpaginated — this taxonomy is flat and expected to stay small, unlike the product
     * catalog itself.
     *
     * @param q case-insensitive name/slug substring filter; {@code null} or blank returns all
     * @return matching categories, sorted by name
     */
    List<ProductCategory> list(String q);
}
