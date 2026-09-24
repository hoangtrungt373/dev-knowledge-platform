package com.ttg.devknowledgeplatform.devpractice.event;

import java.util.ArrayList;
import java.util.List;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.ttg.devknowledgeplatform.devpractice.entity.MethodParameter;
import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.entity.Submission;
import com.ttg.devknowledgeplatform.devpractice.entity.TestCase;
import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionStatus;
import com.ttg.devknowledgeplatform.devpractice.harness.LanguageHarness;
import com.ttg.devknowledgeplatform.devpractice.harness.LanguageHarnessRegistry;
import com.ttg.devknowledgeplatform.devpractice.judge.JudgeClient;
import com.ttg.devknowledgeplatform.devpractice.judge.Judge0SubmissionResult;
import com.ttg.devknowledgeplatform.devpractice.judge.OutputMatcher;
import com.ttg.devknowledgeplatform.devpractice.repository.SubmissionRepository;

import com.ttg.devknowledgeplatform.infra.event.AsyncEventHandler;

import lombok.extern.slf4j.Slf4j;

/**
 * Judges a {@link Submission} against every one of its {@link Problem}'s {@link TestCase}s,
 * stopping at the first failure (same as a real judge — "Wrong Answer on test case 3" style).
 *
 * <p><b>Why this uses {@link TransactionalEventListener}, not this reactor's usual
 * {@code infra.event.EventHandler} composed annotation:</b> {@code @EventHandler} is plain
 * {@code @EventListener} + {@code @Async}, which fires the moment
 * {@code ApplicationEventPublisher.publishEvent} is called — potentially *before* the publishing
 * transaction ({@code SubmissionServiceImpl.create}'s own) has committed. On a background thread,
 * that race would mean {@link #loadAndMarkRunning} sometimes can't see the very row that was just
 * created, since a separate transaction under the default isolation level won't see another
 * transaction's uncommitted writes. {@code phase = AFTER_COMMIT} removes the race entirely — this
 * listener only ever fires once the row is durably committed and visible. {@code @Async} is kept
 * explicit (same {@code "asyncEventExecutor"} pool every other listener in this reactor uses) since
 * {@code @TransactionalEventListener} alone would otherwise run synchronously on the committing
 * thread, immediately after commit but still inside the original HTTP request.
 *
 * <p><b>Why the judging work itself is split into two short transactions around a long,
 * non-transactional middle</b> (via {@link TransactionTemplate}, not this class's own
 * {@code @Transactional}): the Judge0 round-trips in {@link #judge} can take several seconds per
 * test case (poll interval × attempts, see {@code JudgeClientProperties}). Wrapping that whole loop
 * in one open transaction would hold a database connection (and row locks) for the entire judging
 * run — {@link #loadAndMarkRunning} and {@link #saveOutcome} are each their own quick transaction
 * instead, with the network-bound work happening in between while holding no transaction at all.
 */
@Component
@Slf4j
public class SubmissionJudgeEventListener extends AsyncEventHandler<SubmissionCreatedEvent> {

    private final SubmissionRepository submissionRepository;
    private final LanguageHarnessRegistry harnessRegistry;
    private final JudgeClient judgeClient;
    private final OutputMatcher outputMatcher;
    private final TransactionTemplate transactionTemplate;

