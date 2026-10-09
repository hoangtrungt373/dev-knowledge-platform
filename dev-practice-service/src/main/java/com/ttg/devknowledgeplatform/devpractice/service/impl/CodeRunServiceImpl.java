package com.ttg.devknowledgeplatform.devpractice.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.common.exception.Validator;
import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.entity.Submission;
import com.ttg.devknowledgeplatform.devpractice.enums.ExpectedSource;
import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionKind;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionStatus;
import com.ttg.devknowledgeplatform.devpractice.exception.DevPracticeErrorCode;
import com.ttg.devknowledgeplatform.devpractice.harness.LanguageHarnessRegistry;
import com.ttg.devknowledgeplatform.devpractice.judge.CaseJudge;
import com.ttg.devknowledgeplatform.devpractice.judge.JudgeUnavailableException;
import com.ttg.devknowledgeplatform.devpractice.repository.SubmissionRepository;
import com.ttg.devknowledgeplatform.devpractice.service.CodeRunService;
import com.ttg.devknowledgeplatform.devpractice.service.ProblemService;
import com.ttg.devknowledgeplatform.devpractice.service.RunResult;
import com.ttg.devknowledgeplatform.devpractice.service.SubmissionCommands;

import lombok.extern.slf4j.Slf4j;

/**
 * Runs a learner's code against sample cases or custom inputs, synchronously and without saving.
 *
 * <p><b>Expected answers for custom inputs come from the reference solution</b> — the newest ACCEPTED
 * {@code REFERENCE} submission at the problem's current contract version, the same one that verified
 * the problem for publishing. It acts as a <i>test oracle</i>: it already passed every saved test, so
 * whatever it returns for a new input is taken as correct. A custom input equal to a sample still
 * uses the sample's stored answer (no extra judge call). The reference's source never leaves the
 * server — only its return value is shown, the way LeetCode shows "Expected" for any custom input.
 * Each custom input therefore costs up to two judge calls (learner's code, then the reference); the
 * reference is skipped when the learner's code didn't compile.
 *
 * <p><b>Same transaction shape as {@code SubmissionJudgeEventListener}:</b> the problem and its
 * reference are loaded in one short read-only transaction (collections initialized while the session
 * is open), and the judge calls — seconds each — happen afterwards with no transaction or database
 * connection held. {@link TransactionTemplate} rather than {@code @Transactional}, because the read
 * must end <i>inside</i> this method, before the slow part.
 *
 * <p>Cases run sequentially: a run has only a handful of inputs, and firing them all at Judge0 at once
 * is the fastest way to hit the hosted API's rate limit (429).
 */
@Service
@Slf4j
public class CodeRunServiceImpl implements CodeRunService {

    /** Keeps one Run cheap: each input is a full judge round trip (two, with the reference). */
    static final int MAX_CUSTOM_INPUTS = 5;
    private static final int MAX_INPUT_LENGTH = 10_000;

    private final ProblemService problemService;
    private final SubmissionRepository submissionRepository;
    private final LanguageHarnessRegistry harnessRegistry;
    private final CaseJudge caseJudge;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate readOnlyTx;

    public CodeRunServiceImpl(
            ProblemService problemService,
            SubmissionRepository submissionRepository,
            LanguageHarnessRegistry harnessRegistry,
            CaseJudge caseJudge,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.problemService = problemService;
        this.submissionRepository = submissionRepository;
        this.harnessRegistry = harnessRegistry;
        this.caseJudge = caseJudge;
        this.objectMapper = objectMapper;
        this.readOnlyTx = new TransactionTemplate(transactionManager);
        this.readOnlyTx.setReadOnly(true);
    }

