package com.ttg.devknowledgeplatform.devpractice.dto;

import java.util.List;

import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionStatus;

/** The result of an unsaved run — see {@code service.RunResult} for what each field means. */
public record RunResponse(List<CaseResponse> cases) {

    /** One input's run; {@code expectedOutput}/{@code passed} are null for a custom input. */
    public record CaseResponse(String input, String expectedOutput, String actualOutput, SubmissionStatus status,
                               Boolean passed, String diagnostic) {
    }
}