    public SubmissionJudgeEventListener(
            SubmissionRepository submissionRepository,
            LanguageHarnessRegistry harnessRegistry,
            JudgeClient judgeClient,
            OutputMatcher outputMatcher,
            PlatformTransactionManager transactionManager) {
        this.submissionRepository = submissionRepository;
        this.harnessRegistry = harnessRegistry;
        this.judgeClient = judgeClient;
        this.outputMatcher = outputMatcher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("asyncEventExecutor")
    public void onEvent(SubmissionCreatedEvent event) {
        handle(event);
    }

    @Override
    protected void doHandle(SubmissionCreatedEvent event) {
        JudgingInput input = transactionTemplate.execute(status -> loadAndMarkRunning(event.submissionId()));
        if (input == null) {
            log.warn("SubmissionCreatedEvent received for missing submission {}", event.submissionId());
            return;
        }

        JudgingOutcome outcome = judge(input);

        transactionTemplate.executeWithoutResult(status -> saveOutcome(event.submissionId(), outcome));
        log.info("Judged submission {}: {} ({}/{} test cases passed)",
                event.submissionId(), outcome.status(), outcome.passedTestCases(), input.testCases().size());
    }

    private JudgingInput loadAndMarkRunning(Integer submissionId) {
        Submission submission = submissionRepository.findById(submissionId).orElse(null);
        if (submission == null) {
            return null;
        }

        Problem problem = submission.getProblem();
        // Copy the lazy collections into plain lists while the session is still open — the
        // returned JudgingInput outlives this transaction, so problem/testCases become detached
        // the moment this method returns; already-initialized associations stay readable on a
        // detached entity, an un-touched lazy proxy would not.
        List<MethodParameter> parameters = new ArrayList<>(problem.getParameters());
        List<TestCase> testCases = new ArrayList<>(problem.getTestCases());
        problem.setParameters(parameters);

        submission.setStatus(SubmissionStatus.RUNNING);
        submission.setTotalTestCases(testCases.size());
        submissionRepository.save(submission);

        return new JudgingInput(problem, submission.getLanguage(), submission.getSourceCode(), testCases);
    }

    private JudgingOutcome judge(JudgingInput input) {
        LanguageHarness harness = harnessRegistry.get(input.language());
        String program = harness.buildProgram(input.problem(), input.sourceCode());

        int passed = 0;
        for (TestCase testCase : input.testCases()) {
            Judge0SubmissionResult result = judgeClient.run(program, input.language(), testCase.getInput());

            SubmissionStatus failure = switch (result.status()) {
                case COMPILATION_ERROR -> SubmissionStatus.COMPILE_ERROR;
                case TIME_LIMIT_EXCEEDED -> SubmissionStatus.TIME_LIMIT_EXCEEDED;
                case RUNTIME_ERROR, INTERNAL_ERROR, EXEC_FORMAT_ERROR -> SubmissionStatus.RUNTIME_ERROR;
                case ACCEPTED -> outputMatcher.matches(
                        result.stdout(), testCase.getExpectedOutput(), input.problem().getReturnType())
                        ? null : SubmissionStatus.WRONG_ANSWER;
                // Judge0 only reports WRONG_ANSWER when we supply expected_output (we never do —
                // see Judge0SubmissionResult's Javadoc), and IN_QUEUE/PROCESSING are non-final
                // statuses JudgeClient#run never returns; both are defensive fallbacks only.
                case IN_QUEUE, PROCESSING, WRONG_ANSWER -> SubmissionStatus.RUNTIME_ERROR;
            };

            if (failure != null) {
                return new JudgingOutcome(failure, passed, errorMessageOf(result));
            }
            passed++;
        }

        return new JudgingOutcome(SubmissionStatus.ACCEPTED, passed, null);
    }

    private void saveOutcome(Integer submissionId, JudgingOutcome outcome) {
        submissionRepository.findById(submissionId).ifPresent(submission -> {
            submission.setStatus(outcome.status());
            submission.setPassedTestCases(outcome.passedTestCases());
            submission.setErrorMessage(outcome.errorMessage());
            submissionRepository.save(submission);
        });
    }

    private static String errorMessageOf(Judge0SubmissionResult result) {
        if (result.compileOutput() != null && !result.compileOutput().isBlank()) {
            return result.compileOutput();
        }
        if (result.stderr() != null && !result.stderr().isBlank()) {
            return result.stderr();
        }
        return result.message();
    }

    private record JudgingInput(
            Problem problem, ProgrammingLanguage language, String sourceCode, List<TestCase> testCases) {
    }

    private record JudgingOutcome(SubmissionStatus status, Integer passedTestCases, String errorMessage) {
    }
}
