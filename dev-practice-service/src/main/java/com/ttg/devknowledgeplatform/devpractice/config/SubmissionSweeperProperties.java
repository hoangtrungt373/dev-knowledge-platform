package com.ttg.devknowledgeplatform.devpractice.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.submissions.sweeper.*} — when an in-progress submission counts as stuck.
 *
 * <p>{@code staleAfter} must comfortably exceed the longest a healthy judging run can take, or the
 * sweeper would fail submissions that are merely slow: per chunk of up to {@code max-batch-size}
 * test cases, {@code Judge0Client} polls for at most {@code max-poll-attempts × poll-interval-ms}
 * (20 s by default), plus HTTP retries with backoff — a few minutes in the worst case. 10 minutes
 * leaves a wide margin. The poll interval itself stays a raw {@code ${...}} placeholder on
 * {@code StaleSubmissionSweepJob}'s {@code @Scheduled}, same as {@code ecommerce-service}'s
 * {@code OrderJobProperties} explains: the scheduler resolves it from the environment, not from this
 * bound object.
 *
 * @param staleAfter how long a {@code PENDING}/{@code RUNNING} submission may go without any update
 *                   before it is marked {@code JUDGE_ERROR}
 */
@ConfigurationProperties(prefix = "app.submissions.sweeper")
public record SubmissionSweeperProperties(Duration staleAfter) {

    public SubmissionSweeperProperties {
        if (staleAfter == null) {
            staleAfter = Duration.ofMinutes(10);
        }
    }
}
