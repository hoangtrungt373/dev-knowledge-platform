package com.ttg.devknowledgeplatform.devpractice.dto;

import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionStatus;

/**
 * One linked run of an approach, for the admin editor. {@code verified} says whether learners see it
 * right now — accepted at the problem's current contract version ({@code ProblemSolution#verifiedCode()}).
 */
public record AdminSolutionCodeResponse(
        Integer submissionId,
        ProgrammingLanguage language,
        SubmissionStatus status,
        Integer contractVersion,
        boolean verified,
        Integer runtimeMs,
        Integer memoryKb) {
}
