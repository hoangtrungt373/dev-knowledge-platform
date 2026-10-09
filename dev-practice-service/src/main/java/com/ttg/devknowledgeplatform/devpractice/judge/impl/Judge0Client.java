package com.ttg.devknowledgeplatform.devpractice.judge.impl;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
 * <p><b>Batch protocol:</b> requests are split into chunks of {@code app.judge0.max-batch-size}
 * (Judge0's own limit, 20 by default). Each chunk is one {@code POST /submissions/batch} (returns a
 * token per program, in order) followed by {@code GET /submissions/batch?tokens=...} polls until
 * every program in the chunk has a final status — so a chunk costs two-plus-polls HTTP calls however
 * many programs it holds, where the old one-by-one protocol cost that much <i>per program</i>. Chunks
 * run one after another, not in parallel: fewer simultaneous requests is exactly what keeps the
 * hosted API's rate limit (429) at bay. Everything is sent and read {@code base64_encoded=true}, and
 * decoded with the MIME decoder, which tolerates the line breaks Judge0's Ruby {@code Base64.encode64}
 * inserts every 60 characters.
 *
 * <p><b>A rejected program fails the whole call.</b> Judge0 answers a batch with a token per item,
 * or an error object for an item it refuses (e.g. an unknown language id). That can only be a
 * configuration or harness problem on this side, never the user's code, so it surfaces as a
 * {@link JudgeUnavailableException} — a graded submission then ends as {@code JUDGE_ERROR} ("not a
 * verdict on your code") rather than a misleading runtime error.
 *
 * <p><b>Failure handling:</b> each individual HTTP call (the submit, and each poll) is retried with
 * exponential backoff ({@link JudgeClientProperties.Retry}), via a Resilience4j {@link Retry}, on
 * failures that are plausibly transient — 429, 502, 503, 504, and I/O errors including
 * connect/read timeouts. Anything else (400, 401 bad key, 403, ...) fails immediately. Either way, a
 * judge-side failure surfaces as a {@link JudgeUnavailableException} — never a Spring
 * {@code RestClientException} — so callers depend on this module's own contract, not on the
 * transport. A retried submit is not strictly idempotent (a POST that timed out may still have been
 * accepted by Judge0), but the only cost is duplicate, unused submissions on Judge0's side: the client
 * only ever polls the tokens it finally received.
 *
 * <p><b>Two separate Resilience4j policies, deliberately nested rather than merged:</b> the outer
 * one ({@link PollingTemplate}, result-based) answers "has every program in the chunk finished?"; the
 * inner one ({@code callRetry}, exception-based) answers "did this one HTTP call fail transiently?".
 * A poll that hits a read timeout is retried by the inner policy and does not use up a poll attempt.
 * When the poll ceiling is reached, programs that did finish keep their real result; only the
 * unfinished ones are reported as {@link Judge0Status#INTERNAL_ERROR}.
 *
 * <p>Translates {@link ProgrammingLanguage} to Judge0's own {@code language_id} via
 * {@link JudgeClientProperties#getLanguageIds()} — this class, not {@code ProgrammingLanguage}
 * itself, owns that vendor-specific mapping; the constructor fails fast if the configured map is
 * missing an entry for any {@link ProgrammingLanguage} constant.
 *
 * <p>Deliberately never sends Judge0's own {@code expected_output} field — see
 * {@link Judge0Status}'s Javadoc for why the pass/fail verdict is this module's own, computed by
 * comparing {@code stdout} structurally in {@code judge.OutputMatcher}.
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

    private static final String STATUS_FIELDS = "token,stdout,stderr,status_id,compile_output,message";

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
        this.pollingPolicy = new PollingPolicy("judge0-batch-status",
                properties.getMaxPollAttempts(), Duration.ofMillis(properties.getPollIntervalMs()));
        this.callRetry = buildCallRetry(properties.getRetry());
    }

    @Override
    public List<Judge0SubmissionResult> runAll(List<JudgeRequest> requests) {
        List<Judge0SubmissionResult> results = new ArrayList<>(requests.size());
        int chunkSize = properties.getMaxBatchSize();
        for (int from = 0; from < requests.size(); from += chunkSize) {
            List<JudgeRequest> chunk = requests.subList(from, Math.min(from + chunkSize, requests.size()));
            results.addAll(poll(submit(chunk)));
        }
        return results;
    }

    /** One {@code POST /submissions/batch}: returns the tokens, in request order. */
    private List<String> submit(List<JudgeRequest> chunk) {
        List<CreateSubmissionRequest> submissions = chunk.stream()
                .map(r -> new CreateSubmissionRequest(encode(r.program()), properties.getLanguageIds().get(r.language()),
                        encode(r.stdin()), properties.getCpuTimeLimitSeconds()))
                .toList();

        // Read as plain maps: an item Judge0 refuses carries field errors instead of a token, and those
        // errors are what the exception message should show.
        List<Map<String, Object>> response = call("batch submit", () -> restClient.post()
                .uri("/submissions/batch?base64_encoded=true")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateBatchRequest(submissions))
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() { }));

        if (response == null || response.size() != chunk.size()) {
            throw new JudgeUnavailableException("Judge0 returned " + (response == null ? "no" : response.size())
                    + " tokens for a batch of " + chunk.size());
        }
        List<String> tokens = new ArrayList<>(chunk.size());
        for (int i = 0; i < response.size(); i++) {
            Object token = response.get(i) == null ? null : response.get(i).get("token");
            if (!(token instanceof String)) {
                throw new JudgeUnavailableException("Judge0 rejected batch item " + i + " (language "
                        + chunk.get(i).language() + "): " + response.get(i));
            }
            tokens.add((String) token);
        }
        return tokens;
    }

    /** Polls the batch until every token is final (or the poll ceiling is hit), keeping token order. */
    private List<Judge0SubmissionResult> poll(List<String> tokens) {
        PollOutcome<Map<String, GetSubmissionResponse>> outcome;
        try {
            outcome = pollingTemplate.poll(pollingPolicy, () -> fetchStatuses(tokens),
                    statuses -> tokens.stream().allMatch(t -> isFinal(statuses.get(t))));
        } catch (PollingInterruptedException e) {
            throw new JudgeUnavailableException("Interrupted while polling Judge0", e);
        }

        Map<String, GetSubmissionResponse> statuses = switch (outcome) {
            case PollOutcome.Completed<Map<String, GetSubmissionResponse>>(var last, int attempts) -> last;
            case PollOutcome.TimedOut<Map<String, GetSubmissionResponse>>(var last, int attempts) -> {
                log.warn("Judge0 batch {} did not fully finish within {} polls", tokens, attempts);
                yield last == null ? Map.of() : last;
            }
        };
        return tokens.stream().map(token -> toResult(statuses.get(token))).toList();
    }

    /** One {@code GET /submissions/batch} — the probe {@link PollingTemplate} repeats. Keyed by token. */
    private Map<String, GetSubmissionResponse> fetchStatuses(List<String> tokens) {
        BatchStatusResponse response = call("batch poll", () -> restClient.get()
                .uri("/submissions/batch?tokens={tokens}&base64_encoded=true&fields={fields}",
                        String.join(",", tokens), STATUS_FIELDS)
                .retrieve()
                .body(BatchStatusResponse.class));
        if (response == null || response.submissions() == null) {
            throw new JudgeUnavailableException("Judge0 returned no body for batch " + tokens);
        }
        Map<String, GetSubmissionResponse> byToken = new HashMap<>();
        response.submissions().stream()
                .filter(s -> s != null && s.token() != null)
                .forEach(s -> byToken.put(s.token(), s));
        return byToken;
    }

    private static boolean isFinal(GetSubmissionResponse status) {
        return status != null && Judge0Status.fromId(status.statusId()).isFinal();
    }

    /** A finished program's result; one still unfinished when polling gave up is an INTERNAL_ERROR. */
    private Judge0SubmissionResult toResult(GetSubmissionResponse status) {
        if (!isFinal(status)) {
            return new Judge0SubmissionResult(Judge0Status.INTERNAL_ERROR, null, null, null,
                    "Timed out waiting for Judge0 after " + properties.getMaxPollAttempts() + " polls");
        }
        return new Judge0SubmissionResult(Judge0Status.fromId(status.statusId()), decode(status.stdout()),
                decode(status.stderr()), decode(status.compileOutput()), decode(status.message()));
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

    private record CreateBatchRequest(List<CreateSubmissionRequest> submissions) {
    }

    private record CreateSubmissionRequest(
            @JsonProperty("source_code") String sourceCode,
            @JsonProperty("language_id") int languageId,
            String stdin,
            @JsonProperty("cpu_time_limit") int cpuTimeLimit) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BatchStatusResponse(List<GetSubmissionResponse> submissions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GetSubmissionResponse(
            String token,
            String stdout, String stderr,
            @JsonProperty("compile_output") String compileOutput,
            String message,
            @JsonProperty("status_id") int statusId) {
    }
}
