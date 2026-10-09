package com.ttg.devknowledgeplatform.devpractice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import com.ttg.devknowledgeplatform.devpractice.judge.Judge0Status;
import com.ttg.devknowledgeplatform.devpractice.judge.Judge0SubmissionResult;
import com.ttg.devknowledgeplatform.devpractice.judge.JudgeClient;
import com.ttg.devknowledgeplatform.devpractice.judge.JudgeClient.JudgeRequest;
import com.ttg.devknowledgeplatform.devpractice.judge.JudgeUnavailableException;
import com.ttg.devknowledgeplatform.devpractice.judge.OutputMatcher;
import com.ttg.devknowledgeplatform.devpractice.repository.ProblemRepository;
import com.ttg.devknowledgeplatform.devpractice.repository.ProblemTagRepository;
import com.ttg.devknowledgeplatform.devpractice.repository.SubmissionRepository;
import com.ttg.devknowledgeplatform.devpractice.service.RunResult;
import com.ttg.devknowledgeplatform.devpractice.service.SubmissionCommands;
import com.ttg.devknowledgeplatform.infra.service.SlugService;

/**
 * Run's contract: samples are judged against their answers (every one, not stopping at a failure),
 * custom inputs against the reference solution's answer, hidden cases are never touched, a Run is at
 * most two judge batches, and bad inputs / unpublished problems / an unreachable judge become clear
 * business errors.
 *
 * <p>Only the judge backend is faked — a lambda {@link JudgeClient} answering each request by its
 * stdin, with separate answers for the learner's and the reference's program. Everything above it is
 * real: the harness, {@link CaseJudge}'s status mapping, {@link OutputMatcher}'s comparison.
 */
class CodeRunServiceImplTest {

    /** Marks the reference solution's program apart from the learner's. */
    private static final String REFERENCE_CODE = "class Solution:\n    def identity(self, value):\n        return value  # reference";

    private final Map<String, Judge0SubmissionResult> learnerRuns = new HashMap<>();
    private final Map<String, Judge0SubmissionResult> referenceRuns = new HashMap<>();
    /** Every batch the service sent, in order. */
    private final List<List<JudgeRequest>> batches = new ArrayList<>();
    private RuntimeException judgeFailure;

    private final JudgeClient fakeJudge = requests -> {
        if (judgeFailure != null) {
            throw judgeFailure;
        }
        batches.add(requests);
        return requests.stream()
                .map(r -> (isReference(r) ? referenceRuns : learnerRuns).getOrDefault(r.stdin(), ran("0")))
                .toList();
    };

    private final ProblemRepository problemRepository = mock(ProblemRepository.class);
    private final SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
    // The real ProblemServiceImpl over the mocked repository: "a draft is not found" is its rule, and
    // these tests should exercise it, not a stub of it.
    private final ProblemServiceImpl problemService = new ProblemServiceImpl(problemRepository,
            mock(SubmissionRepository.class), mock(ProblemTagRepository.class), mock(SignatureNameValidator.class),
            mock(SlugService.class), new ObjectMapper());
    private final CodeRunServiceImpl service = new CodeRunServiceImpl(
            problemService,
            submissionRepository,
            new LanguageHarnessRegistry(List.of(new PythonLanguageHarness())),
            new CaseJudge(fakeJudge, new OutputMatcher(new ObjectMapper())),
            new ObjectMapper(),
            mock(PlatformTransactionManager.class));

    @Test
    void judgesEverySampleCaseAgainstItsAnswerAndNeverRunsHiddenOnes() {
        givenAPublishedProblem();
        learnerRuns.put("[1]", ran("1"));
        learnerRuns.put("[2]", ran("3"));

        RunResult run = service.run(command(null));

        assertThat(run.cases()).extracting(RunResult.Case::input).containsExactly("[1]", "[2]");
        assertThat(run.cases()).extracting(RunResult.Case::passed).containsExactly(true, false);
        assertThat(run.cases().get(1).status()).isEqualTo(SubmissionStatus.WRONG_ANSWER);
        assertThat(run.cases().get(1).expectedOutput()).isEqualTo("2");
        assertThat(run.cases().get(1).actualOutput()).isEqualTo("3");
        assertThat(run.cases()).extracting(RunResult.Case::expectedSource).containsOnly(ExpectedSource.SAMPLE);
        assertThat(batches).as("both samples in one batch, the hidden case in none").singleElement()
                .satisfies(batch -> assertThat(batch).extracting(JudgeRequest::stdin).containsExactly("[1]", "[2]"));
    }

    @Test
    void withoutAReferenceSolutionACustomInputIsOnlyRun() {
        givenAPublishedProblem();
        learnerRuns.put("[42]", ran("42"));

        RunResult run = service.run(command(List.of(" [42] ")));

        assertThat(run.cases()).singleElement().satisfies(c -> {
            assertThat(c.input()).isEqualTo("[42]");
            assertThat(c.expectedOutput()).isNull();
            assertThat(c.passed()).as("no answer to pass").isNull();
            assertThat(c.actualOutput()).isEqualTo("42");
            assertThat(c.expectedSource()).isEqualTo(ExpectedSource.UNAVAILABLE);
        });
        assertThat(batches).hasSize(1);
    }

