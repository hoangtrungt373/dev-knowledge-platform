package com.ttg.devknowledgeplatform.devpractice.judge;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.devpractice.enums.ParamType;
import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;
import com.ttg.devknowledgeplatform.devpractice.enums.SubmissionStatus;

import lombok.RequiredArgsConstructor;

/**
 * Judges one input against an already-built program: runs it on the judge backend, maps the
 * backend's status to this module's {@link SubmissionStatus}, and compares the output structurally.
 * The single place that decides "what does this one run mean", shared by graded submissions
 * ({@code SubmissionJudgeEventListener}, which stops at the first failure) and unsaved Run requests
 * ({@code CodeRunService}, which reports every case) — so the two can never disagree on a verdict.
 */
@Component
@RequiredArgsConstructor
public class CaseJudge {

    private final JudgeClient judgeClient;
    private final OutputMatcher outputMatcher;

    /**
     * Runs {@code input} through {@code program}.
     *
     * @param expectedOutput the JSON-encoded expected return value, or {@code null} to only run the
     *                       code without judging its answer (a learner's custom input has no known
     *                       answer) — a clean run is then {@code ACCEPTED}
     * @return the verdict, the program's stdout and any diagnostic output
     * @throws JudgeUnavailableException if the judge backend can't be reached
     */
    public CaseResult run(String program, ProgrammingLanguage language, String input, String expectedOutput,
                          ParamType returnType) {
        Judge0SubmissionResult result = judgeClient.run(program, language, input);
        SubmissionStatus status = switch (result.status()) {
            case COMPILATION_ERROR -> SubmissionStatus.COMPILE_ERROR;
            case TIME_LIMIT_EXCEEDED -> SubmissionStatus.TIME_LIMIT_EXCEEDED;
            case RUNTIME_ERROR, INTERNAL_ERROR, EXEC_FORMAT_ERROR -> SubmissionStatus.RUNTIME_ERROR;
            case ACCEPTED -> expectedOutput == null
                    || outputMatcher.matches(result.stdout(), expectedOutput, returnType)
                    ? SubmissionStatus.ACCEPTED : SubmissionStatus.WRONG_ANSWER;
            // Judge0 only reports WRONG_ANSWER when we supply expected_output (we never do — see
            // Judge0SubmissionResult's Javadoc), and IN_QUEUE/PROCESSING are non-final statuses
            // JudgeClient#run never returns; both are defensive fallbacks only.
            case IN_QUEUE, PROCESSING, WRONG_ANSWER -> SubmissionStatus.RUNTIME_ERROR;
        };
        return new CaseResult(status, result.stdout(), diagnosticOf(result));
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
     * One run's outcome.
     *
     * @param status       {@code ACCEPTED} when it ran cleanly (and matched, if an answer was given)
     * @param stdout       what the program printed — the encoded return value, or {@code null}
     * @param diagnostic   compiler output / stderr / judge message, or {@code null}
     */
    public record CaseResult(SubmissionStatus status, String stdout, String diagnostic) {

        public boolean passed() {
            return status == SubmissionStatus.ACCEPTED;
        }
    }
}
