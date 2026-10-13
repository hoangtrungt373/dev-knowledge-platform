package com.ttg.devknowledgeplatform.devpractice.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ttg.devknowledgeplatform.devpractice.dto.StudyPlanResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.StudyPlanSummaryResponse;

/**
 * Public, unauthenticated study plans — browsable logged out, like the problem catalog. Only
 * published problems are ever listed. Carries no learner progress: the GUI derives it from the
 * caller's own {@code GET /api/v1/submissions/progress}.
 */
@RequestMapping("/api/v1/public/study-plans")
public interface PublicStudyPlanApi {

    /**
     * @return {@code 200} with every plan in overview order, each with its published problem ids
     */
    @GetMapping
    ResponseEntity<List<StudyPlanSummaryResponse>> list();

    /**
     * @param slug the plan's URL slug
     * @return {@code 200} with the plan's ordered sections and their published problems, or
     *         {@code 404 STUDY_PLAN_001} if no plan has that slug
     */
    @GetMapping("/{slug}")
    ResponseEntity<StudyPlanResponse> getBySlug(@PathVariable String slug);
}
