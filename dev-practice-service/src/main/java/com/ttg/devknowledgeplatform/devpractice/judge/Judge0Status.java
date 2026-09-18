package com.ttg.devknowledgeplatform.devpractice.judge;

/**
 * Judge0 CE's own submission status vocabulary, narrowed to the buckets this module actually acts
 * on. Judge0 has six distinct runtime-error status ids (7–12, one per signal — SIGSEGV, SIGFPE,
 * etc.); all fold into {@link #RUNTIME_ERROR} here since this module never needs to distinguish
 * *why* the user's code crashed, only that it did.
 *
 * <p><b>{@link #ACCEPTED} does not mean "matched the expected output"</b> — this module never
 * supplies Judge0's own {@code expected_output} field (see {@code Judge0Client}'s Javadoc for why:
 * this module does its own structural JSON comparison instead, which a raw-string Judge0 compare
 * can't do correctly for e.g. {@code [0,1]} vs {@code [0, 1]}). Judge0's {@code ACCEPTED} here only
 * means "ran to completion with exit code 0"; {@code WRONG_ANSWER} is consequently never produced
 * by Judge0 itself in this module's flow — that verdict is this module's own, applied after
 * comparing {@code stdout} to a {@link com.ttg.devknowledgeplatform.devpractice.entity.TestCase}'s
 * {@code expectedOutput}.
 */
public enum Judge0Status {
    IN_QUEUE(1),
    PROCESSING(2),
    ACCEPTED(3),
    WRONG_ANSWER(4),
    TIME_LIMIT_EXCEEDED(5),
    COMPILATION_ERROR(6),
    RUNTIME_ERROR(7),
    INTERNAL_ERROR(13),
    EXEC_FORMAT_ERROR(14);

    private final int id;

    Judge0Status(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    /** {@code true} once Judge0 has stopped processing this submission (not still queued/running). */
    public boolean isFinal() {
        return this != IN_QUEUE && this != PROCESSING;
    }

    /**
     * Maps a raw Judge0 {@code status.id} to this narrowed enum, folding every runtime-error
     * subtype (7–12) into {@link #RUNTIME_ERROR} and any unrecognized id into
     * {@link #INTERNAL_ERROR}.
     */
    public static Judge0Status fromId(int rawId) {
        if (rawId >= 7 && rawId <= 12) {
            return RUNTIME_ERROR;
        }
        for (Judge0Status status : values()) {
            if (status.id == rawId) {
                return status;
            }
        }
        return INTERNAL_ERROR;
    }
}
