package com.ttg.devknowledgeplatform.content;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;

import com.ttg.devknowledgeplatform.infra.config.json.JacksonConfig;
import com.ttg.devknowledgeplatform.infra.security.KeycloakJwtAuthenticationConverter;
import com.ttg.devknowledgeplatform.infra.security.KeycloakRealmRoleConverter;
import com.ttg.devknowledgeplatform.infra.service.impl.SlugServiceImpl;
import com.ttg.devknowledgeplatform.infra.tracing.TraceContextFilter;

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
 * <p><b>{@code @Import} names the exact {@code infra} beans this module actually uses</b>, instead
 * of widening {@code @ComponentScan} to the whole sibling {@code infra} package the way an earlier
 * revision of this class did. That broad-scan approach went through two rounds of real startup
 * failures on `task-service` (a sibling in the identical shape) — a bare
 * {@code @ConfigurationPropertiesScan} never reaching {@code infra} at all, then
 * {@code infra.config.thread.AsyncEventThreadPoolConfig} getting instantiated (and failing to
 * construct) even though this module dispatches no {@code @EventHandler} — before landing on this
 * explicit-import shape; see {@code docs/CHANGELOG.md}'s `[Unreleased]` entry for the full history.
 * This module imports: {@link JacksonConfig} (shared {@code ObjectMapper} customization, needed by
 * every app in this reactor), {@link TraceContextFilter} (distributed-tracing MDC binding + access
 * logging, likewise reactor-wide), {@link SlugServiceImpl} (category/tag slugs, used by
 * {@code CategorySeeder}/{@code TagSeeder} and the four service impls), and
 * {@link KeycloakJwtAuthenticationConverter} plus its own collaborator
 * {@link KeycloakRealmRoleConverter} (JWT → {@code CustomOAuth2User} principal, used by this
 * module's own {@code security.SecurityConfig}). It does *not* import
 * {@code infra.config.thread.AsyncEventThreadPoolConfig} — this module dispatches no
 * {@code @EventHandler}, so that bean is simply never created here. {@code CategorySeeder}/
 * {@code TagSeeder} extending {@code infra.service.seed.CsvSeeder} needs no import of its own —
 * that's a plain abstract superclass, not a bean, and the concrete seeder subclasses are this
 * module's own {@code @Component}s, already covered by default scanning of this module's own
 * package.
 *
 * <p>{@code @ConfigurationPropertiesScan} (bare, no {@code basePackages}) is kept for this module's
 * own {@code InternalApiProperties} in its own {@code config} package — a subpackage of this
 * class's own, so the default declaring-class-rooted scan already covers it without needing to
 * reach {@code infra} at all (that's the one thing the broad-scan approach above got right for
 * this module; the bug was only ever about the sibling {@code infra} package).
 *
 * <p>No {@code @EntityScan}/{@code @EnableJpaRepositories} here — this module doesn't touch
 * {@code common.entity.User}/{@code common.repository.UserRepository} at all.
 * {@code security.KeycloakJwtAuthenticationConverter} builds its {@code CustomOAuth2User} principal
 * directly from the verified JWT's claims (no local {@code User} row persisted or read, mirroring
 * {@code task-service}'s/{@code ecommerce-service}'s converter) — {@code ContentItem.authorUuid} is
 * a plain column, never a foreign key onto a user.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@Import({JacksonConfig.class, TraceContextFilter.class, SlugServiceImpl.class,
        KeycloakRealmRoleConverter.class, KeycloakJwtAuthenticationConverter.class})
public class ContentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContentServiceApplication.class, args);
    }
}
