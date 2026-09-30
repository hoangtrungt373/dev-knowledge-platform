package com.ttg.devknowledgeplatform.devpractice.config;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for {@code judge.impl.Judge0Client}'s calls to a Judge0 CE-compatible API.
 *
 * <p>Bound from the {@code app.judge0} prefix. Defaults to Judge0 CE's hosted RapidAPI instance —
 * chosen specifically to avoid Judge0's {@code isolate} sandbox's known
 * Docker-Desktop-on-Windows/WSL2 cgroup friction while the judging pipeline itself is still being
 * verified end-to-end (see this module's {@code CLAUDE.md}'s "Phase 2" section for the full
 * reasoning). No self-hosted Judge0 stack is scaffolded in this repo right now — it was added to
 * {@code docker-compose.infra.yml} at one point and then removed once RapidAPI became the only
 * backend in use — but this class still supports one unmodified: leave {@code rapidApiKey} blank
 * and repoint {@code baseUrl} at a self-hosted instance's own URL to stop sending the RapidAPI
 * headers entirely ({@code Judge0Client} only adds them when a key is actually configured), so
 * reintroducing self-hosting later is a compose/config addition, not a code change.
 * {@code poll-interval-ms}/{@code max-poll-attempts} bound how long {@code Judge0Client#run} blocks
 * waiting for a result before giving up (default: 500ms × 40 attempts = 20s ceiling per test case).
 */
@ConfigurationProperties(prefix = "app.judge0")
@Validated
@Getter
@Setter
public class JudgeClientProperties {

    /**
     * Base URL of the Judge0 CE-compatible API — RapidAPI's hosted instance by default
     * ({@code https://judge0-ce.p.rapidapi.com}), or a self-hosted instance's own URL (e.g.
     * {@code http://localhost:2358}) if {@code rapidApiKey} is left blank.
     */
    @NotBlank
    private String baseUrl;

    /**
     * RapidAPI subscription key, sent as the {@code X-RapidAPI-Key} header. Required when
     * {@code baseUrl} points at RapidAPI; leave blank for a self-hosted instance, which needs no
     * auth header at all.
     */
    private String rapidApiKey;

    /** Sent as the {@code X-RapidAPI-Host} header, only when {@code rapidApiKey} is set. */
    private String rapidApiHost = "judge0-ce.p.rapidapi.com";

    /** Milliseconds to wait between {@code GET /submissions/{token}} polls. */
    @Min(50)
    private long pollIntervalMs = 500;

    /** Maximum number of polls before giving up on a submission as timed out. */
    @Min(1)
    private int maxPollAttempts = 40;

    /** Judge0's own {@code cpu_time_limit}, in seconds, applied per test-case run. */
    @Min(1)
    private int cpuTimeLimitSeconds = 5;

    /**
     * TCP connect timeout for every call to Judge0. Without one, an unreachable host could block
     * a judging thread far longer than the poll ceiling above ever accounts for.
     */
    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(5);

    /**
     * Socket read timeout for every call to Judge0 — every call is a quick request/response
     * ({@code wait=false} submit, status poll), never a long-held connection, so this can stay
     * short. A hung response otherwise blocks the judging thread indefinitely.
     */
    @NotNull
    private Duration readTimeout = Duration.ofSeconds(15);

    /** Retry of transient Judge0 failures (429/502/503/504, I/O errors) — see {@link Retry}. */
    @Valid
    @NotNull
    private Retry retry = new Retry();

    /**
     * Maps each {@link ProgrammingLanguage} to the Judge0 {@code language_id} to submit it as —
     * externalized here rather than hardcoded (e.g. on {@code ProgrammingLanguage} itself) for two
     * reasons: it's Judge0-specific detail that would otherwise leak into a domain enum used well
     * beyond the judge subsystem (see {@code ProgrammingLanguage}'s own Javadoc), and Judge0
     * language ids are per-deployment and known to be unverified against this reactor's actual
     * instance (see this class's own header Javadoc) — a wrong id is fixable with one config/env
     * var change (e.g. {@code app.judge0.language-ids.java} / {@code JUDGE0_LANGUAGE_ID_JAVA}), not
     * a code change and redeploy. {@code judge.impl.Judge0Client}'s constructor fails fast at
     * startup if any {@link ProgrammingLanguage} constant has no entry here.
     */
    private Map<ProgrammingLanguage, Integer> languageIds = new EnumMap<>(ProgrammingLanguage.class);

    /**
     * Exponential-backoff retry applied to each individual Judge0 HTTP call (the submit, and each
     * status poll), only for failures that are plausibly transient — RapidAPI's rate limit (429),
     * gateway/unavailable errors (502/503/504), and I/O errors including timeouts. A permanent
     * failure (400, 401 bad key, 403, 404) is never retried. With the defaults, one call waits at
     * most 1s + 2s + 4s before giving up.
     */
    @Getter
    @Setter
    public static class Retry {

        /** Total attempts per call, including the first — {@code 1} disables retrying. */
        @Min(1)
        private int maxAttempts = 4;

        /** Delay before the first retry. */
        @NotNull
        private Duration initialInterval = Duration.ofSeconds(1);

        /** Factor each successive delay is multiplied by. */
        @DecimalMin("1.0")
        private double multiplier = 2.0;

        /** Upper bound on any single delay — equal to {@code initialInterval} means a fixed delay. */
        @NotNull
        private Duration maxInterval = Duration.ofSeconds(10);

        /** Rejects an inverted range at startup with a clear message, rather than deep inside Resilience4j's IntervalFunction. */
        @AssertTrue(message = "app.judge0.retry.max-interval must be >= initial-interval")
        public boolean isIntervalRangeValid() {
            return initialInterval == null || maxInterval == null || maxInterval.compareTo(initialInterval) >= 0;
        }
    }
}
