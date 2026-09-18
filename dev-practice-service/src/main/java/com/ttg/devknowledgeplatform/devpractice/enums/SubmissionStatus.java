package com.ttg.devknowledgeplatform.devpractice.enums;

/**
 * A submission's judging lifecycle. Phase 1 (problem catalog + submission persistence, no
 * judging yet) only ever produces {@link #PENDING} — every other value is reserved for the
 * follow-up phase that wires an actual {@code JudgeClient} (Judge0-backed) into this pipeline,
 * defined now so the eventual judging changeset only needs to update code, not widen a
 * database CHECK constraint.
 */
public enum SubmissionStatus {
    PENDING,
    RUNNING,
    ACCEPTED,
    WRONG_ANSWER,
    COMPILE_ERROR,
    RUNTIME_ERROR,
    TIME_LIMIT_EXCEEDED
}
