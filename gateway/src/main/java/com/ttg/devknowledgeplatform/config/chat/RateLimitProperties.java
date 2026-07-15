package com.ttg.devknowledgeplatform.config.chat;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the AI chat endpoint rate limiter.
 *
 * <p>Bound from the {@code app.ai.rate-limit} prefix in {@code application.yml}.
 * Both limits are enforced simultaneously per authenticated user — whichever
 * bucket empties first will block further requests.
 *
 * <p>Override defaults via environment variables:
 * <ul>
 *   <li>{@code RATE_LIMIT_RPM} — requests allowed per minute (default: 10)</li>
 *   <li>{@code RATE_LIMIT_RPH} — requests allowed per hour (default: 100)</li>
 *   <li>{@code RATE_LIMIT_BUCKET_EXPIRATION} — Redis key TTL for inactive buckets (default: PT2H)</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "app.ai.rate-limit")
@Validated
@Getter
@Setter
public class RateLimitProperties {

    /** Maximum number of chat requests a single user may make per minute. */
    @Positive
    private int requestsPerMinute = 10;

    /** Maximum number of chat requests a single user may make per hour. */
    @Positive
    private int requestsPerHour = 100;

    /** How long a user's rate-limit bucket persists in Redis after their last request. */
    @NotNull
    private Duration bucketExpiration = Duration.ofHours(2);
}
