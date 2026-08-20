package com.ttg.devknowledgeplatform.ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.ttg.devknowledgeplatform.common.exception.GlobalExceptionHandler;
import com.ttg.devknowledgeplatform.infra.config.json.JacksonConfig;
import com.ttg.devknowledgeplatform.infra.config.storage.StorageConfig;
import com.ttg.devknowledgeplatform.infra.config.storage.StorageProperties;
import com.ttg.devknowledgeplatform.infra.security.KeycloakJwtAuthenticationConverter;
import com.ttg.devknowledgeplatform.infra.security.KeycloakRealmRoleConverter;
import com.ttg.devknowledgeplatform.infra.service.impl.SlugServiceImpl;
import com.ttg.devknowledgeplatform.infra.service.impl.StorageServiceImpl;
import com.ttg.devknowledgeplatform.infra.tracing.TraceContextFilter;

/**
 * Entry point for the standalone {@code ecommerce-service} application — extracted out of the
 * monolith (see the {@code project-ecommerce-service-module} memory for the full sequencing).
 *
 * <p>Sitting at {@code com.ttg.devknowledgeplatform.ecommerce} (not the shared
 * {@code com.ttg.devknowledgeplatform} root {@code gateway}'s main class uses) means Spring Boot's
 * default component scanning never pulls in anything from {@code content-service}/
 * {@code social-service}/{@code ai-service}/{@code task-service}/{@code identity-service} — this
 * app has no Maven dependency on any of them anyway.
 *
 * <p><b>{@code @Import} names the exact {@code infra} beans this module actually uses</b>, instead
 * of widening {@code @ComponentScan}/{@code @ConfigurationPropertiesScan} to the whole sibling
 * {@code infra} package the way an earlier revision of this class did. That broad-scan approach
 * went through two rounds of real startup failures on `task-service` (a sibling in the identical
 * shape) — a bare {@code @ConfigurationPropertiesScan} never reaching {@code infra} at all, then
 * {@code infra.config.thread.AsyncEventThreadPoolConfig} getting instantiated (and failing to
 * construct) even though this module dispatches no {@code @EventHandler} — before landing on this
 * explicit-import shape; see {@code docs/CHANGELOG.md}'s `[Unreleased]` entry for the full history.
 * This module imports: {@link JacksonConfig} (shared {@code ObjectMapper} customization, needed by
 * every app in this reactor), {@link TraceContextFilter} (distributed-tracing MDC binding + access
 * logging, likewise reactor-wide), {@link SlugServiceImpl} (product/category slug generation, used
 * by {@code ProductServiceImpl}/{@code ProductCategoryServiceImpl}),
 * {@link KeycloakJwtAuthenticationConverter} plus its own collaborator
 * {@link KeycloakRealmRoleConverter} (JWT → {@code CustomOAuth2User} principal, used by this
 * module's own {@code security.SecurityConfig}), and {@link StorageProperties}/
 * {@link StorageConfig}/{@link StorageServiceImpl} (the {@code MinioClient} bean plus the
 * product-image-upload service built on it — {@code ProductServiceImpl.uploadImage}/
 * {@code ProductMapper}'s presigned-URL resolution — same trio {@code identity-service} imports
 * for its own avatar upload). It does *not* import
 * {@code infra.config.thread.AsyncEventThreadPoolConfig} — this module dispatches no
 * {@code @EventHandler}, so that bean is simply never created here.
 *
 * <p>No {@code @EntityScan}/{@code @EnableJpaRepositories} on {@code EcommerceServiceApplication},
 * and no dependency on {@code common.entity.User}/{@code common.repository.UserRepository}
 * anywhere in this module — this module doesn't persist a local {@code User} row at all (see
 * {@code KeycloakJwtAuthenticationConverter}, above, and the "Option C" decision in the
 * {@code project-microservices-extraction-plan} memory). An earlier revision of this class briefly
 * carried both annotations plus an {@code ecommerce.USER} table migration, built on the assumption
 * this module needed a real local {@code User} copy — reverted once it became clear the only real
 * need was resolving the current caller's identity, fully answerable from the JWT alone.
 *
 * <p>{@code @EnableScheduling} is declared here because {@link com.ttg.devknowledgeplatform.ecommerce.outbox.OutboxRelay}
 * needs it and, unlike when this module ran inside the monolith (where {@code ai-service}'s
 * {@code AiServiceConfig} already enabled it app-wide), this standalone app doesn't include
 * {@code ai-service} at all.
 */
@SpringBootApplication
@Import({JacksonConfig.class, TraceContextFilter.class, SlugServiceImpl.class,
        KeycloakRealmRoleConverter.class, KeycloakJwtAuthenticationConverter.class,
        StorageProperties.class, StorageConfig.class, StorageServiceImpl.class,
        GlobalExceptionHandler.class})
@EnableScheduling
public class EcommerceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcommerceServiceApplication.class, args);
    }
}
