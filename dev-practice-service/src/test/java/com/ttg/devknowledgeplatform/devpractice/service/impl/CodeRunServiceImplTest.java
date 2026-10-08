package com.ttg.devknowledgeplatform.devpractice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devpractice.entity.MethodParameter;
import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.entity.TestCase;
import com.ttg.devknowledgeplatform.devpractice.enums.ParamType;
import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionStatus;
import com.ttg.devknowledgeplatform.devpractice.exception.DevPracticeErrorCode;
import com.ttg.devknowledgeplatform.devpractice.harness.LanguageHarnessRegistry;
import com.ttg.devknowledgeplatform.devpractice.harness.PythonLanguageHarness;
import com.ttg.devknowledgeplatform.devpractice.judge.CaseJudge;
import com.ttg.devknowledgeplatform.devpractice.judge.JudgeUnavailableException;
import com.ttg.devknowledgeplatform.devpractice.repository.ProblemRepository;
import com.ttg.devknowledgeplatform.devpractice.service.RunResult;
import com.ttg.devknowledgeplatform.devpractice.service.SubmissionCommands;

/**
 * Run's contract: samples are judged against their answers (every one, not stopping at a failure),
 * custom inputs are only run, hidden cases are never touched, and bad inputs / unpublished problems /
 * an unreachable judge become clear business errors. The judge itself is mocked; the harness is real.
 */
class CodeRunServiceImplTest {

    private final ProblemRepository problemRepository = mock(ProblemRepository.class);
    private final CaseJudge caseJudge = mock(CaseJudge.class);
    private final CodeRunServiceImpl service = new CodeRunServiceImpl(
            problemRepository,
            new LanguageHarnessRegistry(List.of(new PythonLanguageHarness())),
            caseJudge,
            new ObjectMapper(),
            mock(PlatformTransactionManager.class));

    @Test
    void judgesEverySampleCaseAgainstItsAnswerAndNeverRunsHiddenOnes() {
        when(problemRepository.findById(5)).thenReturn(Optional.of(problem(ContentStatus.PUBLISHED)));
        when(caseJudge.run(anyString(), any(), anyString(), anyString(), any()))
                .thenReturn(result(SubmissionStatus.ACCEPTED, "1"), result(SubmissionStatus.WRONG_ANSWER, "3"));

        RunResult run = service.run(command(null));

        assertThat(run.cases()).extracting(RunResult.Case::input).containsExactly("[1]", "[2]");
        assertThat(run.cases()).extracting(RunResult.Case::passed).containsExactly(true, false);
        assertThat(run.cases().get(1).expectedOutput()).isEqualTo("2");
        assertThat(run.cases().get(1).actualOutput()).isEqualTo("3");
        verify(caseJudge, never()).run(anyString(), any(), eq("[99]"), anyString(), any());
    }

    @Test
    void runsCustomInputsWithoutAnAnswerToCheck() {
        when(problemRepository.findById(5)).thenReturn(Optional.of(problem(ContentStatus.PUBLISHED)));
        when(caseJudge.run(anyString(), any(), eq("[42]"), isNull(), any()))
                .thenReturn(result(SubmissionStatus.ACCEPTED, "42"));

        RunResult run = service.run(command(List.of(" [42] ")));

        assertThat(run.cases()).singleElement().satisfies(c -> {
            assertThat(c.input()).isEqualTo("[42]");
            assertThat(c.expectedOutput()).isNull();
            assertThat(c.passed()).as("custom input has nothing to pass").isNull();
            assertThat(c.actualOutput()).isEqualTo("42");
        });
    }

    @Test
    void aCustomInputThatEqualsASampleIsStillCheckedAgainstThatSamplesAnswer() {
        when(problemRepository.findById(5)).thenReturn(Optional.of(problem(ContentStatus.PUBLISHED)));
        // "[ 2 ]" is sample "[2]" with different spacing — same JSON.
        when(caseJudge.run(anyString(), any(), eq("[ 2 ]"), eq("2"), any()))
                .thenReturn(result(SubmissionStatus.ACCEPTED, "2"));

        RunResult run = service.run(command(List.of("[ 2 ]")));

        assertThat(run.cases()).singleElement().satisfies(c -> {
            assertThat(c.expectedOutput()).isEqualTo("2");
            assertThat(c.passed()).isTrue();
        });
    }

