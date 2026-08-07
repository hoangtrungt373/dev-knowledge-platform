package com.ttg.devknowledgeplatform.task;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the standalone {@code task-service} application — extracted out of the monolith
 * (see the {@code project-microservices-extraction-plan} memory / root {@code CLAUDE.md} for the
 * full sequencing).
 *
 * <p>Sitting at {@code com.ttg.devknowledgeplatform.task} (not the shared
 * {@code com.ttg.devknowledgeplatform} root {@code gateway}'s main class uses) means Spring Boot's
 * default component scanning never pulls in anything from {@code content-service}/
 * {@code social-service}/{@code ai-service}/{@code identity-service} — this app has no Maven
 * dependency on any of them anyway.
 *
 * <p>No {@code @EntityScan}/{@code @EnableJpaRepositories} here — this module doesn't touch
 * {@code common.entity.User}/{@code common.repository.UserRepository} at all.
 * {@code security.KeycloakJwtAuthenticationConverter} builds its {@code CustomOAuth2User} principal
 * directly from the verified JWT's claims (no local {@code User} row persisted or read — see that
 * class's Javadoc and the {@code project-microservices-extraction-plan} memory for the "Option C"
 * reasoning this mirrors from {@code ecommerce-service}), and {@code Project}/{@code Task} reference
 * their owner via a plain {@code ownerUuid} column, never a foreign key onto a user. Default
 * scanning already covers this module's own {@code entity}/{@code repository} packages, so nothing
 * needs widening. (An earlier revision of this class briefly carried both annotations plus a
 * {@code task.USER} table migration, built on the assumption this module needed a real local
 * {@code User} copy — reverted once it became clear every ownership check here only ever compares
 * two UUIDs, with no need to persist or display another user's profile.)
 */
@SpringBootApplication
public class TaskServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskServiceApplication.class, args);
    }
}
