package com.ttg.devknowledgeplatform.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import com.ttg.devknowledgeplatform.common.exception.GlobalExceptionHandler;
import com.ttg.devknowledgeplatform.identity.config.KeycloakAdminConfig;
import com.ttg.devknowledgeplatform.identity.config.KeycloakAdminProperties;
import com.ttg.devknowledgeplatform.infra.config.json.JacksonConfig;
import com.ttg.devknowledgeplatform.infra.config.storage.StorageConfig;
import com.ttg.devknowledgeplatform.infra.config.storage.StorageProperties;
import com.ttg.devknowledgeplatform.infra.security.KeycloakRealmRoleConverter;
import com.ttg.devknowledgeplatform.infra.service.impl.StorageServiceImpl;
import com.ttg.devknowledgeplatform.infra.tracing.TraceContextFilter;

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
 * logging, likewise reactor-wide), {@link StorageProperties}/{@link StorageConfig}/
 * {@link StorageServiceImpl} (the {@code MinioClient} bean plus the avatar-upload service built on
 * it, used by {@code UserController}/{@code UserMapper}), and {@link KeycloakRealmRoleConverter}
 * (role mapping, used by this module's own local {@code security.KeycloakJwtAuthenticationConverter}
 * — see that class's own Javadoc for why this module keeps a local converter rather than using
 * {@code infra}'s shared one). It does *not* import
 * {@code infra.config.thread.AsyncEventThreadPoolConfig} — this module dispatches no
 * {@code @EventHandler}, so that bean is simply never created here. {@link KeycloakAdminProperties}
 * is this module's own class (not {@code infra}'s) but still needs this explicit {@code @Import},
 * for a different reason than the {@code infra} classes above: it's a plain
 * {@code @ConfigurationProperties} class with no {@code @Component} stereotype, so default
 * component scanning (which does reach this module's own {@code identity.config} package) never
 * picks it up on its own — importing it registers the bean, then Spring Boot's auto-configured
 * {@code ConfigurationPropertiesBindingPostProcessor} binds it, same mechanism
 * {@link StorageProperties} above relies on. {@link KeycloakAdminConfig}, by contrast, needs no
 * explicit import at all — it's a real {@code @Configuration} class in this module's own package
 * tree, so default scanning already finds it.
 *
 * <p>No {@code @EntityScan}/{@code @EnableJpaRepositories} anymore — {@code User}/
 * {@code UserRepository} used to live in {@code common} (default scanning, rooted at this class's
 * own package tree, doesn't reach there), but moved into this module's own
 * {@code identity.entity}/{@code identity.repository} packages once {@code gateway} dropped its
 * own local copy and this module became the sole consumer (see {@code docs/CHANGELOG.md}). Default
 * scanning now covers them without help.
 */
@SpringBootApplication
@Import({JacksonConfig.class, TraceContextFilter.class, StorageProperties.class,
        StorageConfig.class, StorageServiceImpl.class, KeycloakRealmRoleConverter.class,
        KeycloakAdminProperties.class, GlobalExceptionHandler.class})
public class IdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
