package com.ttg.devknowledgeplatform.devpractice.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create or fully replace an editorial approach (admin). The explanation is Markdown with code only via
 * {@code {{code:LANGUAGE}}} placeholders; {@code referenceSubmissionIds} are this problem's own
 * REFERENCE runs, at most one per language — both checked by {@code ProblemSolutionService}.
 */
public record ProblemSolutionRequest(
        @NotBlank @Size(max = 150) String title,
        @NotBlank String explanation,
        @NotNull List<@NotNull Integer> referenceSubmissionIds) {
}
