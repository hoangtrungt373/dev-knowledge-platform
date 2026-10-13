package com.ttg.devknowledgeplatform.devpractice.service;

import java.util.List;

/** Input records for {@link ProblemSolutionService} — kept apart from REST DTOs, like {@link ProblemCommands}. */
public final class ProblemSolutionCommands {

    private ProblemSolutionCommands() {
    }

    /**
     * A new approach, appended after the problem's existing ones.
     *
     * @param title                  e.g. "Hash Set"
     * @param explanation            Markdown
     * @param referenceSubmissionIds the problem's own {@code REFERENCE} runs implementing it, at most
     *                               one per language; may be empty for an explanation-only approach
     */
    public record Create(String title, String explanation, List<Integer> referenceSubmissionIds) {
    }
}
