package com.ttg.devknowledgeplatform.ai.config;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * HTTP client configuration for the LangChain4j OpenAI integration.
 *
 * <p>Bound from the {@code app.ai.okhttp} prefix. The single {@code timeout} value is passed
 * to both the blocking and streaming LangChain4j model builders via their
 * {@code timeout(Duration)} method, governing the overall HTTP call duration (connect + read +
 * write combined).
 *
 * <p><strong>LangChain4j 0.33.0 limitation:</strong> the OpenAI builder does not expose
 * an {@code okHttpClient(OkHttpClient)} setter in this version, so the OkHttp
 * {@code Dispatcher} thread pool (max concurrent requests, idle thread timeout) remains
 * internally managed. Dispatcher configuration will become available once LangChain4j exposes
 * a {@code customizeHttpClient(...)} or {@code okHttpClient(...)} builder method.
 */
@ConfigurationProperties(prefix = "app.ai.okhttp")
@Validated
@Getter
@Setter
public class OkHttpProperties {

    /**
     * Overall HTTP timeout applied to each OpenAI API call (connect + read + write).
     * Use ISO-8601 duration format, e.g. {@code 60s}, {@code PT2M}.
     */
    @NotNull
    private Duration timeout = Duration.ofSeconds(60);
}
