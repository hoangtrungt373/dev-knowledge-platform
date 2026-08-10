package com.ttg.devknowledgeplatform.routing;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Base URLs of the six standalone services {@link GatewayRoutesConfig} proxies external client
 * traffic to.
 *
 * <p>Bound from the {@code app.services} prefix. Every field defaults to {@code localhost:<port>}
 * for running each service directly on the host machine (e.g. via {@code mvn spring-boot:run})
 * and is overridden in {@code application-docker.yml} to that service's Compose DNS name — same
 * "one value changes, the routing code doesn't" convention {@code ai-service}'s own
 * {@code ContentServiceClientProperties} already established for its one HTTP dependency.
 */
@ConfigurationProperties(prefix = "app.services")
@Validated
@Getter
@Setter
public class GatewayServicesProperties {

    /** {@code ecommerce-service} — catalog, cart/checkout, orders/inventory, payments, reviews. */
    @NotBlank
    private String ecommerceServiceBaseUrl;

    /** {@code identity-service} — Keycloak JIT-provisioning, own-profile mutation. */
    @NotBlank
    private String identityServiceBaseUrl;

    /** {@code task-service} — personal task/project management. */
    @NotBlank
    private String taskServiceBaseUrl;

    /** {@code social-service} — friend graph, groups/channels/DMs. */
    @NotBlank
    private String socialServiceBaseUrl;

    /** {@code content-service} — categories, tags, content items (Q&amp;A, articles). */
    @NotBlank
    private String contentServiceBaseUrl;

    /** {@code ai-service} — RAG chat, admin indexing/embeddings/pipeline-metrics. */
    @NotBlank
    private String aiServiceBaseUrl;
}
