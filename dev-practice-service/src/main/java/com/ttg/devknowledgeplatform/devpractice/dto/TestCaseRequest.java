package com.ttg.devknowledgeplatform.devpractice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

/**
 * Request payload for one test case, nested inside {@link CreateProblemRequest}/
 * {@link UpdateProblemRequest} — a problem's test cases are supplied inline (real content, not a
 * reference to an existing row), unlike {@code content-service}'s tag-id-list pattern.
 */
@Data
public class TestCaseRequest {

    @NotBlank(message = "Input is required")
    private String input;

    @NotBlank(message = "Expected output is required")
    private String expectedOutput;

    @NotNull(message = "sample must be specified")
    private Boolean sample;
}
