package com.ttg.devknowledgeplatform.content.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for the shared-secret API key that gates every {@code /internal/**} endpoint.
 *
 * <p>Bound from the {@code app.internal-api} prefix. Both this app and {@code ai-service} (once it
 * calls over HTTP instead of in-process — see root {@code CLAUDE.md}'s content-service extraction
 * plan) must be configured with the same value via {@code INTERNAL_API_KEY}.
 */
@ConfigurationProperties(prefix = "app.internal-api")
@Validated
@Getter
@Setter
public class InternalApiProperties {

    /** Shared secret every caller of {@code /internal/**} must send as {@code X-Internal-Api-Key}. */
    @NotBlank
    private String key;
}
