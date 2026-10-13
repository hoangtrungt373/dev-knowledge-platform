package com.ttg.devknowledgeplatform.devpractice.service;

import java.util.List;

import com.ttg.devknowledgeplatform.devpractice.entity.ProblemSolution;

/**
 * Editorial approaches for a problem. Learners read them for published problems only; writing is the
 * seeder today (admin management is a planned follow-up, and will go through {@link #create} too).
 */
public interface ProblemSolutionService {

    /**
     * A published problem's approaches in display order — each one's learner-visible code is
     * {@link ProblemSolution#verifiedCode()}.
     *
     * @throws com.ttg.devknowledgeplatform.common.exception.BusinessException {@code PROBLEM_NOT_FOUND}
     *         for an unknown or unpublished slug (the same "draft looks missing" rule as the problem itself)
     */
    List<ProblemSolution> listPublishedBySlug(String slug);

    /**
     * Adds an approach to a problem (any status — solutions can be written before publishing).
     *
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException {@code PROBLEM_NOT_FOUND},
     *         {@code PROBLEM_SOLUTION_INVALID_REFERENCE} if a submission isn't one of this problem's
     *         reference runs, or {@code PROBLEM_SOLUTION_DUPLICATE_LANGUAGE} for two runs in one language
     */
    ProblemSolution create(Integer problemId, ProblemSolutionCommands.Create command);
}
