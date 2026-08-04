package com.ttg.devknowledgeplatform.ecommerce.service;

import com.ttg.devknowledgeplatform.ecommerce.entity.Product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Manages the lifecycle of {@link Product}s and their variants/images.
 *
 * <p>Returns entities rather than REST DTOs — {@code api}'s {@code ProductMapper} does the
 * entity-to-response mapping, matching {@code content-service}'s {@code CategoryService}.
 */
public interface ProductService {

    /**
     * Creates a product together with its variants and image gallery in one operation.
     *
     * <p>Requires at least one variant (see {@code Product}'s Javadoc) and that every variant
     * shares the same set of attribute keys (US-1.6). SKUs are checked both for duplicates within
     * the request itself and for conflicts against existing variants.
     *
     * @param command the product, its variants, and its images to create
     * @return the created product
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if the category does not exist
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException on a slug/SKU conflict, a missing variant, or inconsistent attribute keys
     */
    Product create(ProductCommands.Create command);

    /**
     * Updates a product's basic fields (name, description, category). Does not touch variants or
     * images — those get their own mutation endpoints in a later slice.
     *
     * @param id      the product's primary key
     * @param command the new field values
     * @return the updated product
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if the product or category does not exist
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if the new name conflicts on slug
     */
    Product update(Integer id, ProductCommands.Update command);

    /**
     * Soft-deletes a product by setting {@code active} to {@code false} (US-1.7).
     *
     * @param id the product's primary key
     * @return the deactivated product
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if not found
     */
    Product deactivate(Integer id);

    /**
     * Returns a single product by its primary key.
     *
     * @param id the product's primary key
     * @return the matching product
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if not found
     */
    Product getById(Integer id);

    /**
     * Returns a paginated, optionally filtered list of products.
     *
     * @param pageable          pagination and sort parameters
     * @param productCategoryId optional category filter
     * @param active            optional active-flag filter
     * @param q                 optional case-insensitive name/slug substring filter
     * @return a page of matching products
     */
    Page<Product> list(Pageable pageable, Integer productCategoryId, Boolean active, String q);
}
