package com.ttg.devknowledgeplatform.devpractice.judge.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.ttg.devknowledgeplatform.devpractice.config.JudgeClientProperties;
import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;
import com.ttg.devknowledgeplatform.devpractice.judge.JudgeClient.JudgeRequest;
import com.ttg.devknowledgeplatform.devpractice.judge.JudgeUnavailableException;
import com.ttg.devknowledgeplatform.devpractice.judge.Judge0Status;
import com.ttg.devknowledgeplatform.devpractice.judge.Judge0SubmissionResult;
import com.ttg.devknowledgeplatform.infra.polling.PollingTemplate;

import io.github.resilience4j.retry.RetryRegistry;

/**
 * Exercises {@link Judge0Client}'s batch protocol against a scripted fake Judge0
 * ({@link MockRestServiceServer}) — batch submit/poll, result order, chunking, base64 decoding of
 * Judge0's line-wrapped output, retry of transient failures only, and translation of every
 * judge-side failure into {@link JudgeUnavailableException}. Retry backoff and poll interval are
 * shrunk to 1ms.
 */
class Judge0ClientTest {

    private static final String BASE = "http://judge0.test";
    private static final String SUBMIT_URL = BASE + "/submissions/batch?base64_encoded=true";
    private static final String POLL_URL_PREFIX = BASE + "/submissions/batch?tokens=";

    private JudgeClientProperties properties;
    private MockRestServiceServer server;
    private Judge0Client client;

    @BeforeEach
    void setUp() {
        properties = new JudgeClientProperties();
        properties.setBaseUrl(BASE);
        properties.setPollIntervalMs(1);
        properties.setMaxPollAttempts(5);
        properties.getLanguageIds().put(ProgrammingLanguage.JAVA, 62);
        properties.getLanguageIds().put(ProgrammingLanguage.PYTHON, 71);
        properties.getLanguageIds().put(ProgrammingLanguage.JAVASCRIPT, 63);
        properties.getRetry().setMaxAttempts(3);
        properties.getRetry().setInitialInterval(Duration.ofMillis(1));
        properties.getRetry().setMaxInterval(Duration.ofMillis(1));

        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new Judge0Client(properties, builder.build(), newPollingTemplate());
    }

