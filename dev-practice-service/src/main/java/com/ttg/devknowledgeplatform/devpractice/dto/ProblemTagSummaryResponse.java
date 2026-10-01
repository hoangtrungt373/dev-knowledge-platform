package com.ttg.devknowledgeplatform.devpractice.dto;

/**
 * A tag embedded in a problem response — just enough to render a chip and link/filter by it.
 * Embedded (not ids-only, unlike {@code ecommerce-service}'s {@code Product.tagIds}) so a problem
 * list can show topics without a second request to resolve names.
 */
public record ProblemTagSummaryResponse(Integer id, String name, String slug) {
}
