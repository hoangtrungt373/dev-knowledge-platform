package com.ttg.devknowledgeplatform.infra.polling;

/**
 * Result of a {@link PollingTemplate#poll} call — either the probe reported completion, or the
 * policy's attempt budget ran out first.
 *
 * <p>Sealed so a caller's {@code switch} over it is checked for exhaustiveness by the compiler:
 * running out of attempts is an ordinary, expected outcome of polling (not an exception), and
 * making it a distinct type forces every caller to decide what "timed out" means for its own
 * domain — {@code dev-practice-service}'s {@code Judge0Client} maps it to an
 * {@code INTERNAL_ERROR} verdict, another caller might throw instead.
 *
 * @param <T> the probe's result type
 */
public sealed interface PollOutcome<T> {

    /** Number of probe calls made, including the final one. */
    int attempts();

    /**
     * The probe's last result satisfied the completion predicate.
     *
     * @param value    the completed result
     * @param attempts probe calls made
     */
    record Completed<T>(T value, int attempts) implements PollOutcome<T> {
    }

    /**
     * Every attempt returned an incomplete result.
     *
     * @param lastValue the last (still incomplete) result, useful for diagnostics
     * @param attempts  probe calls made — always equal to the policy's {@code maxAttempts}
     */
    record TimedOut<T>(T lastValue, int attempts) implements PollOutcome<T> {
    }
}
