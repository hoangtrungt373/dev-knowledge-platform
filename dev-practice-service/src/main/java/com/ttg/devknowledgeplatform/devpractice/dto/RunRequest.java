package com.ttg.devknowledgeplatform.devpractice.dto;

import java.util.List;

import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

/**
 * An unsaved run of a learner's code. Without {@code customInputs} it runs the problem's sample cases
 * (and checks the answers); with them it runs those inputs instead (no answer to check). The input
 * count/shape limits are enforced by {@code CodeRunService} so the messages can name the problem's
 * own parameter count.
 */
@Data
public class RunRequest {

    @NotNull(message = "Problem id is required")
    private Integer problemId;

    @NotNull(message = "Language is required")
    private ProgrammingLanguage language;

    @NotBlank(message = "Source code is required")
    private String sourceCode;

    /** JSON argument arrays, e.g. {@code [[1,2,3]]}; omit or leave empty to run the sample cases. */
    private List<String> customInputs;
}