    @Override
    public RunResult run(SubmissionCommands.Run command) {
        List<String> customInputs = command.customInputs() == null ? List.of() : command.customInputs();
        Validator.isTrue(customInputs.size() <= MAX_CUSTOM_INPUTS,
                DevPracticeErrorCode.SUBMISSION_RUN_TOO_MANY_INPUTS, (Object) MAX_CUSTOM_INPUTS);

        Loaded loaded = readOnlyTx.execute(status -> load(command.problemId()));
        Problem problem = loaded.problem();
        List<PlannedCase> plan = customInputs.isEmpty()
                ? samplesOf(problem)
                : validatedCustom(customInputs, problem.getParameters().size(), samplesOf(problem));

        Program learner = new Program(command.language(),
                harnessRegistry.get(command.language()).buildProgram(problem, command.sourceCode()));
        // Built on first use only: a run of samples (or of inputs equal to samples) never needs it.
        ReferenceOracle oracle = new ReferenceOracle(problem, loaded.reference());

        List<RunResult.Case> cases = new ArrayList<>();
        for (PlannedCase planned : plan) {
            RunResult.Case runCase = planned.expectedOutput() != null
                    ? runAgainstSample(learner, planned, problem)
                    : runAgainstReference(learner, planned.input(), oracle, problem);
            cases.add(runCase);
            // The same program fails to compile for every input — running the rest would only repeat it.
            if (runCase.status() == SubmissionStatus.COMPILE_ERROR) {
                break;
            }
        }
        return new RunResult(cases);
    }

    private RunResult.Case runAgainstSample(Program learner, PlannedCase planned, Problem problem) {
        CaseJudge.CaseResult result = judge(learner, planned.input(), planned.expectedOutput(), problem);
        return new RunResult.Case(planned.input(), planned.expectedOutput(), result.stdout(), result.status(),
                result.passed(), result.diagnostic(), ExpectedSource.SAMPLE);
    }

    /**
     * Runs the learner's code first; only when it compiled is the reference asked for the answer —
     * also when the learner's run crashed or timed out, so they still see what was expected.
     */
    private RunResult.Case runAgainstReference(Program learner, String input, ReferenceOracle oracle, Problem problem) {
        CaseJudge.CaseResult mine = judge(learner, input, null, problem);
        if (mine.status() == SubmissionStatus.COMPILE_ERROR || !oracle.available()) {
            return new RunResult.Case(input, null, mine.stdout(), mine.status(), null, mine.diagnostic(),
                    ExpectedSource.UNAVAILABLE);
        }

        Optional<String> expected = oracle.answerFor(input);
        if (expected.isEmpty()) {
            // The reference couldn't run it either — most likely the input breaks the problem's
            // constraints. Nothing to compare against; the learner's own run still shows.
            return new RunResult.Case(input, null, mine.stdout(), mine.status(), null, mine.diagnostic(),
                    ExpectedSource.REFERENCE_FAILED);
        }
        CaseJudge.CaseResult judged = caseJudge.compare(mine, expected.get(), problem.getReturnType());
        return new RunResult.Case(input, expected.get(), judged.stdout(), judged.status(), judged.passed(),
                judged.diagnostic(), ExpectedSource.REFERENCE);
    }

    /** One judge call, with an unreachable judge turned into the learner-facing business error. */
    private CaseJudge.CaseResult judge(Program program, String input, String expectedOutput, Problem problem) {
        try {
            return caseJudge.run(program.source(), program.language(), input, expectedOutput, problem.getReturnType());
        } catch (JudgeUnavailableException e) {
            // Operator detail to the log; the learner gets a generic "try again".
            log.warn("Judge unavailable during a run of problem {}: {}", problem.getId(), e.getMessage());
            throw new BusinessException(DevPracticeErrorCode.JUDGE_UNAVAILABLE);
        }
    }

    /**
     * The published problem (a draft/archived one is "not found", same non-leaking posture as
     * submitting to one) and its current reference solution, if any. Collections are initialized in
     * place (never replaced — see the listener's orphan-removal note) so they stay readable once the
     * problem is detached after this transaction.
     */
    private Loaded load(Integer problemId) {
        Problem problem = problemService.getPublishedById(problemId);
        Hibernate.initialize(problem.getParameters());
        Hibernate.initialize(problem.getTestCases());
        Optional<Submission> reference = submissionRepository
                .findFirstByProblem_IdAndKindAndStatusAndContractVersionOrderByIdDesc(
                        problemId, SubmissionKind.REFERENCE, SubmissionStatus.ACCEPTED, problem.getContractVersion());
        return new Loaded(problem, reference.map(s -> new ReferenceCode(s.getLanguage(), s.getSourceCode())));
    }

