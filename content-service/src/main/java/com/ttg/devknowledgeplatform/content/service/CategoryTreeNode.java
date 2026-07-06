package com.ttg.devknowledgeplatform.content.service;

import java.util.ArrayList;
import java.util.List;

import com.ttg.devknowledgeplatform.content.entity.Category;

/**
 * One node of the category hierarchy returned by {@link CategoryService#listTree()} — a
 * {@link Category} paired with its already-resolved children, so callers don't need to walk
 * {@code parent}/lazy-loaded associations themselves.
 */
public record CategoryTreeNode(Category category, List<CategoryTreeNode> children) {

    public CategoryTreeNode(Category category) {
        this(category, new ArrayList<>());
    }
}
