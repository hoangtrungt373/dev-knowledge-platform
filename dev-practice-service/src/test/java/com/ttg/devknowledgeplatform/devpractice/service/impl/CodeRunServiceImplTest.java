package com.ttg.devknowledgeplatform.devpractice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
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
import com.ttg.devknowledgeplatform.devpractice.entity.Submission;
import com.ttg.devknowledgeplatform.devpractice.entity.TestCase;
import com.ttg.devknowledgeplatform.devpractice.enums.ExpectedSource;
import com.ttg.devknowledgeplatform.devpractice.enums.ParamType;
import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionKind;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionStatus;
import com.ttg.devknowledgeplatform.devpractice.exception.DevPracticeErrorCode;
import com.ttg.devknowledgeplatform.devpractice.harness.LanguageHarnessRegistry;
import com.ttg.devknowledgeplatform.devpractice.harness.PythonLanguageHarness;
import com.ttg.devknowledgeplatform.devpractice.harness.SignatureNameValidator;
import com.ttg.devknowledgeplatform.devpractice.judge.CaseJudge;
import com.ttg.devknowledgeplatform.devpractice.judge.JudgeClient;
import com.ttg.devknowledgeplatform.devpractice.judge.JudgeUnavailableException;
import com.ttg.devknowledgeplatform.devpractice.judge.OutputMatcher;
import com.ttg.devknowledgeplatform.devpractice.repository.ProblemRepository;
import com.ttg.devknowledgeplatform.devpractice.repository.ProblemTagRepository;
import com.ttg.devknowledgeplatform.devpractice.repository.SubmissionRepository;
import com.ttg.devknowledgeplatform.infra.service.SlugService;
import com.ttg.devknowledgeplatform.devpractice.service.RunResult;
import com.ttg.devknowledgeplatform.devpractice.service.SubmissionCommands;

/**
 * Run's contract: samples are judged against their answers (every one, not stopping at a failure),
 * custom inputs against the reference solution's answer, hidden cases are never touched, and bad inputs / unpublished problems /
 * an unreachable judge become clear business errors. The judge itself is mocked; the harness is real.
 */
class CodeRunServiceImplTest {

    /** Marks the reference solution's program apart from the learner's in judge-call stubs. */
    private static final String REFERENCE_CODE = "class Solution:\n    def identity(self, value):\n        return value  # reference";

