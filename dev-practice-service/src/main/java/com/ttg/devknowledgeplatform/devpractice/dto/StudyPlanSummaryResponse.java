package com.ttg.devknowledgeplatform.devpractice.dto;

import java.util.List;

/**
 * One card on the study-plans overview. {@code problemIds} lists the plan's published problems in
 * plan order — the GUI checks them against the learner's own progress to show "x / y solved" without
 * a progress endpoint of its own, and {@code problemIds.size()} is the plan's size.
 */
public record StudyPlanSummaryResponse(
        Integer id, String slug, String name, String description, int sectionCount, List<Integer> problemIds) {
}
