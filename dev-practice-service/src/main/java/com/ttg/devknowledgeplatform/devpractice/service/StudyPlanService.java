package com.ttg.devknowledgeplatform.devpractice.service;

import java.util.List;

import com.ttg.devknowledgeplatform.devpractice.entity.StudyPlan;

/**
 * Read access to study plans. Plans are seeded (see {@code service.seed.StudyPlanSeeder}); there is
 * no write path here yet — admin management is a planned follow-up.
 */
public interface StudyPlanService {

    /** Every plan, in overview order (position, then name). */
    List<StudyPlan> listAll();

    /**
     * @param slug the plan's URL slug
     * @return the plan with its sections and entries reachable (lazily) from it
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException {@code STUDY_PLAN_NOT_FOUND}
     *         if no plan has that slug
     */
    StudyPlan getBySlug(String slug);
}
