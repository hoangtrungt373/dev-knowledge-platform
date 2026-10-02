package com.ttg.devknowledgeplatform.devpractice.dto;

import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

/** An admin's reference solution for a problem — the problem itself comes from the URL path. */
@Data
public class ReferenceSubmissionRequest {

    @NotNull(message = "Language is required")
    private ProgrammingLanguage language;

    @NotBlank(message = "Source code is required")
    private String sourceCode;
}
