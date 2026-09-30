package com.ttg.devknowledgeplatform.devpractice.enums;

/**
 * A submission's judging lifecycle: {@link #PENDING} on creation, {@link #RUNNING} once
 * {@code event.SubmissionJudgeEventListener} picks it up, then exactly one final value.
 *
 * <p>{@link #JUDGE_ERROR} is the one final value that says nothing about the user's code: the
 * judge backend itself failed (unreachable, rate-limited past every retry, rejected our API key,
 * ...), so the submission couldn't be judged at all and should simply be resubmitted. Every other
 * final value is a verdict on the code. It exists so that a judge-side failure never leaves a
 * submission stuck in {@code RUNNING} forever, and is never reported as the user's own fault.
 */
public enum SubmissionStatus {
    PENDING,
    RUNNING,
    ACCEPTED,
    WRONG_ANSWER,
    COMPILE_ERROR,
    RUNTIME_ERROR,
    TIME_LIMIT_EXCEEDED,
    JUDGE_ERROR
}
