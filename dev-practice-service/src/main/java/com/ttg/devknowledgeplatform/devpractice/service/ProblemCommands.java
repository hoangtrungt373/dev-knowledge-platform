package com.ttg.devknowledgeplatform.devpractice.service;

import java.util.List;

import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.devpractice.enums.Difficulty;
import com.ttg.devknowledgeplatform.devpractice.enums.ParamType;

/**
 * Service-layer input records for {@link ProblemService} — never this module's own {@code dto/}
 * REST classes (see {@code content-service/CLAUDE.md}'s rule on keeping the service layer
 * decoupled from the REST/JSON contract, followed here too).
 */
public final class ProblemCommands {

    private ProblemCommands() {
    }

    public record Create(String title, String description, Difficulty difficulty,
            ContentStatus status, String methodName, ParamType returnType,
            List<MethodParameterInput> parameters, List<TestCaseInput> testCases) {
    }

    public record Update(String title, String description, Difficulty difficulty,
            ContentStatus status, String methodName, ParamType returnType,
            List<MethodParameterInput> parameters, List<TestCaseInput> testCases) {
    }

    public record TestCaseInput(String input, String expectedOutput, Boolean sample) {
    }

    /** {@code position} is the parameter's index in the method signature (0-based, in order). */
    public record MethodParameterInput(String name, ParamType type, Integer position) {
    }
}
