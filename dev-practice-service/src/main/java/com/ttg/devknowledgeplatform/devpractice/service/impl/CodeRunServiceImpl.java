package com.ttg.devknowledgeplatform.devpractice.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
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
 *
 * <p><b>Same transaction shape as {@code SubmissionJudgeEventListener}:</b> the problem and its
 * reference are loaded in one short read-only transaction (collections initialized while the session
 * is open), and the judge calls — seconds each — happen afterwards with no transaction or database
 * connection held. {@link TransactionTemplate} rather than {@code @Transactional}, because the read
 * must end <i>inside</i> this method, before the slow part.
 *
 * <p><b>Two batch rounds, whatever the number of cases</b> ({@link CaseJudge#runAll}): round 1 runs
 * the learner's code on every case; round 2 runs the reference on the custom inputs that still need an
 * answer. Round 2 needs only round 1's compile check, not its outputs — but it waits for it on
 * purpose: a learner's code that doesn't compile (common while typing) then costs no reference runs
 * at all, which matters on a metered judge. Merging both rounds into one batch would halve the
 * latency at the price of those wasted runs.
 */
@Service
@Slf4j
public class CodeRunServiceImpl implements CodeRunService {

    /** Keeps one Run cheap: each custom input can mean two judge executions (learner + reference). */
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

        // Round 1 — the learner's code on every case. A sample's answer goes along, so CaseJudge
        // judges those cases right away; a custom input runs without one.
        List<CaseJudge.CaseResult> mine = judgeAll(
                plan.stream().map(p -> learner.request(p.input(), p.expectedOutput())).toList(), problem);

        // One program: if it doesn't compile for one input, it doesn't compile for any. Report it once.
        for (int i = 0; i < plan.size(); i++) {
            if (mine.get(i).status() == SubmissionStatus.COMPILE_ERROR) {
                PlannedCase planned = plan.get(i);
                return new RunResult(List.of(toCase(planned, mine.get(i),
                        planned.expectedOutput() != null ? ExpectedSource.SAMPLE : ExpectedSource.UNAVAILABLE)));
            }
        }

        // Round 2 — the reference's answers for the custom inputs no sample answers.
        List<String> unanswered = plan.stream()
                .filter(p -> p.expectedOutput() == null).map(PlannedCase::input).toList();
        Iterator<Answer> answers = new ReferenceOracle(problem, loaded.reference())
                .answersFor(unanswered).iterator();

        List<RunResult.Case> cases = new ArrayList<>(plan.size());
        for (int i = 0; i < plan.size(); i++) {
            PlannedCase planned = plan.get(i);
            cases.add(planned.expectedOutput() != null
                    ? toCase(planned, mine.get(i), ExpectedSource.SAMPLE)
                    : judgedAgainstReference(planned.input(), mine.get(i), answers.next(), problem));
        }
        return new RunResult(cases);
    }

    /**
     * A custom input's case, given the reference's answer for it: compared when there is one; when
     * there's none, the learner's own run is reported as-is. The answer is wanted even when the
     * learner's run crashed or timed out — they still see what was expected.
     */
    private RunResult.Case judgedAgainstReference(String input, CaseJudge.CaseResult mine, Answer answer,
                                                  Problem problem) {
        PlannedCase unanswered = new PlannedCase(input, null);
        return switch (answer) {
            case Answer.NoReference noReference -> toCase(unanswered, mine, ExpectedSource.UNAVAILABLE);
            // The reference couldn't run it either — most likely the input breaks the problem's
            // constraints. Nothing to compare against; the learner's own run still shows.
            case Answer.ReferenceFailed failed -> toCase(unanswered, mine, ExpectedSource.REFERENCE_FAILED);
            case Answer.Known(String expected) -> toCase(new PlannedCase(input, expected),
                    caseJudge.compare(mine, expected, problem.getReturnType()), ExpectedSource.REFERENCE);
        };
    }

    /** {@code passed} is only meaningful when there was an answer to check against. */
    private static RunResult.Case toCase(PlannedCase planned, CaseJudge.CaseResult result, ExpectedSource source) {
        Boolean passed = planned.expectedOutput() == null ? null : result.passed();
        return new RunResult.Case(planned.input(), planned.expectedOutput(), result.stdout(), result.status(),
                passed, result.diagnostic(), source);
    }

    /** One batch, with an unreachable judge turned into the learner-facing business error. */
    private List<CaseJudge.CaseResult> judgeAll(List<CaseJudge.CaseRequest> requests, Problem problem) {
        try {
            return caseJudge.runAll(requests, problem.getReturnType());
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
     * Answers custom inputs by running the reference solution — one batch for all of them, with its
     * program built once (like a learner's, it depends only on the signature).
     */
    private final class ReferenceOracle {

        private final Problem problem;
        private final ReferenceCode reference;

        ReferenceOracle(Problem problem, Optional<ReferenceCode> reference) {
            this.problem = problem;
            this.reference = reference.orElse(null);
        }

        /** One {@link Answer} per input, in order. No inputs, or no reference, means no judge call. */
        List<Answer> answersFor(List<String> inputs) {
            if (reference == null) {
                return Collections.nCopies(inputs.size(), Answer.NO_REFERENCE);
            }
            if (inputs.isEmpty()) {
                return List.of();
            }
            Program program = new Program(reference.language(),
                    harnessRegistry.get(reference.language()).buildProgram(problem, reference.sourceCode()));
            List<CaseJudge.CaseResult> results = judgeAll(
                    inputs.stream().map(input -> program.request(input, null)).toList(), problem);
            return results.stream().map(this::answerOf).toList();
        }

        private Answer answerOf(CaseJudge.CaseResult result) {
            if (!result.passed() || result.stdout() == null || result.stdout().isBlank()) {
                log.info("Reference solution of problem {} failed on a custom input: {}", problem.getId(), result.status());
                return Answer.REFERENCE_FAILED;
            }
            return new Answer.Known(result.stdout().strip());
        }
    }

    /**
     * The reference's verdict on one custom input — three cases, not an {@code Optional} plus a null:
     * a sealed interface makes the {@code switch} in {@link #judgedAgainstReference} exhaustive, so a
     * fourth case would be a compile error there rather than a silently unhandled branch.
     */
    private sealed interface Answer {

        Answer NO_REFERENCE = new NoReference();
        Answer REFERENCE_FAILED = new ReferenceFailed();

        /** The reference ran cleanly; {@code output} is the expected answer. */
        record Known(String output) implements Answer {
        }

        /** The reference errored or timed out on this input. */
        record ReferenceFailed() implements Answer {
        }

        /** The problem has no accepted reference at its current version. */
        record NoReference() implements Answer {
        }
    }

    /** One input to run, and its expected answer when it's a sample case ({@code null} otherwise). */
    private record PlannedCase(String input, String expectedOutput) {
    }

    /** A built program and the language it's written in. */
    private record Program(ProgrammingLanguage language, String source) {

        CaseJudge.CaseRequest request(String input, String expectedOutput) {
            return new CaseJudge.CaseRequest(source, language, input, expectedOutput);
        }
    }

    /** A reference solution's code, copied out of the entity so it's safe to use after the transaction. */
    private record ReferenceCode(ProgrammingLanguage language, String sourceCode) {
    }

    /** What the read-only transaction hands to the (slow) judging part. */
    private record Loaded(Problem problem, Optional<ReferenceCode> reference) {
    }
}
