package com.ttg.devknowledgeplatform.devpractice.judge;

import java.util.List;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.devpractice.enums.ParamType;
import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionStatus;

import lombok.RequiredArgsConstructor;

/**
 * Judges inputs against already-built programs: runs them on the judge backend (in one batch — see
 * {@link JudgeClient}), maps the backend's status to this module's {@link SubmissionStatus}, and
 * compares each output structurally.
 * The single place that decides "what does this one run mean", shared by graded submissions
 * ({@code SubmissionJudgeEventListener}, whose verdict is the first failure) and unsaved Run requests
 * ({@code CodeRunService}, which reports every case) — so the two can never disagree on a verdict.
 */
@Component
@RequiredArgsConstructor
public class CaseJudge {

    private final JudgeClient judgeClient;
    private final OutputMatcher outputMatcher;

    /**
     * Runs every request in one batch and judges each result.
     *
     * @param requests   what to run; may mix programs and languages (a learner's and a reference's)
     * @param returnType the problem's return type — decides how outputs are compared
     * @return one result per request, in the same order
     * @throws JudgeUnavailableException if the judge backend can't be reached
     */
    public List<CaseResult> runAll(List<CaseRequest> requests, ParamType returnType) {
        if (requests.isEmpty()) {
            return List.of();
        }
        List<Judge0SubmissionResult> results = judgeClient.runAll(requests.stream()
                .map(r -> new JudgeClient.JudgeRequest(r.program(), r.language(), r.input()))
                .toList());
        return IntStream.range(0, requests.size())
                .mapToObj(i -> judge(results.get(i), requests.get(i).expectedOutput(), returnType))
                .toList();
    }

    /** What one finished run means: Judge0's status mapped to ours, then the answer check. */
    private CaseResult judge(Judge0SubmissionResult result, String expectedOutput, ParamType returnType) {
        SubmissionStatus status = switch (result.status()) {
            case COMPILATION_ERROR -> SubmissionStatus.COMPILE_ERROR;
            case TIME_LIMIT_EXCEEDED -> SubmissionStatus.TIME_LIMIT_EXCEEDED;
            case RUNTIME_ERROR, INTERNAL_ERROR, EXEC_FORMAT_ERROR -> SubmissionStatus.RUNTIME_ERROR;
            case ACCEPTED -> expectedOutput == null
                    || outputMatcher.matches(result.stdout(), expectedOutput, returnType)
                    ? SubmissionStatus.ACCEPTED : SubmissionStatus.WRONG_ANSWER;
            // Judge0 only reports WRONG_ANSWER when we supply expected_output (we never do — see
            // Judge0SubmissionResult's Javadoc), and IN_QUEUE/PROCESSING are non-final statuses
            // JudgeClient#runAll never returns; both are defensive fallbacks only.
            case IN_QUEUE, PROCESSING, WRONG_ANSWER -> SubmissionStatus.RUNTIME_ERROR;
        };
        return new CaseResult(status, result.stdout(), diagnosticOf(result), result.runtimeMs(), result.memoryKb());
    }

    /**
     * Judges a run that already happened against an answer that only became known afterwards (a custom
     * input whose answer the reference solution computed). Uses the same {@link OutputMatcher} as
     * {@link #runAll}, so "correct" means the same thing either way. A run that didn't finish cleanly is
     * returned unchanged — there is no output to compare.
     *
     * @param ran            the learner's run, made with no expected output
     * @param expectedOutput the JSON-encoded answer to compare against
     * @return {@code ran} itself when it matches (or never ran cleanly), else the same run as {@code WRONG_ANSWER}
     */
    public CaseResult compare(CaseResult ran, String expectedOutput, ParamType returnType) {
        if (!ran.passed() || outputMatcher.matches(ran.stdout(), expectedOutput, returnType)) {
            return ran;
        }
        return new CaseResult(SubmissionStatus.WRONG_ANSWER, ran.stdout(), ran.diagnostic(), ran.runtimeMs(), ran.memoryKb());
    }

    /** The most useful diagnostic Judge0 returned: compiler output, else stderr, else its own message. */
    private static String diagnosticOf(Judge0SubmissionResult result) {
        if (result.compileOutput() != null && !result.compileOutput().isBlank()) {
            return result.compileOutput();
        }
        if (result.stderr() != null && !result.stderr().isBlank()) {
            return result.stderr();
        }
        return result.message();
    }

    /**
     * One input to run.
     *
     * @param program        the complete program (harness + code)
     * @param language       the language it's written in
     * @param input          the JSON argument array fed on stdin
     * @param expectedOutput the JSON-encoded expected return value, or {@code null} to only run the code
     *                       without judging its answer — a clean run is then {@code ACCEPTED}
     */
    public record CaseRequest(String program, ProgrammingLanguage language, String input, String expectedOutput) {
    }

    /**
     * One run's outcome.
     *
     * @param status       {@code ACCEPTED} when it ran cleanly (and matched, if an answer was given)
     * @param stdout       what the program printed — the encoded return value, or {@code null}
     * @param diagnostic   compiler output / stderr / judge message, or {@code null}
     * @param runtimeMs    the program's CPU time in milliseconds, as Judge0 measured it, or {@code null}
     * @param memoryKb     its peak memory in kilobytes, or {@code null}
     */
    public record CaseResult(SubmissionStatus status, String stdout, String diagnostic,
                             Integer runtimeMs, Integer memoryKb) {

        public boolean passed() {
            return status == SubmissionStatus.ACCEPTED;
        }
    }
}
