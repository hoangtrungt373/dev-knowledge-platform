package com.ttg.devknowledgeplatform.ecommerce.service;

import com.ttg.devknowledgeplatform.ecommerce.entity.Product;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductImage;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductVariant;

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
     * images — see {@link #addVariant}/{@link #removeVariant}/{@link #addImage}/
     * {@link #removeImage}/{@link #updateImageSortOrder} for those (US-1.6).
     *
     * @param id      the product's primary key
     * @param command the new field values
     * @return the updated product
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if the product or category does not exist
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if the new name conflicts on slug
     */
    Product update(Integer id, ProductCommands.Update command);

    /**
     * Adds one variant to an existing product (US-1.6 — variants are independently addable).
     *
     * @param productId the product's primary key
     * @param input     the variant to add
     * @return the added variant
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if the product does not exist
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException on a SKU conflict, or if its attribute
     *         keys don't match the product's existing variants
     */
    ProductVariant addVariant(Integer productId, ProductCommands.VariantInput input);

    /**
     * Removes one variant from a product (US-1.6 — variants are independently removable).
     * Rejected if it's the product's last remaining variant — see {@code Product}'s Javadoc for
     * why a product can never end up with zero variants.
     *
     * @param productId the product's primary key
     * @param variantId the variant's primary key
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if the product or variant does not exist
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if the variant belongs to a different
     *         product, or removing it would leave the product with zero variants
     */
    void removeVariant(Integer productId, Integer variantId);

    /**
     * Adds one image to a product's gallery (US-1.6 — images are independently addable).
     *
     * @param productId the product's primary key
     * @param input     the image to add
     * @return the added image
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if the product does not exist
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if its sort order conflicts with an existing image
     */
    ProductImage addImage(Integer productId, ProductCommands.ImageInput input);

    /**
     * Removes one image from a product's gallery (US-1.6 — images are independently removable).
     * Unlike variants, a product may end up with zero images — the gallery is optional.
     *
     * @param productId the product's primary key
     * @param imageId   the image's primary key
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if the product or image does not exist
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if the image belongs to a different product
     */
    void removeImage(Integer productId, Integer imageId);

    /**
     * Changes one image's position in the gallery (US-1.6 — images are independently reorderable).
     *
     * @param productId   the product's primary key
     * @param imageId     the image's primary key
     * @param newSortOrder the new sort order
     * @return the updated image
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if the product or image does not exist
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if the image belongs to a different
     *         product, or the new sort order conflicts with another image
     */
    ProductImage updateImageSortOrder(Integer productId, Integer imageId, Integer newSortOrder);

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
     * Returns a single <strong>active</strong> product by its slug, for the public detail
     * endpoint (US-1.2). An inactive product's slug resolves the same as a nonexistent one — a
     * deactivated product must not confirm its own past existence to a public, unauthenticated
     * caller.
     *
     * @param slug the product's URL-safe slug
     * @return the matching active product
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if not found or inactive
     */
    Product getActiveBySlug(String slug);

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