    @Test
    void aCustomInputIsJudgedAgainstTheReferenceSolutionsAnswer() {
        givenAPublishedProblemWithAReference();
        learnerRuns.put("[42]", ran("41"));
        referenceRuns.put("[42]", ran("42\n"));

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
        learnerRuns.put("[7]", ran("7"));
        referenceRuns.put("[7]", ran("7"));

        RunResult run = service.run(command(List.of("[7]")));

        assertThat(run.cases()).singleElement().satisfies(c -> {
            assertThat(c.status()).isEqualTo(SubmissionStatus.ACCEPTED);
            assertThat(c.passed()).isTrue();
        });
    }

    @Test
    void whenTheReferenceFailsOnAnInputThereIsNoAnswerToCheck() {
        givenAPublishedProblemWithAReference();
        learnerRuns.put("[-1]", ran("-1"));
        referenceRuns.put("[-1]", new Judge0SubmissionResult(Judge0Status.RUNTIME_ERROR, null, "ValueError", null, null, null, null));

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
        learnerRuns.put("[3]", compileError());
        learnerRuns.put("[4]", compileError());

        RunResult run = service.run(command(List.of("[3]", "[4]")));

        assertThat(run.cases()).as("one program, one compile error — reported once").singleElement().satisfies(c -> {
            assertThat(c.status()).isEqualTo(SubmissionStatus.COMPILE_ERROR);
            assertThat(c.diagnostic()).isEqualTo("SyntaxError");
            assertThat(c.expectedSource()).isEqualTo(ExpectedSource.UNAVAILABLE);
        });
        assertThat(batches).as("no second, reference batch").hasSize(1);
    }

    @Test
    void aCustomInputThatEqualsASampleIsStillCheckedAgainstThatSamplesAnswer() {
        givenAPublishedProblemWithAReference();
        // "[ 2 ]" is sample "[2]" with different spacing — same JSON.
        learnerRuns.put("[ 2 ]", ran("2"));

        RunResult run = service.run(command(List.of("[ 2 ]")));

        assertThat(run.cases()).singleElement().satisfies(c -> {
            assertThat(c.expectedOutput()).isEqualTo("2");
            assertThat(c.passed()).isTrue();
            assertThat(c.expectedSource()).isEqualTo(ExpectedSource.SAMPLE);
        });
        assertThat(batches).as("nothing for the reference to answer").hasSize(1);
    }

    @Test
    void aRunIsAtMostTwoBatchesWhateverTheNumberOfCases() {
        givenAPublishedProblemWithAReference();
        List<String> inputs = List.of("[1]", "[10]", "[20]", "[30]", "[40]");
        inputs.forEach(input -> referenceRuns.put(input, ran(input.substring(1, input.length() - 1))));
        inputs.forEach(input -> learnerRuns.put(input, ran(input.substring(1, input.length() - 1))));

        RunResult run = service.run(command(inputs));

        assertThat(run.cases()).extracting(RunResult.Case::passed).containsOnly(true);
        assertThat(batches).hasSize(2);
        assertThat(batches.get(0)).as("round 1: the learner's code on all five").hasSize(5)
                .noneMatch(CodeRunServiceImplTest::isReference);
        assertThat(batches.get(1)).as("round 2: the reference, only on the four no sample answers")
                .allMatch(CodeRunServiceImplTest::isReference)
                .extracting(JudgeRequest::stdin).containsExactly("[10]", "[20]", "[30]", "[40]");
    }

    @Test
    void rejectsACustomInputThatIsNotOneValuePerParameter() {
        givenAPublishedProblem();

        assertThatThrownBy(() -> service.run(command(List.of("[1, 2]"))))
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(DevPracticeErrorCode.SUBMISSION_RUN_INPUT_INVALID))
                .hasMessageContaining("#1").hasMessageContaining("1 argument(s)");
        assertThatThrownBy(() -> service.run(command(List.of("not json"))))
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(DevPracticeErrorCode.SUBMISSION_RUN_INPUT_INVALID));
        assertThat(batches).isEmpty();
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
        givenAPublishedProblem();
        judgeFailure = new JudgeUnavailableException("Judge0 batch submit failed: 429 Too Many Requests");

        assertThatThrownBy(() -> service.run(command(null)))
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(DevPracticeErrorCode.JUDGE_UNAVAILABLE))
                .message().doesNotContain("429");
    }

    private void givenAPublishedProblem() {
        when(problemRepository.findById(5)).thenReturn(Optional.of(problem(ContentStatus.PUBLISHED)));
    }

    /** Problem 5 at contract v1 with an ACCEPTED Python reference. */
    private void givenAPublishedProblemWithAReference() {
        givenAPublishedProblem();
        when(submissionRepository.findFirstByProblem_IdAndKindAndStatusAndContractVersionOrderByIdDesc(
                5, SubmissionKind.REFERENCE, SubmissionStatus.ACCEPTED, 1))
                .thenReturn(Optional.of(Submission.builder()
                        .language(ProgrammingLanguage.PYTHON).sourceCode(REFERENCE_CODE).build()));
    }

    private static boolean isReference(JudgeRequest request) {
        return request.program().contains("# reference");
    }

    private static SubmissionCommands.Run command(List<String> customInputs) {
        return new SubmissionCommands.Run(5, ProgrammingLanguage.PYTHON, "class Solution: ...", customInputs);
    }

    /** A clean run that printed {@code stdout}. */
    private static Judge0SubmissionResult ran(String stdout) {
        return new Judge0SubmissionResult(Judge0Status.ACCEPTED, stdout, null, null, null, 10, 9000);
    }

    private static Judge0SubmissionResult compileError() {
        return new Judge0SubmissionResult(Judge0Status.COMPILATION_ERROR, null, null, "SyntaxError", null, null, null);
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
