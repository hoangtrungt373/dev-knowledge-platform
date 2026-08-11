package com.ttg.devknowledgeplatform.content;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;

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
 * <p><b>{@code @ComponentScan(basePackages = ...)}</b> explicitly re-adds
 * {@code com.ttg.devknowledgeplatform.infra} — a sibling package, not a parent, so default
 * scanning never reached it. This module injects {@code infra.service.SlugService} (category/tag
 * slugs) and its seeders (`CategorySeeder`/`TagSeeder`) extend {@code infra.service.seed.CsvSeeder}
 * — neither had a bean definition available before this annotation, a real,
 * previously-undetected gap across every standalone service in this reactor that uses an
 * {@code infra} bean, caught and fixed reactor-wide in the same pass as this comment (see
 * {@code docs/CHANGELOG.md}). This module's own package must be listed explicitly too — an
 * explicit {@code @ComponentScan} replaces the implicit single-package default
 * {@code @SpringBootApplication} provides, rather than adding to it.
 *
 * <p>No {@code @EntityScan}/{@code @EnableJpaRepositories} here — this module doesn't touch
 * {@code common.entity.User}/{@code common.repository.UserRepository} at all.
 * {@code security.KeycloakJwtAuthenticationConverter} builds its {@code CustomOAuth2User} principal
 * directly from the verified JWT's claims (no local {@code User} row persisted or read, mirroring
 * {@code task-service}'s/{@code ecommerce-service}'s converter) — {@code ContentItem.authorUuid} is
 * a plain column, never a foreign key onto a user.
 *
 * <p>{@code @ConfigurationPropertiesScan} is required here (unlike when this module ran embedded in
 * {@code gateway}, which already had one covering this package too) so {@code InternalApiProperties}
 * in this module's own {@code config} package still gets bound.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.ttg.devknowledgeplatform.content", "com.ttg.devknowledgeplatform.infra"})
@ConfigurationPropertiesScan
public class ContentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContentServiceApplication.class, args);
    }
}
