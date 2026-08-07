package com.ttg.devknowledgeplatform.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Entry point for the standalone {@code identity-service} application — extracted out of the
 * monolith (see the {@code project-microservices-extraction-plan} memory / root {@code CLAUDE.md}
 * for the full sequencing).
 *
 * <p>Sitting at {@code com.ttg.devknowledgeplatform.identity} (not the shared
 * {@code com.ttg.devknowledgeplatform} root {@code gateway}'s main class uses) means Spring Boot's
 * default component scanning never pulls in anything from {@code content-service}/
 * {@code social-service}/{@code ai-service}/{@code task-service} — this app has no Maven dependency
 * on any of them anyway, same isolation {@code ecommerce-service} already established. That default
 * scanning is scoped to this class's own package tree only, though, so this module owns no
 * entities/repositories of its own but still needs {@code common.entity.User}/
 * {@code common.repository.UserRepository} — {@link EntityScan}/{@link EnableJpaRepositories} widen
 * the scan explicitly rather than relying on the default, which would silently miss both (a real
 * bug caught here and fixed in {@code ecommerce-service} too — see that module's
 * {@code EcommerceServiceApplication}).
 */
@SpringBootApplication
@EntityScan(basePackages = "com.ttg.devknowledgeplatform.common.entity")
@EnableJpaRepositories(basePackages = "com.ttg.devknowledgeplatform.common.repository")
public class IdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
