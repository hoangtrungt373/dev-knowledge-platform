package com.ttg.devknowledgeplatform.content.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.ttg.devknowledgeplatform.content.entity.Category;

/**
 * Manages the lifecycle of content categories, which form an arbitrarily deep tree.
 *
 * <p>Category names are case-insensitively unique across the entire tree. Each category
 * automatically receives a URL-safe slug derived from its name. A category cannot be
 * deleted while it has child categories or is referenced by content items.
 *
 * <p>Returns entities rather than REST DTOs — {@code api}'s {@code CategoryMapper} does the
 * entity-to-response mapping, matching how {@code social-service}'s {@code FriendService} and
 * {@code ai-service}'s {@code RagQueryService} return internal models that {@code api} maps to
 * its own response types.
 */
public interface CategoryService {

    /**
     * Creates a new category, optionally nested under a parent.
     *
     * @param name     the category name
     * @param parentId the parent category's id, or {@code null} for a root category
     * @return the created category
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if the name conflicts with an existing category
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if the specified parent does not exist
     */
    Category create(String name, Integer parentId);

    /**
     * Updates the name and/or parent of an existing category.
     *
     * <p>The slug is regenerated only when the name changes. Setting a descendant as
     * the new parent is rejected to prevent cycles.
     *
     * @param id       the category's primary key
     * @param name     the new name
     * @param parentId the new parent category's id, or {@code null} for a root category
     * @return the updated category
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if {@code id} or the new parent does not exist
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if the new name conflicts or the new parent would create a cycle
     */
    Category update(Integer id, String name, Integer parentId);

    /**
     * Returns a single category by its primary key.
     *
     * @param id the category's primary key
     * @return the matching category
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if not found
     */
    Category getById(Integer id);

    /**
     * Returns a paginated, optionally filtered flat list of categories.
     *
     * <p>{@code rootOnly} and {@code parentId} are mutually exclusive; passing both throws.
     *
     * @param pageable  pagination and sort parameters
     * @param parentId  filter to children of this parent; {@code null} returns all
     * @param rootOnly  when {@code true}, returns only root (parentless) categories
     * @param q         case-insensitive name substring filter; {@code null} or blank returns all
     * @return a page of matching categories
     */
    Page<Category> list(Pageable pageable, Integer parentId, Boolean rootOnly, String q);

    /**
     * Returns the full category hierarchy as a nested tree, sorted alphabetically at each level.
     *
     * <p>Fetches all categories in a single query and assembles the tree in memory.
     *
     * @return root nodes, each containing their children recursively
     */
    List<CategoryTreeNode> listTree();

    /**
     * Permanently deletes a category.
     *
     * @param id the category's primary key
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if not found
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if the category has children or is referenced by content items
     */
    void delete(Integer id);
}
