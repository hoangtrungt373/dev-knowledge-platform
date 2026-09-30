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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.ttg.devknowledgeplatform.devpractice.config.JudgeClientProperties;
import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;
import com.ttg.devknowledgeplatform.devpractice.judge.JudgeUnavailableException;
import com.ttg.devknowledgeplatform.devpractice.judge.Judge0Status;
import com.ttg.devknowledgeplatform.devpractice.judge.Judge0SubmissionResult;
import com.ttg.devknowledgeplatform.infra.polling.PollingTemplate;

import io.github.resilience4j.retry.RetryRegistry;

/**
 * Exercises {@link Judge0Client}'s protocol against a scripted fake Judge0
 * ({@link MockRestServiceServer}) — submit/poll, base64 decoding of Judge0's line-wrapped output,
 * retry of transient failures only, and translation of every judge-side failure into
 * {@link JudgeUnavailableException}. Retry backoff and poll interval are shrunk to 1ms.
 */
class Judge0ClientTest {

    private static final String BASE = "http://judge0.test";
    private static final String SUBMIT_URL = BASE + "/submissions?base64_encoded=true&wait=false";
    private static final String POLL_URL_PREFIX = BASE + "/submissions/tok-1";

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
                .andExpect(jsonPath("$.language_id").value(62))
                .andExpect(jsonPath("$.source_code").value(base64("class Main {}")))
                .andExpect(jsonPath("$.stdin").value(base64("[1]")))
                .andRespond(withSuccess("{\"token\":\"tok-1\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(startsWith(POLL_URL_PREFIX)))
                .andRespond(withSuccess("{\"status_id\":2}", MediaType.APPLICATION_JSON));
        // Judge0's Ruby Base64.encode64 wraps output and ends it with a newline — "[0,1]\n" here.
        server.expect(once(), requestTo(startsWith(POLL_URL_PREFIX)))
                .andRespond(withSuccess("{\"status_id\":3,\"stdout\":\"WzAs\\nMV0K\\n\"}", MediaType.APPLICATION_JSON));

        Judge0SubmissionResult result = client.run("class Main {}", ProgrammingLanguage.JAVA, "[1]");

        assertThat(result.status()).isEqualTo(Judge0Status.ACCEPTED);
        assertThat(result.stdout()).isEqualTo("[0,1]\n");
        server.verify();
    }

    @Test
    void retriesRateLimitAndTransientServerErrorsThenSucceeds() {
        server.expect(once(), requestTo(SUBMIT_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(once(), requestTo(SUBMIT_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(once(), requestTo(SUBMIT_URL))
                .andRespond(withSuccess("{\"token\":\"tok-1\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(startsWith(POLL_URL_PREFIX)))
                .andRespond(withSuccess("{\"status_id\":3,\"stdout\":\"MQ==\"}", MediaType.APPLICATION_JSON));

        assertThat(client.run("x", ProgrammingLanguage.PYTHON, "[]").stdout()).isEqualTo("1");
        server.verify();
    }

    @Test
    void retriesIoErrorsSuchAsReadTimeouts() {
        server.expect(once(), requestTo(SUBMIT_URL))
                .andRespond(withSuccess("{\"token\":\"tok-1\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(startsWith(POLL_URL_PREFIX)))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));
        server.expect(once(), requestTo(startsWith(POLL_URL_PREFIX)))
                .andRespond(withSuccess("{\"status_id\":5}", MediaType.APPLICATION_JSON));

        assertThat(client.run("x", ProgrammingLanguage.JAVA, "[]").status()).isEqualTo(Judge0Status.TIME_LIMIT_EXCEEDED);
        server.verify();
    }

    @Test
    void neverRetriesPermanentFailures() {
        // Exactly one request expected — a retry would hit the mock with an unexpected call.
        server.expect(once(), requestTo(SUBMIT_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.run("x", ProgrammingLanguage.JAVA, "[]"))
                .isInstanceOf(JudgeUnavailableException.class)
                .hasMessageContaining("submit")
                .hasMessageContaining("401");
        server.verify();
    }

    @Test
    void givesUpAfterMaxAttemptsWithJudgeUnavailable() {
        server.expect(times(3), requestTo(SUBMIT_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.run("x", ProgrammingLanguage.JAVA, "[]"))
                .isInstanceOf(JudgeUnavailableException.class)
                .hasMessageContaining("429");
        server.verify();
    }

    @Test
    void reportsPollCeilingAsInternalError() {
        server.expect(once(), requestTo(SUBMIT_URL))
                .andRespond(withSuccess("{\"token\":\"tok-1\"}", MediaType.APPLICATION_JSON));
        server.expect(times(5), requestTo(startsWith(POLL_URL_PREFIX)))
                .andRespond(withSuccess("{\"status_id\":1}", MediaType.APPLICATION_JSON));

        Judge0SubmissionResult result = client.run("x", ProgrammingLanguage.JAVA, "[]");

        assertThat(result.status()).isEqualTo(Judge0Status.INTERNAL_ERROR);
        server.verify();
    }

    @Test
    void failsFastWhenALanguageIdIsMissing() {
        properties.getLanguageIds().remove(ProgrammingLanguage.JAVASCRIPT);

        assertThatThrownBy(() -> new Judge0Client(properties, RestClient.create(), newPollingTemplate()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JAVASCRIPT");
    }

    /** Fresh registry per test — a shared one would keep the first test's poll policy for the name. */
    private static PollingTemplate newPollingTemplate() {
        return new PollingTemplate(RetryRegistry.ofDefaults());
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
