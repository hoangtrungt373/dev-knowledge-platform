package com.ttg.devknowledgeplatform.task;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

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
 * <p><b>{@code @ComponentScan(basePackages = ...)}</b> explicitly re-adds
 * {@code com.ttg.devknowledgeplatform.infra} — a sibling package, not a parent, so default
 * scanning never reached it. This module doesn't actually inject any {@code infra} bean today
 * (confirmed via a reactor-wide grep — see {@code docs/CHANGELOG.md}), so this widening is
 * currently a no-op here, added purely for consistency with the other five standalone services
 * (all of which genuinely needed it) and to prevent this exact gap from resurfacing silently the
 * moment this module ever does add an {@code infra} dependency. This module's own package must be
 * listed explicitly too — an explicit {@code @ComponentScan} replaces the implicit single-package
 * default {@code @SpringBootApplication} provides, rather than adding to it.
 *
 * <p>No {@code @EntityScan}/{@code @EnableJpaRepositories} here — this module doesn't touch
 * {@code common.entity.User}/{@code common.repository.UserRepository} at all.
 * {@code security.KeycloakJwtAuthenticationConverter} builds its {@code CustomOAuth2User} principal
 * directly from the verified JWT's claims (no local {@code User} row persisted or read — see that
 * class's Javadoc and the {@code project-microservices-extraction-plan} memory for the "Option C"
 * reasoning this mirrors from {@code ecommerce-service}), and {@code Project}/{@code Task} reference
 * their owner via a plain {@code ownerUuid} column, never a foreign key onto a user. (An earlier
 * revision of this class briefly carried both annotations plus a
 * {@code task.USER} table migration, built on the assumption this module needed a real local
 * {@code User} copy — reverted once it became clear every ownership check here only ever compares
 * two UUIDs, with no need to persist or display another user's profile.)
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.ttg.devknowledgeplatform.task", "com.ttg.devknowledgeplatform.infra"})
public class TaskServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskServiceApplication.class, args);
    }
}
