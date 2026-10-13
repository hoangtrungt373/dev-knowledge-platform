package com.ttg.devknowledgeplatform.devpractice.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionKind;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionStatus;

/** What a learner may see of an approach's code: accepted reference runs at the current contract only. */
class ProblemSolutionTest {

    private final Problem problem = Problem.builder().contractVersion(3).build();
    private final ProblemSolution solution = ProblemSolution.builder().problem(problem).title("Hash Set").build();

    @Test
    void showsOnlyRunsAcceptedAtTheCurrentContractVersionOrderedByLanguage() {
        Submission python = run(ProgrammingLanguage.PYTHON, SubmissionStatus.ACCEPTED, 3);
        Submission java = run(ProgrammingLanguage.JAVA, SubmissionStatus.ACCEPTED, 3);
        solution.addCode(python);
        solution.addCode(java);

        assertThat(solution.verifiedCode()).containsExactly(java, python);
    }

    @Test
    void hidesPendingRejectedAndStaleRuns() {
        solution.addCode(run(ProgrammingLanguage.JAVA, SubmissionStatus.PENDING, 3));
        solution.addCode(run(ProgrammingLanguage.PYTHON, SubmissionStatus.WRONG_ANSWER, 3));
        // Accepted, but against test data that has since changed (contract 2 → 3).
        solution.addCode(run(ProgrammingLanguage.JAVASCRIPT, SubmissionStatus.ACCEPTED, 2));

        assertThat(solution.verifiedCode()).isEmpty();
    }

    @Test
    void neverShowsANonReferenceRun() {
        Submission learners = run(ProgrammingLanguage.JAVA, SubmissionStatus.ACCEPTED, 3);
        learners.setKind(SubmissionKind.USER);
        solution.addCode(learners);

        assertThat(solution.verifiedCode()).isEmpty();
    }

    @Test
    void addCodeLinksTheRunBackToThisApproach() {
        Submission java = run(ProgrammingLanguage.JAVA, SubmissionStatus.ACCEPTED, 3);

        ProblemSolutionCode code = solution.addCode(java);

        assertThat(code.getSolution()).isSameAs(solution);
        assertThat(code.getSubmission()).isSameAs(java);
        assertThat(solution.getCodes()).containsExactly(code);
    }

    private Submission run(ProgrammingLanguage language, SubmissionStatus status, int contractVersion) {
        return Submission.builder()
                .problem(problem)
                .kind(SubmissionKind.REFERENCE)
                .language(language)
                .status(status)
                .contractVersion(contractVersion)
                .build();
    }
}
