package com.ttg.devknowledgeplatform.ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the standalone {@code ecommerce-service} application — extracted out of the
 * monolith (see the {@code project-ecommerce-service-module} memory for the full sequencing).
 *
 * <p>Sitting at {@code com.ttg.devknowledgeplatform.ecommerce} (not the shared
 * {@code com.ttg.devknowledgeplatform} root {@code gateway}'s main class uses) is deliberate, not
 * just a naming choice: Spring Boot's default component/entity/repository scanning covers only
 * the main class's own package and subpackages. Sitting here means {@code common.entity.User} and
 * {@code common.repository.UserRepository} — which target a table this service's own database
 * doesn't have — are correctly never scanned or wired, with zero extra configuration. Scanning
 * {@code common.entity.AbstractEntity} needs no special handling either, despite living outside
 * this package tree: it's a {@code @MappedSuperclass}, resolved via normal Java inheritance the
 * moment Hibernate processes an {@code @Entity} subclass (e.g. {@code Product}), not something
 * that needs its own scan entry.
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
