package com.ttg.devknowledgeplatform.devpractice.dto;

import java.util.List;

/**
 * One chapter of a plan. {@code problems} reuses the catalog's row shape, so the plan page renders
 * a problem exactly the way the catalog list does; it may be empty while a section's problems are
 * still drafts.
 */
public record StudyPlanSectionResponse(
        Integer id, String title, String description, List<ProblemSummaryResponse> problems) {
}
