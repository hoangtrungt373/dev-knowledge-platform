package com.ttg.devknowledgeplatform.infra.polling;

import java.time.Duration;
import java.util.Objects;

/**
 * How long and how often a {@link PollingTemplate#poll} call re-checks for completion.
 *
 * <p>{@code name} identifies the underlying Resilience4j {@code Retry} instance in the template's
 * {@code RetryRegistry} (and so in its metrics/events), which means one name must always describe
 * the same policy — {@link PollingTemplate} rejects a second, different policy registered under an
 * already-used name rather than silently reusing the first one's settings.
 *
 * @param name         stable identifier, e.g. {@code "judge0-submission-status"}
 * @param maxAttempts  total probe calls including the first; {@code 1} means "check once, never wait"
 * @param interval     fixed wait between two probe calls
 */
public record PollingPolicy(String name, int maxAttempts, Duration interval) {

    public PollingPolicy {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("PollingPolicy name must not be blank");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("PollingPolicy maxAttempts must be >= 1, was " + maxAttempts);
        }
        Objects.requireNonNull(interval, "PollingPolicy interval must not be null");
        if (interval.isNegative()) {
            throw new IllegalArgumentException("PollingPolicy interval must not be negative, was " + interval);
        }
    }
}
