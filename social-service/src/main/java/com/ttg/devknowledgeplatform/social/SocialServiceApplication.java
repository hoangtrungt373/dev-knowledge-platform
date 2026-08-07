package com.ttg.devknowledgeplatform.social;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the standalone {@code social-service} application — extracted out of the
 * monolith (see the {@code project-microservices-extraction-plan} memory / root {@code CLAUDE.md}
 * for the full sequencing).
 *
 * <p>Sitting at {@code com.ttg.devknowledgeplatform.social} (not the shared
 * {@code com.ttg.devknowledgeplatform} root {@code gateway}'s main class uses) means Spring Boot's
 * default component scanning never pulls in anything from {@code content-service}/{@code ai-service}/
 * {@code identity-service}/{@code task-service}/{@code ecommerce-service} — this app has no Maven
 * dependency on any of them anyway.
 *
 * <p>No {@code @EntityScan}/{@code @EnableJpaRepositories} here — this module doesn't touch
 * {@code common.entity.User}/{@code common.repository.UserRepository} at all. Every relationship
 * in this module's own entity graph points at {@code entity.SocialProfile}, this module's own lean
 * entity (see its Javadoc for why it isn't a copy of {@code common.entity.User}), and default
 * scanning already covers this module's own {@code entity}/{@code repository} packages.
 */
@SpringBootApplication
public class SocialServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SocialServiceApplication.class, args);
    }
}
