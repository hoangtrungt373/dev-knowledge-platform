package com.ttg.devknowledgeplatform.ecommerce.service;

import com.ttg.devknowledgeplatform.ecommerce.entity.ProductCategory;

import java.util.ArrayList;
import java.util.List;

/**
 * One node of the product-category hierarchy returned by {@link ProductCategoryService#listTree()}
 * — a {@link ProductCategory} paired with its already-resolved children, so callers don't need to
 * walk {@code parent}/lazy-loaded associations themselves. Mirrors {@code content-service}'s own
 * {@code CategoryTreeNode} exactly.
 */
public record ProductCategoryTreeNode(ProductCategory category, List<ProductCategoryTreeNode> children) {

    public ProductCategoryTreeNode(ProductCategory category) {
        this(category, new ArrayList<>());
    }
}
