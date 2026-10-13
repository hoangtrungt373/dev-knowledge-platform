package com.ttg.devknowledgeplatform.devpractice.dto;

import java.util.List;

/**
 * An editorial approach as the admin editor sees it: the explanation exactly as stored (placeholders
 * intact, for editing), the same text rendered the way learners get it (for preview), and
 * <em>every</em> linked run — not just the verified ones — so a pending, rejected or stale link is
 * visible and can be replaced.
 */
public record AdminProblemSolutionResponse(
        Integer id,
        String title,
        String explanation,
        String renderedExplanation,
        Integer position,
        List<AdminSolutionCodeResponse> codes) {
}
