package com.ttg.devknowledgeplatform.devpractice.dto;

import com.ttg.devknowledgeplatform.devpractice.enums.Difficulty;

/**
 * Lightweight row shape for problem list views (admin and public alike) — no description or test
 * cases, both of which are irrelevant until a specific problem is opened.
 */
public record ProblemSummaryResponse(Integer id, String slug, String title, Difficulty difficulty) {
}