    private static List<PlannedCase> samplesOf(Problem problem) {
        return problem.getTestCases().stream()
                .filter(tc -> Boolean.TRUE.equals(tc.getSample()))
                .map(tc -> new PlannedCase(tc.getInput(), tc.getExpectedOutput()))
                .toList();
    }

    /**
     * Rejects a custom input the harness couldn't even feed to the method: it must be a JSON array
     * with one value per parameter (the same arity rule test cases follow). Value <i>types</i> aren't
     * checked here — a wrong type surfaces as a runtime error from the run itself, which is also
     * what a learner would see on LeetCode.
     *
     * <p>A custom input that <i>is</i> one of the samples (compared as parsed JSON, so spacing doesn't
     * matter) keeps that sample's answer — the learner GUI pre-fills its editable cases from the
     * samples, and running an unchanged one shouldn't cost a reference run.
     */
    private List<PlannedCase> validatedCustom(List<String> inputs, int parameterCount, List<PlannedCase> samples) {
        List<PlannedCase> plan = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            String input = inputs.get(i) == null ? "" : inputs.get(i).trim();
            String label = "#" + (i + 1);
            Validator.isTrue(!input.isEmpty() && input.length() <= MAX_INPUT_LENGTH,
                    DevPracticeErrorCode.SUBMISSION_RUN_INPUT_INVALID, label,
                    "it must be 1–" + MAX_INPUT_LENGTH + " characters");
            JsonNode node;
            try {
                node = objectMapper.readTree(input);
            } catch (JsonProcessingException e) {
                throw new BusinessException(DevPracticeErrorCode.SUBMISSION_RUN_INPUT_INVALID, (Object) label,
                        "it isn't valid JSON");
            }
            Validator.isTrue(node != null && node.isArray() && node.size() == parameterCount,
                    DevPracticeErrorCode.SUBMISSION_RUN_INPUT_INVALID, label,
                    "it must be a JSON array with " + parameterCount + " argument(s), one per parameter");
            plan.add(new PlannedCase(input, expectedFor(node, samples)));
        }
        return plan;
    }

    /** The answer of the sample whose input equals {@code input} as JSON, or {@code null} if none does. */
    private String expectedFor(JsonNode input, List<PlannedCase> samples) {
        for (PlannedCase sample : samples) {
            try {
                if (input.equals(objectMapper.readTree(sample.input()))) {
                    return sample.expectedOutput();
                }
            } catch (JsonProcessingException e) {
                // A malformed stored sample just can't be matched; the input still runs.
            }
        }
        return null;
    }

    /**
     * Answers custom inputs by running the reference solution. Its program is built lazily, once per
     * Run, and reused for every input — like a learner's program, it depends only on the signature.
     */
    private final class ReferenceOracle {

        private final Problem problem;
        private final ReferenceCode reference;
        private Program program;

        ReferenceOracle(Problem problem, Optional<ReferenceCode> reference) {
            this.problem = problem;
            this.reference = reference.orElse(null);
        }

        boolean available() {
            return reference != null;
        }

        /** The reference's answer for {@code input}, or empty if it didn't run cleanly on it. */
        Optional<String> answerFor(String input) {
            if (program == null) {
                program = new Program(reference.language(),
                        harnessRegistry.get(reference.language()).buildProgram(problem, reference.sourceCode()));
            }
            CaseJudge.CaseResult result = judge(program, input, null, problem);
            if (!result.passed() || result.stdout() == null || result.stdout().isBlank()) {
                log.info("Reference solution of problem {} failed on a custom input: {}", problem.getId(), result.status());
                return Optional.empty();
            }
            return Optional.of(result.stdout().strip());
        }
    }

    /** One input to run, and its expected answer when it's a sample case ({@code null} otherwise). */
    private record PlannedCase(String input, String expectedOutput) {
    }

    /** A built program and the language it's written in. */
    private record Program(ProgrammingLanguage language, String source) {
    }

    /** A reference solution's code, copied out of the entity so it's safe to use after the transaction. */
    private record ReferenceCode(ProgrammingLanguage language, String sourceCode) {
    }

    /** What the read-only transaction hands to the (slow) judging part. */
    private record Loaded(Problem problem, Optional<ReferenceCode> reference) {
    }
}
