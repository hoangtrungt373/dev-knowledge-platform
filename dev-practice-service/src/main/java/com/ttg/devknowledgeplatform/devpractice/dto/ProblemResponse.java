package com.ttg.devknowledgeplatform.devpractice.dto;

import java.time.Instant;
import java.util.List;

import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.devpractice.enums.Difficulty;

/**
 * Full problem detail — used by both the admin ({@code getById}, all test cases) and public
 * ({@code getBySlug}, sample test cases only) endpoints. See
 * {@link com.ttg.devknowledgeplatform.devpractice.mapper.ProblemMapper#toPublicResponse} for the
 * filtering that keeps hidden test cases out of the public response.
 */
public record ProblemResponse(
        Integer id,
        String slug,
        String title,
        String description,
        Difficulty difficulty,
        ContentStatus status,
        List<TestCaseResponse> testCases,
        Instant publishedAt,
        Instant createdAt) {
}
