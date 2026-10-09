package com.ttg.devknowledgeplatform.devpractice.service;

import java.util.List;

import com.ttg.devknowledgeplatform.devpractice.enums.ExpectedSource;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionStatus;

/**
 * The outcome of an unsaved run — one entry per input that was run. Nothing here is persisted.
 *
 * @param cases one entry per input, in order; stops early after a compile error, which every
 *              later input would only repeat
 */
public record RunResult(List<Case> cases) {

    /**
     * One input's run.
     *
     * @param input          the JSON argument array that was run
     * @param expectedOutput the expected answer: a sample's stored answer, or what the reference solution
     *                       returned for a custom input; {@code null} when none could be found (see
     *                       {@code expectedSource})
     * @param actualOutput   what the code returned (its stdout), or {@code null} if it never got there
     * @param status         {@code ACCEPTED} when it ran cleanly and (for a sample) matched;
     *                       otherwise the failure, using the same vocabulary as a graded submission
     * @param passed         {@code true}/{@code false} when an answer was known, {@code null} otherwise
     *                       (there's nothing to pass)
     * @param diagnostic     compiler output, stderr or the judge's message — shown to the learner
     * @param expectedSource where {@code expectedOutput} came from, or why there is none
     */
    public record Case(String input, String expectedOutput, String actualOutput, SubmissionStatus status,
                       Boolean passed, String diagnostic, ExpectedSource expectedSource) {
    }
}