    private final ProblemRepository problemRepository = mock(ProblemRepository.class);
    private final SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
    private final CaseJudge caseJudge = mock(CaseJudge.class);
    // The real ProblemServiceImpl over the mocked repository: "a draft is not found" is its rule, and
    // these tests should exercise it, not a stub of it.
    private final ProblemServiceImpl problemService = new ProblemServiceImpl(problemRepository,
            mock(SubmissionRepository.class), mock(ProblemTagRepository.class), mock(SignatureNameValidator.class),
            mock(SlugService.class), new ObjectMapper());
    private final CodeRunServiceImpl service = new CodeRunServiceImpl(
            problemService,
            submissionRepository,
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
    void withoutAReferenceSolutionACustomInputIsOnlyRun() {
        when(problemRepository.findById(5)).thenReturn(Optional.of(problem(ContentStatus.PUBLISHED)));
        when(caseJudge.run(anyString(), any(), eq("[42]"), isNull(), any()))
                .thenReturn(result(SubmissionStatus.ACCEPTED, "42"));

        RunResult run = service.run(command(List.of(" [42] ")));

        assertThat(run.cases()).singleElement().satisfies(c -> {
            assertThat(c.input()).isEqualTo("[42]");
            assertThat(c.expectedOutput()).isNull();
            assertThat(c.passed()).as("no answer to pass").isNull();
            assertThat(c.actualOutput()).isEqualTo("42");
            assertThat(c.expectedSource()).isEqualTo(ExpectedSource.UNAVAILABLE);
        });
    }

    @Test
    void aCustomInputIsJudgedAgainstTheReferenceSolutionsAnswer() {
        givenAPublishedProblemWithAReference();
        whenLearnerReturns("[42]", result(SubmissionStatus.ACCEPTED, "41"));
        whenReferenceReturns("[42]", result(SubmissionStatus.ACCEPTED, "42\n"));

        RunResult run = service.run(command(List.of("[42]")));

        assertThat(run.cases()).singleElement().satisfies(c -> {
            assertThat(c.expectedOutput()).as("the reference's stdout, trimmed").isEqualTo("42");
            assertThat(c.actualOutput()).isEqualTo("41");
            assertThat(c.status()).isEqualTo(SubmissionStatus.WRONG_ANSWER);
            assertThat(c.passed()).isFalse();
            assertThat(c.expectedSource()).isEqualTo(ExpectedSource.REFERENCE);
        });
    }

    @Test
    void aCustomInputMatchingTheReferencePasses() {
        givenAPublishedProblemWithAReference();
        whenLearnerReturns("[7]", result(SubmissionStatus.ACCEPTED, "7"));
        whenReferenceReturns("[7]", result(SubmissionStatus.ACCEPTED, "7"));

        RunResult run = service.run(command(List.of("[7]")));

        assertThat(run.cases()).singleElement().satisfies(c -> {
            assertThat(c.status()).isEqualTo(SubmissionStatus.ACCEPTED);
            assertThat(c.passed()).isTrue();
        });
    }

    @Test
    void whenTheReferenceFailsOnAnInputThereIsNoAnswerToCheck() {
        givenAPublishedProblemWithAReference();
        whenLearnerReturns("[-1]", result(SubmissionStatus.ACCEPTED, "-1"));
        whenReferenceReturns("[-1]", new CaseJudge.CaseResult(SubmissionStatus.RUNTIME_ERROR, null, "ValueError"));

        RunResult run = service.run(command(List.of("[-1]")));

        assertThat(run.cases()).singleElement().satisfies(c -> {
            assertThat(c.expectedOutput()).isNull();
            assertThat(c.passed()).isNull();
            assertThat(c.status()).as("the learner's own run is still reported").isEqualTo(SubmissionStatus.ACCEPTED);
            assertThat(c.diagnostic()).as("the reference's error never leaks").isNull();
            assertThat(c.expectedSource()).isEqualTo(ExpectedSource.REFERENCE_FAILED);
        });
    }

    @Test
    void theReferenceIsNotRunWhenTheLearnersCodeDoesNotCompile() {
        givenAPublishedProblemWithAReference();
        whenLearnerReturns("[3]", new CaseJudge.CaseResult(SubmissionStatus.COMPILE_ERROR, null, "SyntaxError"));

        RunResult run = service.run(command(List.of("[3]", "[4]")));

        assertThat(run.cases()).singleElement()
                .satisfies(c -> assertThat(c.expectedSource()).isEqualTo(ExpectedSource.UNAVAILABLE));
        verify(caseJudge, never()).run(argThat((String p) -> p != null && p.contains("# reference")), any(), anyString(), any(), any());
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
            assertThat(c.expectedSource()).isEqualTo(ExpectedSource.SAMPLE);
        });
        verify(caseJudge, times(1)).run(anyString(), any(), anyString(), any(), any()); // no second, reference run
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

    /** Problem 5 at contract v1 with an ACCEPTED Python reference; compare() uses the real matcher. */
    private void givenAPublishedProblemWithAReference() {
        when(problemRepository.findById(5)).thenReturn(Optional.of(problem(ContentStatus.PUBLISHED)));
        when(submissionRepository.findFirstByProblem_IdAndKindAndStatusAndContractVersionOrderByIdDesc(
                5, SubmissionKind.REFERENCE, SubmissionStatus.ACCEPTED, 1))
                .thenReturn(Optional.of(Submission.builder()
                        .language(ProgrammingLanguage.PYTHON).sourceCode(REFERENCE_CODE).build()));
        CaseJudge realComparison = new CaseJudge(mock(JudgeClient.class), new OutputMatcher(new ObjectMapper()));
        when(caseJudge.compare(any(), anyString(), any())).thenAnswer(inv ->
                realComparison.compare(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
    }

    private void whenLearnerReturns(String input, CaseJudge.CaseResult result) {
        when(caseJudge.run(argThat((String p) -> p != null && !p.contains("# reference")), any(), eq(input), isNull(), any()))
                .thenReturn(result);
    }

    private void whenReferenceReturns(String input, CaseJudge.CaseResult result) {
        when(caseJudge.run(argThat((String p) -> p != null && p.contains("# reference")), any(), eq(input), isNull(), any()))
                .thenReturn(result);
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
