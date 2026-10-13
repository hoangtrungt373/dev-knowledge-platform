package com.ttg.devknowledgeplatform.devpractice.service;

import java.util.List;

import com.ttg.devknowledgeplatform.devpractice.entity.ProblemSolution;

/**
 * Editorial approaches for a problem. Learners read them for published problems only; the seeder and
 * the admin endpoints write them, both through this service so they share one set of rules.
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

    /**
     * A problem's approaches in display order, any problem status — the admin editor's view.
     *
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException {@code PROBLEM_NOT_FOUND}
     */
    List<ProblemSolution> listForAdmin(Integer problemId);

    /**
     * Replaces an approach's title, explanation and linked runs, under the same rules as {@link #create}.
     * Links that stay are kept as-is (not deleted and re-inserted).
     *
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException {@code PROBLEM_SOLUTION_NOT_FOUND}
     *         if the approach doesn't belong to {@code problemId}, plus every {@link #create} error
     */
    ProblemSolution update(Integer problemId, Integer solutionId, ProblemSolutionCommands.Update command);

    /**
     * Deletes an approach (never its linked runs) and renumbers the rest 0..n-1.
     *
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException {@code PROBLEM_SOLUTION_NOT_FOUND}
     */
    void delete(Integer problemId, Integer solutionId);

    /**
     * Puts a problem's approaches in the given order.
     *
     * @param orderedIds every one of the problem's approach ids, exactly once, in the new order
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException {@code PROBLEM_SOLUTION_ORDER_MISMATCH}
     *         if the list isn't exactly the problem's approaches (e.g. a stale page)
     */
    List<ProblemSolution> reorder(Integer problemId, List<Integer> orderedIds);
}
