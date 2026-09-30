package com.ttg.devknowledgeplatform.devpractice.judge.impl;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.ttg.devknowledgeplatform.devpractice.config.JudgeClientProperties;
import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;
import com.ttg.devknowledgeplatform.devpractice.judge.JudgeClient;
import com.ttg.devknowledgeplatform.devpractice.judge.JudgeUnavailableException;
import com.ttg.devknowledgeplatform.devpractice.judge.Judge0Status;
import com.ttg.devknowledgeplatform.devpractice.judge.Judge0SubmissionResult;
import com.ttg.devknowledgeplatform.infra.polling.PollOutcome;
import com.ttg.devknowledgeplatform.infra.polling.PollingInterruptedException;
import com.ttg.devknowledgeplatform.infra.polling.PollingPolicy;
import com.ttg.devknowledgeplatform.infra.polling.PollingTemplate;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link JudgeClient} implementation against any Judge0 CE-compatible API — Judge0 CE's hosted
 * RapidAPI instance by default, or a self-hosted instance (see {@link JudgeClientProperties}' Javadoc
 * for why RapidAPI is the default and how to switch). The {@link RestClient} itself — base URL,
 * timeouts, RapidAPI auth headers — is built by {@code config.Judge0RestClientConfig}.
 *
 * <p><b>Protocol:</b> submits with {@code base64_encoded=true} (source code and stdin can contain
 * arbitrary bytes — newlines, quotes, non-ASCII — that would otherwise need careful JSON escaping over
 * the wire) and {@code wait=false}, then polls {@code GET /submissions/{token}} until Judge0 reports a
 * final status, per {@link JudgeClientProperties}' poll interval/attempt-count bounds — the loop
 * itself is {@code infra}'s shared {@link PollingTemplate}; this class only supplies the probe (one
 * status fetch) and the completion test ({@link Judge0Status#isFinal()}). Responses are
 * decoded with the MIME base64 decoder, which tolerates line breaks: Judge0 is a Ruby app whose
 * {@code Base64.encode64} wraps output every 60 characters, which the strict basic decoder rejects.
 *
 * <p><b>Failure handling:</b> each individual HTTP call (the submit, and each poll) is retried with
 * exponential backoff ({@link JudgeClientProperties.Retry}), via a Resilience4j {@link Retry}, on
 * failures that are plausibly transient —
 * 429, 502, 503, 504, and I/O errors including connect/read timeouts. Anything else (400, 401 bad key,
 * 403, ...) fails immediately. Either way, a judge-side failure surfaces as a
 * {@link JudgeUnavailableException} — never a Spring {@code RestClientException} — so callers depend
 * on this module's own contract, not on the transport. A retried submit is not strictly idempotent (a
 * POST that timed out may still have been accepted by Judge0), but the only cost is a duplicate,
 * unused submission on Judge0's side: the client only ever polls the token it finally received.
 *
 * <p><b>Two separate Resilience4j policies, deliberately nested rather than merged:</b> the outer
 * one ({@link PollingTemplate}, result-based) answers "is the submission finished yet?"; the inner
 * one ({@code callRetry}, exception-based) answers "did this one HTTP call fail transiently?". A
 * poll that hits a read timeout is retried by the inner policy and does not use up a poll attempt,
 * so the worst case per test case is roughly {@code maxPollAttempts} times the inner policy's full
 * backoff — bounded, but larger than either setting alone suggests.
 *
 * <p>Translates {@link ProgrammingLanguage} to Judge0's own {@code language_id} via
 * {@link JudgeClientProperties#getLanguageIds()} — this class, not {@code ProgrammingLanguage}
 * itself, owns that vendor-specific mapping (see that enum's own Javadoc for why); the constructor
 * fails fast if the configured map is missing an entry for any {@link ProgrammingLanguage} constant,
 * rather than letting a missing id surface later as a confusing per-submission failure.
 *
 * <p>Deliberately never sends Judge0's own {@code expected_output} field — see
 * {@link Judge0Status}'s Javadoc for why the pass/fail verdict is this module's own, computed by
 * comparing {@code stdout} against a {@code TestCase.expectedOutput} structurally (JSON-aware, not
 * a raw string compare) in {@code judge.OutputMatcher}.
 */
@Service
@Slf4j
public class Judge0Client implements JudgeClient {

    /** Failures worth retrying — rate limiting, gateway/availability errors, and I/O errors/timeouts. */
    private static final List<Class<? extends Throwable>> TRANSIENT_FAILURES = List.of(
            HttpClientErrorException.TooManyRequests.class,
            HttpServerErrorException.BadGateway.class,
            HttpServerErrorException.ServiceUnavailable.class,
            HttpServerErrorException.GatewayTimeout.class,
            ResourceAccessException.class);

    private final RestClient restClient;
    private final JudgeClientProperties properties;
    private final PollingTemplate pollingTemplate;
    private final PollingPolicy pollingPolicy;
    private final Retry callRetry;

    public Judge0Client(JudgeClientProperties properties, RestClient judge0RestClient, PollingTemplate pollingTemplate) {
        this.properties = properties;
        for (ProgrammingLanguage language : ProgrammingLanguage.values()) {
            if (!properties.getLanguageIds().containsKey(language)) {
                throw new IllegalStateException(
                        "Missing app.judge0.language-ids entry for " + language
                                + " — every ProgrammingLanguage constant needs a Judge0 language_id configured");
            }
        }
        this.restClient = judge0RestClient;
        this.pollingTemplate = pollingTemplate;
        this.pollingPolicy = new PollingPolicy("judge0-submission-status",
                properties.getMaxPollAttempts(), Duration.ofMillis(properties.getPollIntervalMs()));
        this.callRetry = buildCallRetry(properties.getRetry());
    }

    @Override
    public Judge0SubmissionResult run(String program, ProgrammingLanguage language, String stdin) {
        String token = submit(program, language, stdin);
        return poll(token);
    }

    private String submit(String program, ProgrammingLanguage language, String stdin) {
        int languageId = properties.getLanguageIds().get(language);
        CreateSubmissionRequest request = new CreateSubmissionRequest(
                encode(program), languageId, encode(stdin), properties.getCpuTimeLimitSeconds());

        CreateSubmissionResponse response = call("submit", () -> restClient.post()
                .uri("/submissions?base64_encoded=true&wait=false")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(CreateSubmissionResponse.class));

        if (response == null || response.token() == null) {
            throw new JudgeUnavailableException("Judge0 did not return a submission token");
        }
        return response.token();
    }

    private Judge0SubmissionResult poll(String token) {
        PollOutcome<GetSubmissionResponse> outcome;
        try {
            outcome = pollingTemplate.poll(pollingPolicy, () -> fetchStatus(token),
                    response -> Judge0Status.fromId(response.statusId()).isFinal());
        } catch (PollingInterruptedException e) {
            throw new JudgeUnavailableException("Interrupted while polling Judge0", e);
        }

        return switch (outcome) {
            case PollOutcome.Completed<GetSubmissionResponse>(GetSubmissionResponse response, int attempts) ->
                    new Judge0SubmissionResult(
                            Judge0Status.fromId(response.statusId()), decode(response.stdout()),
                            decode(response.stderr()), decode(response.compileOutput()), decode(response.message()));
            case PollOutcome.TimedOut<GetSubmissionResponse>(GetSubmissionResponse lastResponse, int attempts) -> {
                log.warn("Judge0 submission {} did not finish within {} polls", token, attempts);
                yield new Judge0SubmissionResult(Judge0Status.INTERNAL_ERROR, null, null, null,
                        "Timed out waiting for Judge0 after " + attempts + " polls");
            }
        };
    }

    /** One status fetch — the probe {@link PollingTemplate} repeats until the status is final. */
    private GetSubmissionResponse fetchStatus(String token) {
        GetSubmissionResponse response = call("poll " + token, () -> restClient.get()
                .uri("/submissions/{token}?base64_encoded=true&fields=stdout,stderr,status_id,compile_output,message", token)
                .retrieve()
                .body(GetSubmissionResponse.class));
        if (response == null) {
            throw new JudgeUnavailableException("Judge0 returned no body for token " + token);
        }
        return response;
    }

    /**
     * Runs one HTTP call under the retry policy, translating whatever finally fails into this
     * module's own {@link JudgeUnavailableException}.
     */
    private <T> T call(String operation, Supplier<T> request) {
        try {
            return callRetry.executeSupplier(request);
        } catch (RestClientException e) {
            throw new JudgeUnavailableException("Judge0 " + operation + " failed: " + e.getMessage(), e);
        }
    }

    private static Retry buildCallRetry(JudgeClientProperties.Retry retry) {
        Duration initial = retry.getInitialInterval();
        Duration max = retry.getMaxInterval();
        // Exponential only when there is a range to grow into; equal bounds (or multiplier 1.0)
        // mean a fixed delay (max < initial is rejected earlier, by JudgeClientProperties.Retry's
        // validation).
        IntervalFunction backoff = max.compareTo(initial) > 0 && retry.getMultiplier() > 1.0
                ? IntervalFunction.ofExponentialBackoff(initial, retry.getMultiplier(), max)
                : IntervalFunction.of(initial);
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(retry.getMaxAttempts())
                .intervalFunction(backoff)
                .retryOnException(e -> TRANSIENT_FAILURES.stream().anyMatch(type -> type.isInstance(e)))
                .build();

        Retry callRetry = Retry.of("judge0-call", config);
        callRetry.getEventPublisher().onRetry(event -> log.warn(
                "Judge0 call failed (attempt {}/{}), retrying in {}: {}",
                event.getNumberOfRetryAttempts(), retry.getMaxAttempts(), event.getWaitInterval(),
                event.getLastThrowable() == null ? "-" : event.getLastThrowable().getMessage()));
        return callRetry;
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /** MIME decoder: ignores the line breaks Judge0's Ruby {@code Base64.encode64} inserts. */
    private static String decode(String base64) {
        return base64 == null ? null : new String(Base64.getMimeDecoder().decode(base64), StandardCharsets.UTF_8);
    }

    private record CreateSubmissionRequest(
            @JsonProperty("source_code") String sourceCode,
            @JsonProperty("language_id") int languageId,
            String stdin,
            @JsonProperty("cpu_time_limit") int cpuTimeLimit) {
    }

    private record CreateSubmissionResponse(String token) {
    }

    private record GetSubmissionResponse(
            String stdout, String stderr,
            @JsonProperty("compile_output") String compileOutput,
            String message,
            @JsonProperty("status_id") int statusId) {
    }
}