    @Test
    void stopsAfterACompileErrorSinceEveryOtherInputWouldRepeatIt() {
        when(problemRepository.findById(5)).thenReturn(Optional.of(problem(ContentStatus.PUBLISHED)));
        when(caseJudge.run(anyString(), any(), anyString(), anyString(), any()))
                .thenReturn(new CaseJudge.CaseResult(SubmissionStatus.COMPILE_ERROR, null, "SyntaxError"));

        RunResult run = service.run(command(null));

        assertThat(run.cases()).singleElement()
                .satisfies(c -> assertThat(c.diagnostic()).isEqualTo("SyntaxError"));
        verify(caseJudge, times(1)).run(anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    void rejectsACustomInputThatIsNotOneValuePerParameter() {
        when(problemRepository.findById(5)).thenReturn(Optional.of(problem(ContentStatus.PUBLISHED)));

        assertThatThrownBy(() -> service.run(command(List.of("[1, 2]"))))
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(DevPracticeErrorCode.SUBMISSION_RUN_INPUT_INVALID))
                .hasMessageContaining("#1").hasMessageContaining("1 argument(s)");
        assertThatThrownBy(() -> service.run(command(List.of("not json"))))
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(DevPracticeErrorCode.SUBMISSION_RUN_INPUT_INVALID));
    }

    @Test
    void capsTheNumberOfCustomInputs() {
        List<String> tooMany = Collections.nCopies(CodeRunServiceImpl.MAX_CUSTOM_INPUTS + 1, "[1]");

        assertThatThrownBy(() -> service.run(command(tooMany)))
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(DevPracticeErrorCode.SUBMISSION_RUN_TOO_MANY_INPUTS));
        verify(problemRepository, never()).findById(any());
    }

    @Test
    void aDraftProblemIsNotFound() {
        when(problemRepository.findById(5)).thenReturn(Optional.of(problem(ContentStatus.DRAFT)));

        assertThatThrownBy(() -> service.run(command(null)))
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(DevPracticeErrorCode.PROBLEM_NOT_FOUND));
    }

    @Test
    void anUnreachableJudgeBecomesAGenericJudgeUnavailableError() {
        when(problemRepository.findById(5)).thenReturn(Optional.of(problem(ContentStatus.PUBLISHED)));
        when(caseJudge.run(anyString(), any(), anyString(), anyString(), any()))
                .thenThrow(new JudgeUnavailableException("Judge0 submit failed: 429 Too Many Requests"));

        assertThatThrownBy(() -> service.run(command(null)))
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(DevPracticeErrorCode.JUDGE_UNAVAILABLE))
                .message().doesNotContain("429");
    }

    private static SubmissionCommands.Run command(List<String> customInputs) {
        return new SubmissionCommands.Run(5, ProgrammingLanguage.PYTHON, "class Solution: ...", customInputs);
    }

    private static CaseJudge.CaseResult result(SubmissionStatus status, String stdout) {
        return new CaseJudge.CaseResult(status, stdout, null);
    }

    /** identity(value: int) -> int, two sample cases ([1]→1, [2]→2) and one hidden one ([99]→99). */
    private static Problem problem(ContentStatus status) {
        Problem problem = Problem.builder().title("Identity").methodName("identity").returnType(ParamType.INT)
                .status(status).build();
        problem.setId(5);
        problem.getParameters().add(MethodParameter.builder()
                .problem(problem).name("value").type(ParamType.INT).position(0).build());
        problem.getTestCases().add(TestCase.builder().problem(problem).input("[1]").expectedOutput("1").sample(true).build());
        problem.getTestCases().add(TestCase.builder().problem(problem).input("[2]").expectedOutput("2").sample(true).build());
        problem.getTestCases().add(TestCase.builder().problem(problem).input("[99]").expectedOutput("99").sample(false).build());
        return problem;
    }
}
