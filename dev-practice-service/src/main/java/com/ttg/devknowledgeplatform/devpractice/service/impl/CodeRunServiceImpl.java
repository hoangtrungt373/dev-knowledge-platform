package com.ttg.devknowledgeplatform.devpractice.service.impl;

import java.util.ArrayList;
import java.util.List;

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
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionStatus;
import com.ttg.devknowledgeplatform.devpractice.exception.DevPracticeErrorCode;
import com.ttg.devknowledgeplatform.devpractice.harness.LanguageHarnessRegistry;
import com.ttg.devknowledgeplatform.devpractice.judge.CaseJudge;
import com.ttg.devknowledgeplatform.devpractice.judge.JudgeUnavailableException;
import com.ttg.devknowledgeplatform.devpractice.service.CodeRunService;
import com.ttg.devknowledgeplatform.devpractice.service.ProblemService;
import com.ttg.devknowledgeplatform.devpractice.service.RunResult;
import com.ttg.devknowledgeplatform.devpractice.service.SubmissionCommands;

import lombok.extern.slf4j.Slf4j;

/**
 * Runs a learner's code against sample cases or custom inputs, synchronously and without saving.
 *
 * <p><b>Same transaction shape as {@code SubmissionJudgeEventListener}:</b> the problem is loaded in
 * one short read-only transaction (collections initialized while the session is open), and the
 * judge calls — seconds each — happen afterwards with no transaction or database connection held.
 * {@link TransactionTemplate} rather than {@code @Transactional}, because the read must end
 * <i>inside</i> this method, before the slow part.
 *
 * <p>Cases run sequentially: a run has only a handful of inputs, and firing them all at Judge0 at once
 * is the fastest way to hit the hosted API's rate limit (429).
 */
@Service
@Slf4j
public class CodeRunServiceImpl implements CodeRunService {

    /** Keeps one Run cheap: each input is a full judge round trip. */
    static final int MAX_CUSTOM_INPUTS = 5;
    private static final int MAX_INPUT_LENGTH = 10_000;

    private final ProblemService problemService;
    private final LanguageHarnessRegistry harnessRegistry;
    private final CaseJudge caseJudge;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate readOnlyTx;

    public CodeRunServiceImpl(
            ProblemService problemService,
            LanguageHarnessRegistry harnessRegistry,
            CaseJudge caseJudge,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.problemService = problemService;
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

        Problem problem = readOnlyTx.execute(status -> loadPublished(command.problemId()));
        List<PlannedCase> plan = customInputs.isEmpty()
                ? samplesOf(problem)
                : validatedCustom(customInputs, problem.getParameters().size(), samplesOf(problem));

        String program = harnessRegistry.get(command.language()).buildProgram(problem, command.sourceCode());
        List<RunResult.Case> cases = new ArrayList<>();
        for (PlannedCase planned : plan) {
            CaseJudge.CaseResult result;
            try {
                result = caseJudge.run(program, command.language(), planned.input(), planned.expectedOutput(),
                        problem.getReturnType());
            } catch (JudgeUnavailableException e) {
                // Operator detail to the log; the learner gets a generic "try again".
                log.warn("Judge unavailable during a run of problem {}: {}", problem.getId(), e.getMessage());
                throw new BusinessException(DevPracticeErrorCode.JUDGE_UNAVAILABLE);
            }
            Boolean passed = planned.expectedOutput() == null ? null : result.passed();
            cases.add(new RunResult.Case(planned.input(), planned.expectedOutput(), result.stdout(),
                    result.status(), passed, result.diagnostic()));
            // The same program fails to compile for every input — running the rest would only repeat it.
            if (result.status() == SubmissionStatus.COMPILE_ERROR) {
                break;
            }
        }
        return new RunResult(cases);
    }

    /** A draft/archived problem is "not found", same non-leaking posture as submitting to one. */
    private Problem loadPublished(Integer problemId) {
        Problem problem = problemService.getPublishedById(problemId);
        // Initialized in place (never replaced — see the listener's orphan-removal note) so they stay
        // readable once the problem is detached after this transaction.
        Hibernate.initialize(problem.getParameters());
        Hibernate.initialize(problem.getTestCases());
        return problem;
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
     * samples, and running an unchanged one should still say whether it passed.
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

    /** One input to run, and its expected answer when it's a sample case ({@code null} for custom). */
    private record PlannedCase(String input, String expectedOutput) {
    }
}
