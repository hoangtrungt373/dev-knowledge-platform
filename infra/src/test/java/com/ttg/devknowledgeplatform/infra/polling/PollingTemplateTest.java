package com.ttg.devknowledgeplatform.infra.polling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.resilience4j.retry.RetryRegistry;

/**
 * Covers {@link PollingTemplate}'s contract on top of Resilience4j: completion, attempt-budget
 * exhaustion, unretried probe exceptions, interrupt translation, and duplicate-name protection.
 */
class PollingTemplateTest {

    private static final PollingPolicy POLICY = new PollingPolicy("test-poll", 4, Duration.ofMillis(1));

    private PollingTemplate template;

    @BeforeEach
    void setUp() {
        template = new PollingTemplate(RetryRegistry.ofDefaults());
    }

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void returnsCompletedWithAttemptCountOnceThePredicateAccepts() {
        Iterator<String> states = List.of("QUEUED", "RUNNING", "DONE").iterator();

        PollOutcome<String> outcome = template.poll(POLICY, states::next, "DONE"::equals);

        assertThat(outcome).isEqualTo(new PollOutcome.Completed<>("DONE", 3));
    }

    @Test
    void completesOnFirstAttemptWithoutWaiting() {
        PollOutcome<String> outcome = template.poll(POLICY, () -> "DONE", "DONE"::equals);

        assertThat(outcome).isEqualTo(new PollOutcome.Completed<>("DONE", 1));
    }

    @Test
    void returnsTimedOutWithLastValueAfterExactlyMaxAttempts() {
        AtomicInteger calls = new AtomicInteger();

        PollOutcome<Integer> outcome = template.poll(POLICY, calls::incrementAndGet, n -> false);

        assertThat(outcome).isEqualTo(new PollOutcome.TimedOut<>(4, 4));
        assertThat(calls).hasValue(4);
    }

    @Test
    void propagatesProbeExceptionsWithoutRetrying() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> template.poll(POLICY, () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("boom");
        }, v -> true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
        assertThat(calls).hasValue(1);
    }

    @Test
    void propagatesTheProbesOwnNullPointerExceptionUnchanged() {
        // Guards the interrupt heuristic below: an NPE from the caller's own code is a caller bug,
        // not an interruption.
        NullPointerException bug = new NullPointerException("caller bug");

        assertThatThrownBy(() -> template.poll(POLICY, () -> {
            throw bug;
        }, v -> true)).isSameAs(bug);
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    void translatesAnInterruptedWaitIntoPollingInterruptedException() {
        // With the flag already set, Resilience4j's first inter-attempt sleep throws
        // InterruptedException immediately — the path that would otherwise surface as an NPE.
        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> template.poll(POLICY, () -> "RUNNING", "DONE"::equals))
                .isInstanceOf(PollingInterruptedException.class)
                .hasMessageContaining("test-poll");
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void rejectsADifferentPolicyUnderAnAlreadyUsedName() {
        template.poll(POLICY, () -> "DONE", "DONE"::equals);
        PollingPolicy conflicting = new PollingPolicy("test-poll", 10, Duration.ofMillis(1));

        assertThatThrownBy(() -> template.poll(conflicting, () -> "DONE", "DONE"::equals))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test-poll");
    }

    @Test
    void acceptsTheSamePolicyAgain() {
        template.poll(POLICY, () -> "DONE", "DONE"::equals);

        assertThat(template.poll(POLICY, () -> "DONE", "DONE"::equals))
                .isInstanceOf(PollOutcome.Completed.class);
    }

    @Test
    void policyRejectsInvalidSettings() {
        assertThatThrownBy(() -> new PollingPolicy(" ", 1, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PollingPolicy("p", 0, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PollingPolicy("p", 1, Duration.ofMillis(-1))).isInstanceOf(IllegalArgumentException.class);
    }
}
