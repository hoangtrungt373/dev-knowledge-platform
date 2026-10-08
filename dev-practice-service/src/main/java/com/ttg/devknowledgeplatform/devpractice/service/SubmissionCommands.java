package com.ttg.devknowledgeplatform.devpractice.service;

import java.util.List;

import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;

public final class SubmissionCommands {

    private SubmissionCommands() {
    }

    public record Create(Integer problemId, ProgrammingLanguage language, String sourceCode) {
    }

    /**
     * An unsaved run.
     *
     * @param customInputs JSON argument arrays to run instead of the problem's sample cases;
     *                     {@code null} or empty = run the samples
     */
    public record Run(Integer problemId, ProgrammingLanguage language, String sourceCode, List<String> customInputs) {
    }
}
