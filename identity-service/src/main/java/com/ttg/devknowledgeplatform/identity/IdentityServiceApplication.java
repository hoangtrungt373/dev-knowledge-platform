package com.ttg.devknowledgeplatform.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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
 * <p>No {@code @EntityScan}/{@code @EnableJpaRepositories} anymore — {@code User}/
 * {@code UserRepository} used to live in {@code common} (default scanning, rooted at this class's
 * own package tree, doesn't reach there), but moved into this module's own
 * {@code identity.entity}/{@code identity.repository} packages once {@code gateway} dropped its
 * own local copy and this module became the sole consumer (see {@code docs/CHANGELOG.md}). Default
 * scanning now covers them without help.
 */
@SpringBootApplication
public class IdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
