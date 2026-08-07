package com.ttg.devknowledgeplatform.ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the standalone {@code ecommerce-service} application — extracted out of the
 * monolith (see the {@code project-ecommerce-service-module} memory for the full sequencing).
 *
 * <p>Sitting at {@code com.ttg.devknowledgeplatform.ecommerce} (not the shared
 * {@code com.ttg.devknowledgeplatform} root {@code gateway}'s main class uses) means Spring Boot's
 * default component scanning never pulls in anything from {@code content-service}/
 * {@code social-service}/{@code ai-service}/{@code task-service}/{@code identity-service} — this
 * app has no Maven dependency on any of them anyway.
 *
 * <p>No {@code @EntityScan}/{@code @EnableJpaRepositories} here — this module doesn't touch
 * {@code common.entity.User}/{@code common.repository.UserRepository} at all.
 * {@code KeycloakJwtAuthenticationConverter} builds its {@code CustomOAuth2User} principal
 * directly from the verified JWT's claims (no local {@code User} row persisted or read — see that
 * class's Javadoc and the {@code project-microservices-extraction-plan} memory for the "Option C"
 * decision this reflects), and none of this module's own entities have a foreign key onto a user.
 * Default scanning already covers this module's own {@code entity}/{@code repository} packages, so
 * nothing needs widening. (An earlier revision of this class briefly carried both annotations plus
 * an {@code ecommerce.USER} table migration, built on the assumption this module needed a real
 * local {@code User} copy — reverted once it became clear the only real need was resolving the
 * current caller's identity, fully answerable from the JWT alone.)
 *
 * <p>{@code @EnableScheduling} is declared here because {@link com.ttg.devknowledgeplatform.ecommerce.outbox.OutboxRelay}
 * needs it and, unlike when this module ran inside the monolith (where {@code ai-service}'s
 * {@code AiServiceConfig} already enabled it app-wide), this standalone app doesn't include
 * {@code ai-service} at all.
 */
@SpringBootApplication
@EnableScheduling
public class EcommerceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcommerceServiceApplication.class, args);
    }
}
