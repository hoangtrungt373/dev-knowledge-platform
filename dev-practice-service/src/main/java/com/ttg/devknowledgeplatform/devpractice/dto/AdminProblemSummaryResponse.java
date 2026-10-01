package com.ttg.devknowledgeplatform.devpractice.dto;

import java.time.Instant;

import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.devpractice.enums.Difficulty;

/**
 * Row shape for the admin problem list ({@code GET /api/v1/admin/problems}) — the public
 * {@link ProblemSummaryResponse} plus the lifecycle fields only an admin needs ({@code status},
 * {@code publishedAt}, {@code createdAt}). Kept as its own type rather than widening the public
 * summary, so the public list never grows admin-only fields by accident (same "same resource,
 * different audience" split as {@code ecommerce-service}'s shopper vs. admin order endpoints).
 *
 * <p>Deliberately carries no test-case/parameter counts: both are lazy collections on
 * {@code Problem}, so counting them per row would issue one extra query per problem in the page
 * (an N+1 query problem).
 */
public record AdminProblemSummaryResponse(
        Integer id,
        String slug,
        String title,
        Difficulty difficulty,
        ContentStatus status,
        Instant publishedAt,
        Instant createdAt) {
}
