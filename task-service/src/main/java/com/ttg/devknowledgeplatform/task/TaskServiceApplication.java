package com.ttg.devknowledgeplatform.task;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import com.ttg.devknowledgeplatform.common.exception.GlobalExceptionHandler;
import com.ttg.devknowledgeplatform.infra.config.json.JacksonConfig;
import com.ttg.devknowledgeplatform.infra.security.KeycloakJwtAuthenticationConverter;
import com.ttg.devknowledgeplatform.infra.security.KeycloakRealmRoleConverter;
import com.ttg.devknowledgeplatform.infra.tracing.TraceContextFilter;

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
 * <p><b>{@code @Import} names the exact {@code infra} beans this module actually uses</b>, instead
 * of widening {@code @ComponentScan}/{@code @ConfigurationPropertiesScan} to the whole sibling
 * {@code infra} package the way an earlier revision of this class did. That broad-scan approach
 * went through two rounds of real startup failures — a bare {@code @ConfigurationPropertiesScan}
 * never reaching {@code infra} at all, then {@code infra.config.thread.AsyncEventThreadPoolConfig}
 * getting instantiated (and failing to construct) even though this module dispatches no
 * {@code @EventHandler} — before landing on this explicit-import shape; see
 * {@code docs/CHANGELOG.md}'s `[Unreleased]` entry for the full history. This module imports:
 * {@link JacksonConfig} (shared {@code ObjectMapper} customization, needed by every app in this
 * reactor), {@link TraceContextFilter} (distributed-tracing MDC binding + access logging, likewise
 * reactor-wide), and {@link KeycloakJwtAuthenticationConverter} plus its own collaborator
 * {@link KeycloakRealmRoleConverter} (JWT → {@code CustomOAuth2User} principal, used by this
 * module's own {@code security.SecurityConfig}). It does *not* import
 * {@code infra.config.thread.AsyncEventThreadPoolConfig} — this module dispatches no
 * {@code @EventHandler}, so that bean is simply never created here, unlike the blanket package
 * scan the earlier revision used, which instantiated it (and failed) regardless of whether
 * anything actually needed it.
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
@Import({JacksonConfig.class, TraceContextFilter.class, KeycloakRealmRoleConverter.class,
        KeycloakJwtAuthenticationConverter.class, GlobalExceptionHandler.class})
public class TaskServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskServiceApplication.class, args);
    }
}
