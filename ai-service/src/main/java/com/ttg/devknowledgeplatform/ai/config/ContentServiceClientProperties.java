package com.ttg.devknowledgeplatform.ai.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for {@code ContentServiceClient}'s HTTP calls to {@code content-service}'s
 * internal indexing API.
 *
 * <p>Bound from the {@code app.content-service} prefix. {@code base-url} points at the same
 * process today ({@code content-service}'s {@code /internal/**} controllers still run embedded in
 * {@code gateway}'s Spring context) and will change to that module's own host:port once it's
 * extracted into a standalone service — only this one value needs to change, the client code
 * doesn't. {@code internal-api-key} must match {@code content-service}'s own
 * {@code app.internal-api.key} (both read from the same {@code INTERNAL_API_KEY} env var today).
 */
@ConfigurationProperties(prefix = "app.content-service")
@Validated
@Getter
@Setter
public class ContentServiceClientProperties {

    /** Base URL of content-service's internal API — e.g. {@code http://localhost:8080}. */
    @NotBlank
    private String baseUrl;

    /** Shared secret sent as {@code X-Internal-Api-Key} on every request. */
    @NotBlank
    private String internalApiKey;
}
