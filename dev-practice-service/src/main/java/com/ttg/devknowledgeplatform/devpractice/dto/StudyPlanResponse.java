package com.ttg.devknowledgeplatform.devpractice.dto;

import java.util.List;

/** A whole study plan for its detail page: ordered sections, each with its ordered published problems. */
public record StudyPlanResponse(
        Integer id, String slug, String name, String description, List<StudyPlanSectionResponse> sections) {
}
