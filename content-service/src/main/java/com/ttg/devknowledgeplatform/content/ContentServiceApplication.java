package com.ttg.devknowledgeplatform.content;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the standalone {@code content-service} application — extracted out of the
 * monolith (see the {@code project-microservices-extraction-plan} memory / root {@code CLAUDE.md}'s
 * Long-term direction section for the full sequencing).
 *
 * <p>Sitting at {@code com.ttg.devknowledgeplatform.content} (not the shared
 * {@code com.ttg.devknowledgeplatform} root {@code gateway}'s main class uses) means Spring Boot's
 * default component scanning never pulls in anything from {@code ai-service}/{@code social-service}/
 * {@code task-service}/{@code identity-service}/{@code ecommerce-service} — this app has no Maven
 * dependency on any of them anyway.
 *
 * <p>No {@code @EntityScan}/{@code @EnableJpaRepositories} here — this module doesn't touch
 * {@code common.entity.User}/{@code common.repository.UserRepository} at all.
 * {@code security.KeycloakJwtAuthenticationConverter} builds its {@code CustomOAuth2User} principal
 * directly from the verified JWT's claims (no local {@code User} row persisted or read, mirroring
 * {@code task-service}'s/{@code ecommerce-service}'s converter) — {@code ContentItem.authorUuid} is
 * a plain column, never a foreign key onto a user. Default scanning already covers this module's
 * own {@code entity}/{@code repository} packages, so nothing needs widening.
 *
 * <p>{@code @ConfigurationPropertiesScan} is required here (unlike when this module ran embedded in
 * {@code gateway}, which already had one covering this package too) so {@code InternalApiProperties}
 * in this module's own {@code config} package still gets bound.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ContentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContentServiceApplication.class, args);
    }
}
