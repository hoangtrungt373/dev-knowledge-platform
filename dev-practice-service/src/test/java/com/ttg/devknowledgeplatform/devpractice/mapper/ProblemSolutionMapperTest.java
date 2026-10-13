package com.ttg.devknowledgeplatform.devpractice.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.ttg.devknowledgeplatform.devpractice.dto.ProblemSolutionResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.SolutionCodeResponse;
import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.entity.ProblemSolution;
import com.ttg.devknowledgeplatform.devpractice.entity.Submission;
import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionKind;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionStatus;

/**
 * The learner's view of an approach: the code panel and the explanation's placeholders come from the
 * same verified runs, so the two can never disagree. (No other mapper is used, so the generated
 * implementation runs here without Spring.)
 */
class ProblemSolutionMapperTest {

    private final ProblemSolutionMapper mapper = new ProblemSolutionMapperImpl();
    private final Problem problem = Problem.builder().contractVersion(2).build();

    @Test
    void expandsThePlaceholderFromTheSameVerifiedRunTheCodePanelShows() {
        ProblemSolution solution = solution("Use a set.\n\n{{code:JAVA}}");
        solution.addCode(run(ProgrammingLanguage.JAVA, SubmissionStatus.ACCEPTED, 2, "class Solution {}"));

        ProblemSolutionResponse response = mapper.toResponse(solution);

        assertThat(response.explanation()).isEqualTo("Use a set.\n\n```java\nclass Solution {}\n```");
        assertThat(response.codes()).containsExactly(
                new SolutionCodeResponse(ProgrammingLanguage.JAVA, "class Solution {}", 12, 40960));
    }

    @Test
    void aStaleRunIsHiddenFromBothTheCodePanelAndTheExplanation() {
        ProblemSolution solution = solution("Use a set.\n\n{{code:JAVA}}");
        // Accepted against contract 1; the problem's test data has since moved to contract 2.
        solution.addCode(run(ProgrammingLanguage.JAVA, SubmissionStatus.ACCEPTED, 1, "class Old {}"));

        ProblemSolutionResponse response = mapper.toResponse(solution);

        assertThat(response.codes()).isEmpty();
        assertThat(response.explanation())
                .doesNotContain("class Old")
                .endsWith("_The Java code for this approach is being re-verified._");
    }

    private ProblemSolution solution(String explanation) {
        return ProblemSolution.builder().problem(problem).title("Hash Set").explanation(explanation).position(0).build();
    }

    private Submission run(ProgrammingLanguage language, SubmissionStatus status, int contractVersion, String code) {
        return Submission.builder()
                .problem(problem).kind(SubmissionKind.REFERENCE).language(language).status(status)
                .contractVersion(contractVersion).sourceCode(code).runtimeMs(12).memoryKb(40960)
                .build();
    }
}
