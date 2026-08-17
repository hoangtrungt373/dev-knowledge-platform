package com.ttg.devknowledgeplatform.social;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;

import com.ttg.devknowledgeplatform.common.exception.GlobalExceptionHandler;
import com.ttg.devknowledgeplatform.infra.config.json.JacksonConfig;
import com.ttg.devknowledgeplatform.infra.config.storage.StorageConfig;
import com.ttg.devknowledgeplatform.infra.config.storage.StorageProperties;
import com.ttg.devknowledgeplatform.infra.config.thread.AsyncEventThreadPoolConfig;
import com.ttg.devknowledgeplatform.infra.config.thread.AsyncEventThreadPoolProperties;
import com.ttg.devknowledgeplatform.infra.security.KeycloakRealmRoleConverter;
import com.ttg.devknowledgeplatform.infra.service.impl.StorageServiceImpl;
import com.ttg.devknowledgeplatform.infra.tracing.TraceContextFilter;

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
 * <p><b>{@code @Import}/{@code @EnableConfigurationProperties} name the exact {@code infra} beans
 * this module actually uses</b>, instead of widening {@code @ComponentScan}/
 * {@code @ConfigurationPropertiesScan} to the whole sibling {@code infra} package the way an
 * earlier revision of this class did. That broad-scan approach went through two rounds of real
 * startup failures on `task-service` (a sibling in the identical shape) — a bare
 * {@code @ConfigurationPropertiesScan} never reaching {@code infra} at all, then this very module's
 * {@code infra.config.thread.AsyncEventThreadPoolConfig} usage relying on scan-order luck rather
 * than an explicit dependency — before landing on this explicit-import shape; see
 * {@code docs/CHANGELOG.md}'s `[Unreleased]` entry for the full history. This module imports:
 * {@link JacksonConfig} (shared {@code ObjectMapper} customization, needed by every app in this
 * reactor), {@link TraceContextFilter} (distributed-tracing MDC binding + access logging, likewise
 * reactor-wide), {@link StorageProperties}/{@link StorageConfig}/{@link StorageServiceImpl} (the
 * {@code MinioClient} bean plus the avatar/attachment presigned-URL service built on it, used by
 * {@code mapper/FriendMapper}/{@code MessagingMapper}), {@link KeycloakRealmRoleConverter} (role
 * mapping, used by this module's own local {@code security.KeycloakJwtAuthenticationConverter} —
 * see that class's own Javadoc for why this module keeps a local converter rather than using
 * {@code infra}'s shared one), and {@link AsyncEventThreadPoolConfig} — genuinely needed here,
 * because this module's own {@code event/FriendRequestSentEventListener}/
 * {@code FriendRequestAcceptedEventListener} both extend {@code infra}'s {@code AsyncEventHandler},
 * whose {@code @Async} dispatch runs on the {@code asyncEventExecutor} bean that class provides.
 * {@code @EnableConfigurationProperties(AsyncEventThreadPoolProperties.class)} registers that
 * config's own constructor dependency — a bare {@code @ConfigurationProperties} POJO with no
 * {@code @Component} of its own. This module has no local {@code @ConfigurationProperties} classes
 * of its own, so no {@code @ConfigurationPropertiesScan} is declared at all.
 *
 * <p>No {@code @EntityScan}/{@code @EnableJpaRepositories} here — this module doesn't touch
 * {@code common.entity.User}/{@code common.repository.UserRepository} at all. Every relationship
 * in this module's own entity graph points at {@code entity.SocialProfile}, this module's own lean
 * entity (see its Javadoc for why it isn't a copy of {@code common.entity.User}).
 *
 * <p><b>{@code @EnableAsync}</b> is declared because {@code event/FriendRequestSentEventListener}/
 * {@code FriendRequestAcceptedEventListener} both extend {@code infra}'s {@code AsyncEventHandler},
 * which dispatches via a plain {@code @Async} method. Without {@code @EnableAsync} declared
 * somewhere in the context, Spring silently ignores {@code @Async} rather than erroring — no crash,
 * just both listeners quietly running synchronously on the calling thread instead of the dedicated
 * {@code asyncEventExecutor} pool the whole point of extending {@code AsyncEventHandler} was to
 * use — a real bug this module hit once before this was caught (see {@code docs/CHANGELOG.md}).
 * Mirrors {@code ai-service}'s own {@code AiServiceApplication}, which declares this for the
 * identical reason.
 */
@SpringBootApplication
@EnableConfigurationProperties(AsyncEventThreadPoolProperties.class)
@Import({JacksonConfig.class, TraceContextFilter.class, StorageProperties.class,
        StorageConfig.class, StorageServiceImpl.class, KeycloakRealmRoleConverter.class,
        AsyncEventThreadPoolConfig.class, GlobalExceptionHandler.class})
@EnableAsync
public class SocialServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SocialServiceApplication.class, args);
    }
}
