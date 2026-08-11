package com.ttg.devknowledgeplatform.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Entry point for the standalone {@code identity-service} application — extracted out of the
 * monolith (see the {@code project-microservices-extraction-plan} memory / root {@code CLAUDE.md}
 * for the full sequencing).
 *
 * <p>Sitting at {@code com.ttg.devknowledgeplatform.identity} (not the shared
 * {@code com.ttg.devknowledgeplatform} root {@code gateway}'s main class uses) means Spring Boot's
 * default component scanning never pulls in anything from {@code content-service}/
 * {@code social-service}/{@code ai-service}/{@code task-service} — this app has no Maven dependency
 * on any of them anyway, same isolation {@code ecommerce-service} already established.
 *
 * <p><b>{@code @ComponentScan(basePackages = ...)}</b> explicitly re-adds
 * {@code com.ttg.devknowledgeplatform.infra} — a sibling package, not a parent, so default
 * scanning never reached it. This module's {@code UserController}/{@code UserMapper} inject
 * {@code infra.service.StorageService} (avatar upload) — without this, that bean would never have
 * been found and the app would have failed to start with an unsatisfied-dependency error the
 * moment it tried to construct either class. A real, previously-undetected gap across every
 * standalone service in this reactor that uses an {@code infra} bean, caught and fixed
 * reactor-wide in the same pass as this comment (see {@code docs/CHANGELOG.md}). This module's own
 * package must be listed explicitly too — an explicit {@code @ComponentScan} replaces the implicit
 * single-package default {@code @SpringBootApplication} provides, rather than adding to it.
 *
 * <p>No {@code @EntityScan}/{@code @EnableJpaRepositories} anymore — {@code User}/
 * {@code UserRepository} used to live in {@code common} (default scanning, rooted at this class's
 * own package tree, doesn't reach there), but moved into this module's own
 * {@code identity.entity}/{@code identity.repository} packages once {@code gateway} dropped its
 * own local copy and this module became the sole consumer (see {@code docs/CHANGELOG.md}). Default
 * scanning now covers them without help.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.ttg.devknowledgeplatform.identity", "com.ttg.devknowledgeplatform.infra"})
public class IdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
