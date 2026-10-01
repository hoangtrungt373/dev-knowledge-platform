package com.ttg.devknowledgeplatform.devpractice.dto;

import java.util.List;

import com.ttg.devknowledgeplatform.devpractice.enums.Difficulty;

/**
 * Lightweight row shape for the public problem list — no description or test
 * cases, both of which are irrelevant until a specific problem is opened. The admin list uses
 * {@link AdminProblemSummaryResponse} instead, which adds lifecycle fields.
 */
public record ProblemSummaryResponse(
        Integer id, String slug, String title, Difficulty difficulty, List<ProblemTagSummaryResponse> tags) {
}
