package com.ttg.devknowledgeplatform.devpractice.service;

/**
 * "Run" — tries a learner's code against a published problem's sample cases, or against inputs of
 * their own, without creating a submission. Unlike a graded submission it is synchronous (the
 * request waits for the judge) and leaves nothing behind: no row, no verdict history, no effect on
 * solved/attempted progress. Hidden test cases are never used.
 */
public interface CodeRunService {

    /**
     * @throws com.ttg.devknowledgeplatform.common.exception.BusinessException {@code PROBLEM_NOT_FOUND}
     *         (missing or not published), {@code SUBMISSION_RUN_TOO_MANY_INPUTS},
     *         {@code SUBMISSION_RUN_INPUT_INVALID} (not a JSON array with one value per parameter),
     *         {@code JUDGE_UNAVAILABLE}
     */
    RunResult run(SubmissionCommands.Run command);
}
