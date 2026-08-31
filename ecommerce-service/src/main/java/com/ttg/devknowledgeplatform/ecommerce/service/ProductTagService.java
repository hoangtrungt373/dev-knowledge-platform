package com.ttg.devknowledgeplatform.ecommerce.service;

import com.ttg.devknowledgeplatform.ecommerce.entity.ProductTag;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Manages the lifecycle of {@link ProductTag}s — a flat, free-form label a {@code Product} can be
 * tagged with (many-to-many, via {@code ProductTagAssignment}).
 *
 * <p>Tag *assignment* to a product doesn't live here — it travels with
 * {@link ProductCommands.Create}/{@link ProductCommands.Update}'s own {@code tagIds}, applied by
 * {@code ProductServiceImpl}, mirroring {@code content-service}'s split between {@code TagService}
 * (pure CRUD) and {@code QuestionAnswerServiceImpl.applyTagIds} (assignment).
 *
 * <p>Returns entities rather than REST DTOs — {@code api}'s {@code ProductTagMapper} does the
 * entity-to-response mapping, matching {@code content-service}'s {@code TagService}.
 */
public interface ProductTagService {

    /**
     * Creates a new product tag.
     *
     * @param name the tag name
     * @return the created tag
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if a tag with the same name already exists
     */
    ProductTag create(String name);

    /**
     * Renames an existing product tag, regenerating its slug.
     *
     * @param id   the tag's primary key
     * @param name the new name
     * @return the updated tag
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if {@code id} does not exist
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if the new name conflicts with another tag
     */
    ProductTag update(Integer id, String name);

    /**
     * Returns a single product tag by its primary key.
     *
     * @param id the tag's primary key
     * @return the matching tag
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if not found
     */
    ProductTag getById(Integer id);

    /**
     * Returns a paginated, optionally filtered list of product tags.
     *
     * @param pageable pagination and sort parameters
     * @param q        case-insensitive name/slug substring filter; {@code null} or blank returns all
     * @return a page of matching tags
     */
    Page<ProductTag> list(Pageable pageable, String q);

    /**
     * Permanently deletes a product tag.
     *
     * @param id the tag's primary key
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if not found
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if the tag is still assigned to any product
     */
    void delete(Integer id);
}
