package com.ttg.devknowledgeplatform.devpractice.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttg.devknowledgeplatform.devpractice.entity.MethodParameter;
import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.entity.Submission;
import com.ttg.devknowledgeplatform.devpractice.entity.TestCase;
import com.ttg.devknowledgeplatform.devpractice.enums.ParamType;
import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionStatus;
import com.ttg.devknowledgeplatform.devpractice.harness.LanguageHarnessRegistry;
import com.ttg.devknowledgeplatform.devpractice.harness.PythonLanguageHarness;
import com.ttg.devknowledgeplatform.devpractice.judge.JudgeClient;
import com.ttg.devknowledgeplatform.devpractice.judge.JudgeUnavailableException;
import com.ttg.devknowledgeplatform.devpractice.judge.Judge0Status;
import com.ttg.devknowledgeplatform.devpractice.judge.Judge0SubmissionResult;
import com.ttg.devknowledgeplatform.devpractice.judge.OutputMatcher;
import com.ttg.devknowledgeplatform.devpractice.repository.SubmissionRepository;

/**
 * Verifies {@link SubmissionJudgeEventListener}'s final-status bookkeeping — in particular that a
 * judge-side failure (or an unexpected bug) ends as {@link SubmissionStatus#JUDGE_ERROR} instead of
 * leaving the submission {@code RUNNING} forever. Uses a mocked {@link JudgeClient} and repository;
 * the harness and output matcher are the real ones.
 */
class SubmissionJudgeEventListenerTest {

    private final SubmissionRepository repository = mock(SubmissionRepository.class);
    private final JudgeClient judgeClient = mock(JudgeClient.class);
    private SubmissionJudgeEventListener listener;
    private Submission submission;

    @BeforeEach
    void setUp() {
        listener = new SubmissionJudgeEventListener(
                repository,
                new LanguageHarnessRegistry(List.of(new PythonLanguageHarness())),
                judgeClient,
                new OutputMatcher(new ObjectMapper()),
                mock(PlatformTransactionManager.class));

        Problem problem = Problem.builder().methodName("identity").returnType(ParamType.INT).build();
        problem.getParameters().add(MethodParameter.builder()
                .problem(problem).name("value").type(ParamType.INT).position(0).build());
        problem.getTestCases().add(TestCase.builder().problem(problem).input("[1]").expectedOutput("1").build());
        problem.getTestCases().add(TestCase.builder().problem(problem).input("[2]").expectedOutput("2").build());

        submission = Submission.builder()
                .problem(problem).userUuid("u-1").language(ProgrammingLanguage.PYTHON)
                .sourceCode("class Solution: ...").build();
        when(repository.findById(7)).thenReturn(Optional.of(submission));
    }

    @Test
    void acceptsWhenEveryTestCaseMatches() {
        when(judgeClient.run(anyString(), any(), anyString()))
                .thenReturn(accepted("1"), accepted("2"));

        listener.onEvent(new SubmissionCreatedEvent(7));

        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.ACCEPTED);
        assertThat(submission.getPassedTestCases()).isEqualTo(2);
        assertThat(submission.getTotalTestCases()).isEqualTo(2);
    }

    @Test
    void judgeUnavailableEndsAsJudgeErrorNotRunning() {
        when(judgeClient.run(anyString(), any(), anyString()))
                .thenReturn(accepted("1"))
                .thenThrow(new JudgeUnavailableException("Judge0 submit failed: 429 Too Many Requests"));

        listener.onEvent(new SubmissionCreatedEvent(7));

        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.JUDGE_ERROR);
        assertThat(submission.getPassedTestCases()).as("a partial run is not a score").isNull();
        assertThat(submission.getErrorMessage())
                .contains("temporarily unavailable")
                .as("operator detail must not reach the user").doesNotContain("429");
    }

    @Test
    void unexpectedFailureAlsoEndsAsJudgeError() {
        when(judgeClient.run(anyString(), any(), anyString())).thenThrow(new NullPointerException("bug"));

        listener.onEvent(new SubmissionCreatedEvent(7));

        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.JUDGE_ERROR);
    }

    private static Judge0SubmissionResult accepted(String stdout) {
        return new Judge0SubmissionResult(Judge0Status.ACCEPTED, stdout, null, null, null);
    }
}
