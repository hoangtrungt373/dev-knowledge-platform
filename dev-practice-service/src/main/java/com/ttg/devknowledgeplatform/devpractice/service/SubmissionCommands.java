package com.ttg.devknowledgeplatform.devpractice.service;

import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;

public final class SubmissionCommands {

    private SubmissionCommands() {
    }

    public record Create(Integer problemId, ProgrammingLanguage language, String sourceCode) {
    }
}
