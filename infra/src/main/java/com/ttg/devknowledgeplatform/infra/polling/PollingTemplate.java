package com.ttg.devknowledgeplatform.infra.polling;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

import lombok.extern.slf4j.Slf4j;

/**
 * Blocking "call, check, wait, repeat" helper for waiting on a long-running operation owned by
 * another system — e.g. {@code dev-practice-service} polling Judge0 until a submission reaches a
 * final status. Template-callback style, like Spring's own {@code JdbcTemplate}/{@code RestTemplate}:
 * this class owns the loop, the attempt budget, the wait and interrupt handling; the caller supplies
 * only the probe (how to fetch the current state) and the completion predicate (what "done" means).
 *
 * <p><b>Built on Resilience4j's {@link Retry}, using its result-based retry</b>
 * ({@code retryOnResult}) rather than a hand-written loop: "the result isn't final yet" is modelled
 * as a retryable result, with a fixed {@code waitDuration} between attempts and
 * {@code maxAttempts} as the budget. Exceptions from the probe are deliberately <em>not</em> retried
 * here ({@code retryOnException(e -> false)}) — they propagate immediately. Whether a failed probe
 * call is worth retrying (a 429 yes, a 401 no) is protocol knowledge that belongs to the caller, which
 * can wrap its own probe in a separate exception-based {@code Retry}. Keeping the two policies apart
 * stops them from silently multiplying each other's attempt counts.
 *
 * <p>Each call's completion predicate is per-invocation, but a Resilience4j {@code RetryConfig}'s
 * result predicate is fixed per {@code Retry} instance. This template bridges the two by wrapping
 * each probe result in a private {@link Probe} record that already carries its own
 * {@code complete} flag, so one config-level predicate ({@code probe -> !probe.complete()}) serves
 * every caller.
 *
 * <p><b>Interrupt handling — a real Resilience4j quirk, confirmed by {@code PollingTemplateTest}:</b>
 * when the thread is interrupted during a result-based wait, Resilience4j (2.1.0, the version the
 * Spring Cloud BOM pins) rethrows its "last exception" — which is {@code null} when every attempt so
 * far returned a result instead of throwing, so the JVM throws a {@code NullPointerException}
 * instead — and does not restore the interrupt flag that {@code Thread.sleep} cleared. The flag
 * therefore can't be used to detect the interruption. Instead this template records every failure
 * that came from the caller's own probe/predicate; a {@code NullPointerException} that did
 * <em>not</em> come from there can only have come from Resilience4j's wait. That, or a probe
 * failure while the flag is set, is reported as a {@link PollingInterruptedException} with the flag
 * restored, so callers never see the misleading NPE and never lose the interrupt.
 *
 * <p>Registered via {@code @Import(PollingTemplate.class)} by each service that needs it (this
 * reactor's explicit-import convention for {@code infra} beans — see {@code infra/CLAUDE.md}). If
 * the service already exposes a {@link RetryRegistry} bean (e.g. from
 * {@code resilience4j-spring-boot3}), polling {@code Retry}s join it and show up in its metrics;
 * otherwise this template keeps a private registry.
 */
@Component
@Slf4j
public class PollingTemplate {

    private final RetryRegistry retryRegistry;
    private final Map<String, PollingPolicy> registeredPolicies = new ConcurrentHashMap<>();

    /**
     * Spring constructor — reuses the application's own {@link RetryRegistry} bean if one exists.
     *
     * @param retryRegistry optional shared registry
     */
    @Autowired
    public PollingTemplate(ObjectProvider<RetryRegistry> retryRegistry) {
        this(retryRegistry.getIfAvailable(RetryRegistry::ofDefaults));
    }

    /**
     * Plain constructor for tests and non-Spring use.
     *
     * @param retryRegistry registry the per-policy {@link Retry} instances are created in
     */
    public PollingTemplate(RetryRegistry retryRegistry) {
        this.retryRegistry = retryRegistry;
    }

    /**
     * Calls {@code probe} until {@code isComplete} accepts its result or {@code policy}'s attempt
     * budget runs out, waiting {@code policy.interval()} between calls. Blocks the calling thread
     * for up to {@code (maxAttempts - 1) × interval} plus the probe calls' own duration.
     *
     * @param policy     attempt budget and wait interval
     * @param probe      fetches the current state; an exception it throws propagates unchanged
     * @param isComplete decides whether a probe result is final
     * @param <T>        the probe's result type
     * @return {@link PollOutcome.Completed} or {@link PollOutcome.TimedOut} — running out of attempts
     *         is a normal outcome, not an exception
     * @throws PollingInterruptedException if the thread is interrupted while polling
     * @throws IllegalStateException       if {@code policy.name()} was already registered with
     *                                     different settings
     */
    public <T> PollOutcome<T> poll(PollingPolicy policy, Supplier<T> probe, Predicate<? super T> isComplete) {
        Retry retry = retryFor(policy);
        AtomicInteger attempts = new AtomicInteger();
        AtomicReference<RuntimeException> callerFailure = new AtomicReference<>();

        Probe<T> last;
        try {
            last = retry.executeSupplier(() -> {
                attempts.incrementAndGet();
                try {
                    T value = probe.get();
                    return new Probe<>(value, isComplete.test(value));
                } catch (RuntimeException e) {
                    callerFailure.set(e);
                    throw e;
                }
            });
        } catch (RuntimeException e) {
            if (e == callerFailure.get() && !Thread.currentThread().isInterrupted()) {
                throw e;
            }
            if (e == callerFailure.get() || e instanceof NullPointerException) {
                // Either the probe itself was interrupted, or Resilience4j's own wait was (see the
                // class Javadoc) — which also swallowed the interrupt flag, so restore it.
                Thread.currentThread().interrupt();
                throw new PollingInterruptedException(policy.name(), e);
            }
            throw e;
        }

        if (last.complete()) {
            return new PollOutcome.Completed<>(last.value(), attempts.get());
        }
        log.debug("Polling '{}' timed out after {} attempts", policy.name(), attempts.get());
        return new PollOutcome.TimedOut<>(last.value(), attempts.get());
    }

    private Retry retryFor(PollingPolicy policy) {
        PollingPolicy existing = registeredPolicies.putIfAbsent(policy.name(), policy);
        if (existing != null && !existing.equals(policy)) {
            // RetryRegistry#retry(name, config) returns the already-registered instance and ignores
            // the new config, so a second policy under the same name would silently poll with the
            // first one's settings — fail loudly instead.
            throw new IllegalStateException("Polling policy '" + policy.name()
                    + "' is already registered as " + existing + ", cannot re-register as " + policy);
        }
        RetryConfig config = RetryConfig.<Probe<?>>custom()
                .maxAttempts(policy.maxAttempts())
                .waitDuration(policy.interval())
                .retryOnResult(result -> !result.complete())
                .retryOnException(e -> false)
                .build();
        return retryRegistry.retry(policy.name(), config);
    }

    /** One probe result plus whether it satisfied the caller's completion predicate. */
    private record Probe<T>(T value, boolean complete) {
    }
}