    @Test
    void submitsBase64EncodedAndPollsUntilFinal() {
        server.expect(once(), requestTo(SUBMIT_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.submissions[0].language_id").value(62))
                .andExpect(jsonPath("$.submissions[0].source_code").value(base64("class Main {}")))
                .andExpect(jsonPath("$.submissions[0].stdin").value(base64("[1]")))
                .andRespond(json("[{\"token\":\"tok-1\"}]"));
        server.expect(once(), requestTo(startsWith(POLL_URL_PREFIX + "tok-1")))
                .andRespond(json("{\"submissions\":[{\"token\":\"tok-1\",\"status_id\":2}]}"));
        // Judge0's Ruby Base64.encode64 wraps output and ends it with a newline — "[0,1]\n" here.
        server.expect(once(), requestTo(startsWith(POLL_URL_PREFIX + "tok-1")))
                .andRespond(json("{\"submissions\":[{\"token\":\"tok-1\",\"status_id\":3,\"stdout\":\"WzAs\\nMV0K\\n\","
                        + "\"time\":\"0.042\",\"memory\":9472}]}"));

        List<Judge0SubmissionResult> results = client.runAll(List.of(java("class Main {}", "[1]")));

        assertThat(results).singleElement().satisfies(r -> {
            assertThat(r.status()).isEqualTo(Judge0Status.ACCEPTED);
            assertThat(r.stdout()).isEqualTo("[0,1]\n");
            assertThat(r.runtimeMs()).as("CPU seconds → whole milliseconds, exactly").isEqualTo(42);
            assertThat(r.memoryKb()).isEqualTo(9472);
        });
        server.verify();
    }

    @Test
    void aBatchKeepsRequestOrderAndPollsUntilEveryItemIsFinal() {
        server.expect(once(), requestTo(SUBMIT_URL))
                .andExpect(jsonPath("$.submissions.length()").value(2))
                .andExpect(jsonPath("$.submissions[1].language_id").value(71))
                .andRespond(json("[{\"token\":\"tok-1\"},{\"token\":\"tok-2\"}]"));
        // First poll: only one of the two is done — keep polling.
        server.expect(once(), requestTo(startsWith(POLL_URL_PREFIX)))
                .andRespond(json("{\"submissions\":[{\"token\":\"tok-1\",\"status_id\":3,\"stdout\":\"MQ==\"},"
                        + "{\"token\":\"tok-2\",\"status_id\":2}]}"));
        // Second poll: both done — and listed in the opposite order, which must not swap the results.
        server.expect(once(), requestTo(startsWith(POLL_URL_PREFIX)))
                .andRespond(json("{\"submissions\":[{\"token\":\"tok-2\",\"status_id\":6,\"compile_output\":\"Ym9vbQ==\"},"
                        + "{\"token\":\"tok-1\",\"status_id\":3,\"stdout\":\"MQ==\"}]}"));

        List<Judge0SubmissionResult> results = client.runAll(List.of(
                java("class Main {}", "[1]"), new JudgeRequest("print(1)", ProgrammingLanguage.PYTHON, "[2]")));

        assertThat(results).extracting(Judge0SubmissionResult::status)
                .containsExactly(Judge0Status.ACCEPTED, Judge0Status.COMPILATION_ERROR);
        assertThat(results.get(1).compileOutput()).isEqualTo("boom");
        server.verify();
    }

    @Test
    void splitsRequestsIntoChunksOfTheMaxBatchSize() {
        properties.setMaxBatchSize(2);
        client = new Judge0Client(properties, rebind(), newPollingTemplate());
        server.expect(once(), requestTo(SUBMIT_URL))
                .andExpect(jsonPath("$.submissions.length()").value(2))
                .andRespond(json("[{\"token\":\"a\"},{\"token\":\"b\"}]"));
        server.expect(once(), requestTo(startsWith(POLL_URL_PREFIX + "a")))
                .andRespond(json("{\"submissions\":[{\"token\":\"a\",\"status_id\":3},{\"token\":\"b\",\"status_id\":3}]}"));
        server.expect(once(), requestTo(SUBMIT_URL))
                .andExpect(jsonPath("$.submissions.length()").value(1))
                .andRespond(json("[{\"token\":\"c\"}]"));
        server.expect(once(), requestTo(startsWith(POLL_URL_PREFIX + "c")))
                .andRespond(json("{\"submissions\":[{\"token\":\"c\",\"status_id\":5}]}"));

        List<Judge0SubmissionResult> results = client.runAll(List.of(
                java("x", "[1]"), java("x", "[2]"), java("x", "[3]")));

        assertThat(results).extracting(Judge0SubmissionResult::status).containsExactly(
                Judge0Status.ACCEPTED, Judge0Status.ACCEPTED, Judge0Status.TIME_LIMIT_EXCEEDED);
        server.verify();
    }

    @Test
    void anEmptyRequestListMakesNoCall() {
        assertThat(client.runAll(List.of())).isEmpty();
        server.verify();
    }

    @Test
    void aRejectedItemFailsTheWholeCallWithJudge0sOwnReason() {
        server.expect(once(), requestTo(SUBMIT_URL))
                .andRespond(json("[{\"token\":\"tok-1\"},{\"language_id\":[\"is not included in the list\"]}]"));

        assertThatThrownBy(() -> client.runAll(List.of(java("x", "[1]"), java("x", "[2]"))))
                .isInstanceOf(JudgeUnavailableException.class)
                .hasMessageContaining("item 1")
                .hasMessageContaining("is not included in the list");
        server.verify();
    }

    @Test
    void retriesRateLimitAndTransientServerErrorsThenSucceeds() {
        server.expect(once(), requestTo(SUBMIT_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(once(), requestTo(SUBMIT_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(once(), requestTo(SUBMIT_URL)).andRespond(json("[{\"token\":\"tok-1\"}]"));
        server.expect(once(), requestTo(startsWith(POLL_URL_PREFIX)))
                .andRespond(json("{\"submissions\":[{\"token\":\"tok-1\",\"status_id\":3,\"stdout\":\"MQ==\"}]}"));

        assertThat(client.runAll(List.of(java("x", "[]"))).get(0).stdout()).isEqualTo("1");
        server.verify();
    }

    @Test
    void retriesIoErrorsSuchAsReadTimeouts() {
        server.expect(once(), requestTo(SUBMIT_URL)).andRespond(json("[{\"token\":\"tok-1\"}]"));
        server.expect(once(), requestTo(startsWith(POLL_URL_PREFIX)))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));
        server.expect(once(), requestTo(startsWith(POLL_URL_PREFIX)))
                .andRespond(json("{\"submissions\":[{\"token\":\"tok-1\",\"status_id\":5}]}"));

        assertThat(client.runAll(List.of(java("x", "[]"))).get(0).status())
                .isEqualTo(Judge0Status.TIME_LIMIT_EXCEEDED);
        server.verify();
    }

    @Test
    void neverRetriesPermanentFailures() {
        // Exactly one request expected — a retry would hit the mock with an unexpected call.
        server.expect(once(), requestTo(SUBMIT_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.runAll(List.of(java("x", "[]"))))
                .isInstanceOf(JudgeUnavailableException.class)
                .hasMessageContaining("submit")
                .hasMessageContaining("401");
        server.verify();
    }

    @Test
    void givesUpAfterMaxAttemptsWithJudgeUnavailable() {
        server.expect(times(3), requestTo(SUBMIT_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.runAll(List.of(java("x", "[]"))))
                .isInstanceOf(JudgeUnavailableException.class)
                .hasMessageContaining("429");
        server.verify();
    }

    @Test
    void atThePollCeilingFinishedItemsKeepTheirResultAndOnlyUnfinishedOnesAreInternalErrors() {
        server.expect(once(), requestTo(SUBMIT_URL)).andRespond(json("[{\"token\":\"tok-1\"},{\"token\":\"tok-2\"}]"));
        server.expect(times(5), requestTo(startsWith(POLL_URL_PREFIX)))
                .andRespond(json("{\"submissions\":[{\"token\":\"tok-1\",\"status_id\":3,\"stdout\":\"MQ==\"},"
                        + "{\"token\":\"tok-2\",\"status_id\":1}]}"));

        List<Judge0SubmissionResult> results = client.runAll(List.of(java("x", "[1]"), java("x", "[2]")));

        assertThat(results.get(0).status()).isEqualTo(Judge0Status.ACCEPTED);
        assertThat(results.get(0).stdout()).isEqualTo("1");
        assertThat(results.get(1).status()).isEqualTo(Judge0Status.INTERNAL_ERROR);
        server.verify();
    }

    @Test
    void failsFastWhenALanguageIdIsMissing() {
        properties.getLanguageIds().remove(ProgrammingLanguage.JAVASCRIPT);

        assertThatThrownBy(() -> new Judge0Client(properties, RestClient.create(), newPollingTemplate()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JAVASCRIPT");
    }

    /** A fresh client + mock server pair, for a test that changes the properties first. */
    private RestClient rebind() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        return builder.build();
    }

    /** Fresh registry per test — a shared one would keep the first test's poll policy for the name. */
    private static PollingTemplate newPollingTemplate() {
        return new PollingTemplate(RetryRegistry.ofDefaults());
    }

    private static JudgeRequest java(String program, String stdin) {
        return new JudgeRequest(program, ProgrammingLanguage.JAVA, stdin);
    }

    private static org.springframework.test.web.client.ResponseCreator json(String body) {
        return withSuccess(body, MediaType.APPLICATION_JSON);
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
