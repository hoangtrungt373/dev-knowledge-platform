package com.ttg.devknowledgeplatform.devpractice.judge.impl;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.ttg.devknowledgeplatform.devpractice.config.JudgeClientProperties;
import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;
import com.ttg.devknowledgeplatform.devpractice.judge.JudgeClient;
import com.ttg.devknowledgeplatform.devpractice.judge.Judge0Status;
import com.ttg.devknowledgeplatform.devpractice.judge.Judge0SubmissionResult;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link JudgeClient} implementation, backed by a Spring {@link RestClient} against any Judge0
 * CE-compatible API — Judge0 CE's hosted RapidAPI instance by default, or a self-hosted instance
 * (see {@link JudgeClientProperties}' Javadoc for why RapidAPI is the default and how to switch).
 * Submits with {@code base64_encoded=true} (source code and stdin can contain arbitrary bytes —
 * newlines, quotes, non-ASCII — that would otherwise need careful JSON escaping over the wire) and
 * {@code wait=false}, then polls {@code GET /submissions/{token}} until Judge0 reports a final
 * status, per {@link JudgeClientProperties}' poll interval/attempt-count bounds.
 *
 * <p>Adds {@code X-RapidAPI-Key}/{@code X-RapidAPI-Host} request headers only when
 * {@link JudgeClientProperties#getRapidApiKey()} is actually set — a self-hosted instance needs no
 * auth header at all in this reactor's own compose setup, so leaving the key blank is what makes
 * this same class work unmodified against either backend.
 *
 * <p>Deliberately never sends Judge0's own {@code expected_output} field — see
 * {@link Judge0Status}'s Javadoc for why the pass/fail verdict is this module's own, computed by
 * comparing {@code stdout} against a {@code TestCase.expectedOutput} structurally (JSON-aware, not
 * a raw string compare) in {@code event.SubmissionJudgeEventListener}.
 */
@Service
@Slf4j
public class Judge0Client implements JudgeClient {

    private static final String RAPIDAPI_KEY_HEADER = "X-RapidAPI-Key";
    private static final String RAPIDAPI_HOST_HEADER = "X-RapidAPI-Host";

    private final RestClient restClient;
    private final JudgeClientProperties properties;

    public Judge0Client(JudgeClientProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        builder = builder.baseUrl(properties.getBaseUrl());
        if (properties.getRapidApiKey() != null && !properties.getRapidApiKey().isBlank()) {
            builder = builder
                    .defaultHeader(RAPIDAPI_KEY_HEADER, properties.getRapidApiKey())
                    .defaultHeader(RAPIDAPI_HOST_HEADER, properties.getRapidApiHost());
        }
        this.restClient = builder.build();
    }

    @Override
    public Judge0SubmissionResult run(String program, ProgrammingLanguage language, String stdin) {
        String token = submit(program, language, stdin);
        return poll(token);
    }

    private String submit(String program, ProgrammingLanguage language, String stdin) {
        CreateSubmissionRequest request = new CreateSubmissionRequest(
                encode(program), language.getJudge0LanguageId(), encode(stdin), properties.getCpuTimeLimitSeconds());

        CreateSubmissionResponse response = restClient.post()
                .uri("/submissions?base64_encoded=true&wait=false")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(CreateSubmissionResponse.class);

        if (response == null || response.token() == null) {
            throw new IllegalStateException("Judge0 did not return a submission token");
        }
        return response.token();
    }

    private Judge0SubmissionResult poll(String token) {
        for (int attempt = 0; attempt < properties.getMaxPollAttempts(); attempt++) {
            GetSubmissionResponse response = restClient.get()
                    .uri("/submissions/{token}?base64_encoded=true&fields=stdout,stderr,status_id,compile_output,message", token)
                    .retrieve()
                    .body(GetSubmissionResponse.class);

            if (response == null) {
                throw new IllegalStateException("Judge0 returned no body for token " + token);
            }

            Judge0Status status = Judge0Status.fromId(response.statusId());
            if (status.isFinal()) {
                return new Judge0SubmissionResult(
                        status, decode(response.stdout()), decode(response.stderr()),
                        decode(response.compileOutput()), decode(response.message()));
            }

            sleep();
        }

        log.warn("Judge0 submission {} did not finish within {} polls", token, properties.getMaxPollAttempts());
        return new Judge0SubmissionResult(Judge0Status.INTERNAL_ERROR, null, null, null,
                "Timed out waiting for Judge0 after " + properties.getMaxPollAttempts() + " polls");
    }

    private void sleep() {
        try {
            Thread.sleep(properties.getPollIntervalMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while polling Judge0", e);
        }
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String base64) {
        return base64 == null ? null : new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
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
