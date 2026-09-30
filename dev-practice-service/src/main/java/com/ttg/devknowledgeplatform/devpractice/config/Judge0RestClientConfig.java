package com.ttg.devknowledgeplatform.devpractice.config;

import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Builds the {@link RestClient} {@code judge.impl.Judge0Client} talks to Judge0 through — base
 * URL, connect/read timeouts, and the RapidAPI auth headers, all from {@link JudgeClientProperties}.
 *
 * <p>Kept separate from {@code Judge0Client} on purpose: transport wiring (where, how long, which
 * headers) lives here; protocol behavior (submit, poll, retry, decode) lives in the client. That
 * split is also what lets {@code Judge0ClientTest} hand the client a {@code RestClient} bound to a
 * {@code MockRestServiceServer} — if the client built its own request factory, it would silently
 * replace the mock's.
 */
@Configuration
public class Judge0RestClientConfig {

    private static final String RAPIDAPI_KEY_HEADER = "X-RapidAPI-Key";
    private static final String RAPIDAPI_HOST_HEADER = "X-RapidAPI-Host";

    /**
     * @param builder    Spring Boot's auto-configured, prototype-scoped builder (already carrying
     *                   the application's Jackson-backed message converters)
     * @param properties {@code app.judge0.*}
     * @return the Judge0-specific client
     */
    @Bean
    public RestClient judge0RestClient(RestClient.Builder builder, JudgeClientProperties properties) {
        ClientHttpRequestFactorySettings timeouts = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(properties.getConnectTimeout())
                .withReadTimeout(properties.getReadTimeout());
        builder = builder
                .baseUrl(properties.getBaseUrl())
                .requestFactory(ClientHttpRequestFactories.get(timeouts));
        // Only RapidAPI needs auth headers — a self-hosted Judge0 needs none, so leaving the key
        // blank is what makes the same client work unmodified against either backend.
        if (properties.getRapidApiKey() != null && !properties.getRapidApiKey().isBlank()) {
            builder = builder
                    .defaultHeader(RAPIDAPI_KEY_HEADER, properties.getRapidApiKey())
                    .defaultHeader(RAPIDAPI_HOST_HEADER, properties.getRapidApiHost());
        }
        return builder.build();
    }
}
