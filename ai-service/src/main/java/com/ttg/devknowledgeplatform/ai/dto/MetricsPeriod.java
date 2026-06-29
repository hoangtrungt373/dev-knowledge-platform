package com.ttg.devknowledgeplatform.ai.dto;

import java.time.Duration;

/**
 * Named lookback windows for the pipeline metrics summary endpoint.
 *
 * <p>Each constant encapsulates a {@link Duration} that the service subtracts from
 * {@code Instant.now()} to produce the {@code since} boundary passed to the native query.
 * Keeping the time arithmetic in the service (not here) makes the enum purely descriptive
 * and easier to test without mocking clocks.
 */
public enum MetricsPeriod {

    /** Rolling window: last 24 hours. */
    LAST_24H(Duration.ofHours(24)),

    /** Rolling window: last 7 days. */
    LAST_7_DAYS(Duration.ofDays(7)),

    /** Rolling window: last 30 days. */
    LAST_30_DAYS(Duration.ofDays(30));

    private final Duration lookback;

    MetricsPeriod(Duration lookback) {
        this.lookback = lookback;
    }

    /**
     * Returns the duration subtracted from now to form the start of the reporting window.
     *
     * @return the lookback {@link Duration} for this period
     */
    public Duration getLookback() {
        return lookback;
    }
}
