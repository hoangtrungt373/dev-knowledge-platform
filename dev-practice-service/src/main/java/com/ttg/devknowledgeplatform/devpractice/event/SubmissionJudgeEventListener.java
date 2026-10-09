package com.ttg.devknowledgeplatform.devpractice.event;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.Hibernate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.entity.Submission;
import com.ttg.devknowledgeplatform.devpractice.entity.TestCase;
import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionStatus;
import com.ttg.devknowledgeplatform.devpractice.harness.LanguageHarness;
import com.ttg.devknowledgeplatform.devpractice.harness.LanguageHarnessRegistry;
import com.ttg.devknowledgeplatform.devpractice.judge.CaseJudge;
import com.ttg.devknowledgeplatform.devpractice.judge.JudgeUnavailableException;
import com.ttg.devknowledgeplatform.devpractice.repository.SubmissionRepository;
import com.ttg.devknowledgeplatform.devpractice.service.ProblemService;

import com.ttg.devknowledgeplatform.infra.event.AsyncEventHandler;

import lombok.extern.slf4j.Slf4j;

/**
 * Judges a {@link Submission} against every one of its {@link Problem}'s {@link TestCase}s. All test
 * cases go to the judge in one batch ({@link CaseJudge#runAll}); the verdict is the first failing case
 * in order, same as a real judge ("Wrong Answer on test case 3" style). Later cases run anyway — the
 * price of one batch instead of one round trip per case, and invisible to the user, since
 * {@code passedTestCases} still counts only the cases before the first failure.
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
 * {@code @Transactional}): the Judge0 batch in {@link #judge} can take several seconds (poll
 * interval × attempts, see {@code JudgeClientProperties}). Wrapping that whole loop
 * in one open transaction would hold a database connection (and row locks) for the entire judging
 * run — {@link #loadAndMarkRunning} and {@link #saveOutcome} are each their own quick transaction
 * instead, with the network-bound work happening in between while holding no transaction at all.
 *
 * <p><b>A submission never stays {@code RUNNING}:</b> any failure in the judging middle — the judge
 * backend being unavailable ({@link JudgeUnavailableException}) or an unexpected bug on this side —
 * is caught here and saved as {@link SubmissionStatus#JUDGE_ERROR}. Letting it propagate would reach
 * {@code AsyncEventHandler#handle}, which only logs, leaving the row {@code RUNNING} forever. (The
 * one remaining gap: if {@link #saveOutcome} itself fails, e.g. the database is down, or the process
 * dies mid-judging, the row still stays {@code RUNNING} — that needs a stale-{@code RUNNING} sweeper,
 * not handled here.)
 *
 * <p><b>Publish-on-accept:</b> an {@code ACCEPTED} run flagged {@link Submission#getPublishOnAccept()}
 * publishes its problem in the same transaction that saves the verdict, via
 * {@link ProblemService#publishIfVerified} — which re-checks that the problem is still a draft at the
 * judged contract version, so an edit made while judging can't be published unverified.
 */
@Component
@Slf4j
public class SubmissionJudgeEventListener extends AsyncEventHandler<SubmissionCreatedEvent> {

    private final SubmissionRepository submissionRepository;
    private final LanguageHarnessRegistry harnessRegistry;
    private final CaseJudge caseJudge;
    private final TransactionTemplate transactionTemplate;
    private final ProblemService problemService;

    public SubmissionJudgeEventListener(
            SubmissionRepository submissionRepository,
            ProblemService problemService,
            LanguageHarnessRegistry harnessRegistry,
            CaseJudge caseJudge,
            PlatformTransactionManager transactionManager) {
        this.submissionRepository = submissionRepository;
        this.problemService = problemService;
        this.harnessRegistry = harnessRegistry;
        this.caseJudge = caseJudge;
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

        JudgingOutcome outcome = judgeOrJudgeError(event.submissionId(), input);

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
        // Load the lazy collections while the session is still open — the returned JudgingInput
        // outlives this transaction, so problem becomes detached the moment this method returns;
        // an already-initialized collection stays readable on a detached entity, an untouched lazy
        // one would not. Initialize in place, never replace: Problem.parameters is orphanRemoval,
        // and swapping a managed entity's collection for a new list makes Hibernate refuse to
        // commit ("A collection with cascade=all-delete-orphan was no longer referenced by the
        // owning entity instance") — it tracks that exact collection object to compute deletions.
        Hibernate.initialize(problem.getParameters());
        List<TestCase> testCases = new ArrayList<>(problem.getTestCases());

        submission.setStatus(SubmissionStatus.RUNNING);
        submission.setTotalTestCases(testCases.size());
        // Recorded in the same transaction that loads the test cases above, so the stamp names
        // exactly the test data this verdict is about — what makes an ACCEPTED reference verify the
        // problem only at this contract version (see Problem#contractVersion).
        submission.setContractVersion(problem.getContractVersion());
        submissionRepository.save(submission);

        return new JudgingInput(problem, submission.getLanguage(), submission.getSourceCode(), testCases);
    }

    /** {@link #judge}, with every failure converted to {@link JudgingOutcome#JUDGE_ERROR} — see the class Javadoc. */
    private JudgingOutcome judgeOrJudgeError(Integer submissionId, JudgingInput input) {
        try {
            return judge(input);
        } catch (JudgeUnavailableException e) {
            log.warn("Judge unavailable for submission {}: {}", submissionId, e.getMessage(), e);
        } catch (RuntimeException e) {
            // A bug on our side (harness, matcher, ...) — still must not leave the row RUNNING.
            log.error("Unexpected failure judging submission {}", submissionId, e);
        }
        return JudgingOutcome.JUDGE_ERROR;
    }

    private JudgingOutcome judge(JudgingInput input) {
        LanguageHarness harness = harnessRegistry.get(input.language());
        String program = harness.buildProgram(input.problem(), input.sourceCode());

        // What one run means (status mapping, output comparison) lives in CaseJudge, shared with
        // CodeRunService's unsaved Run so the two can never disagree on a verdict.
        List<CaseJudge.CaseResult> results = caseJudge.runAll(input.testCases().stream()
                .map(tc -> new CaseJudge.CaseRequest(program, input.language(), tc.getInput(), tc.getExpectedOutput()))
                .toList(), input.problem().getReturnType());

        int passed = 0;
        for (CaseJudge.CaseResult result : results) {
            if (!result.passed()) {
                return new JudgingOutcome(result.status(), passed, result.diagnostic());
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
            if (outcome.status() == SubmissionStatus.ACCEPTED && Boolean.TRUE.equals(submission.getPublishOnAccept())) {
                problemService.publishIfVerified(submission.getProblem().getId(), submission.getContractVersion());
            }
        });
    }

    private record JudgingInput(
            Problem problem, ProgrammingLanguage language, String sourceCode, List<TestCase> testCases) {
    }

    private record JudgingOutcome(SubmissionStatus status, Integer passedTestCases, String errorMessage) {

        /**
         * Judging couldn't complete. {@code passedTestCases} is left null rather than a partial count —
         * a partial run isn't a meaningful score. The message is shown to the submitter, so it's
         * deliberately generic; the operator-facing cause is logged instead.
         */
        static final JudgingOutcome JUDGE_ERROR = new JudgingOutcome(SubmissionStatus.JUDGE_ERROR, null,
                "The judge is temporarily unavailable, so this submission could not be judged. Please resubmit.");
    }
}
