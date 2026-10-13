package com.ttg.devknowledgeplatform.devpractice.dto;

import java.util.List;

/**
 * One card on the study-plans overview. {@code problems} lists the plan's published problems in plan
 * order (section by section) as {@code {id, slug}} — the GUI checks the ids against the learner's own
 * progress to show "x / y solved" and uses the slugs to open the first unsolved one, without a
 * progress endpoint of its own; {@code problems.size()} is the plan's size.
 */
public record StudyPlanSummaryResponse(
        Integer id,
        String slug,
        String name,
        String description,
        int sectionCount,
        List<StudyPlanProblemRefResponse> problems) {
}
