package com.ttg.devknowledgeplatform.ecommerce.service;

import com.ttg.devknowledgeplatform.ecommerce.entity.ProductAttribute;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Manages the lifecycle of {@link ProductAttribute}s — the "Option B" global attribute registry
 * (e.g. "Color", with a controlled vocabulary of values), reusable across categories via
 * {@link com.ttg.devknowledgeplatform.ecommerce.entity.ProductCategoryAttribute}.
 *
 * <p>Category *assignment* doesn't live here — it travels with {@code ProductCategoryService
 * .create}/{@code .update}'s own {@code attributes} parameter, applied by
 * {@code ProductCategoryServiceImpl}, mirroring this module's own split between
 * {@code ProductTagService} (pure CRUD) and {@code ProductServiceImpl.applyTagIds} (assignment).
 *
 * <p>An attribute's values are managed as one whole list here (create/update both take the
 * complete {@code List<String>}, clearing and rebuilding {@link ProductAttribute#getValues()}) —
 * not independent add/remove-one-value endpoints — since they're always edited together in one
 * admin form. Returns entities rather than REST DTOs — {@code api}'s {@code ProductAttributeMapper}
 * does the entity-to-response mapping, matching {@code ProductTagService}.
 */
public interface ProductAttributeService {

    /**
     * Creates a new product attribute with its full value list.
     *
     * @param name   the attribute name (matched literally against a {@code ProductVariant
     *               .attributes} map key — see {@link ProductAttribute}'s own Javadoc)
     * @param values the attribute's controlled vocabulary, in display order; must be non-empty
     *               and contain no duplicates (case-insensitive)
     * @return the created attribute
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if the name conflicts with
     *         an existing attribute, {@code values} is empty, or contains a duplicate
     */
    ProductAttribute create(String name, List<String> values);

    /**
     * Renames an existing product attribute and/or replaces its value list.
     *
     * @param id     the attribute's primary key
     * @param name   the new name
     * @param values the new, complete controlled vocabulary, in display order
     * @return the updated attribute
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if {@code id} does not exist
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if the new name conflicts with
     *         another attribute, {@code values} is empty, or contains a duplicate
     */
    ProductAttribute update(Integer id, String name, List<String> values);

    /**
     * Returns a single product attribute by its primary key.
     *
     * @param id the attribute's primary key
     * @return the matching attribute
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if not found
     */
    ProductAttribute getById(Integer id);

    /**
     * Returns a paginated, optionally filtered list of product attributes.
     *
     * @param pageable pagination and sort parameters
     * @param q        case-insensitive name substring filter; {@code null} or blank returns all
     * @return a page of matching attributes
     */
    Page<ProductAttribute> list(Pageable pageable, String q);

    /**
     * Permanently deletes a product attribute.
     *
     * @param id the attribute's primary key
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if not found
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if the attribute is still
     *         assigned to any category
     */
    void delete(Integer id);
}
