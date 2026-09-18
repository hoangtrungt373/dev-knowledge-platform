package com.ttg.devknowledgeplatform.devpractice.judge;

/**
 * The outcome of running one program against one stdin through Judge0, narrowed to what
 * {@code event.SubmissionJudgeEventListener} needs — see {@link JudgeClient}.
 *
 * @param status        the final Judge0 status, already folded to this module's narrower vocabulary
 * @param stdout        captured standard output, or {@code null} if the run never produced any
 * @param stderr        captured standard error, or {@code null}
 * @param compileOutput compiler output, or {@code null} for a language with no separate compile step
 * @param message       Judge0's own diagnostic message (e.g. for an internal error), or {@code null}
 */
public record Judge0SubmissionResult(
        Judge0Status status, String stdout, String stderr, String compileOutput, String message) {
}
