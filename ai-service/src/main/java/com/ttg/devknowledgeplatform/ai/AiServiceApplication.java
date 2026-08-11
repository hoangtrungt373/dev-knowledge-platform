package com.ttg.devknowledgeplatform.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Entry point for the standalone {@code ai-service} application — extracted out of the monolith
 * (see the {@code project-microservices-extraction-plan} memory / root {@code CLAUDE.md}'s
 * Long-term direction section for the full sequencing; this is the sixth and final module
 * extracted, leaving {@code gateway} with no embedded feature module of its own).
 *
 * <p>Sitting at {@code com.ttg.devknowledgeplatform.ai} (not the shared
 * {@code com.ttg.devknowledgeplatform} root {@code gateway}'s main class uses) means Spring Boot's
 * default component scanning never pulls in anything from {@code content-service}/
 * {@code social-service}/{@code task-service}/{@code identity-service}/{@code ecommerce-service} —
 * this app has no Maven dependency on any of them anyway (its former one-directional dependency on
 * {@code content-service} was removed in an earlier step of that module's own extraction — see
 * {@code content-service/CLAUDE.md}).
 *
 * <p><b>{@code @ComponentScan(basePackages = ...)}</b> explicitly re-adds
 * {@code com.ttg.devknowledgeplatform.infra} — a sibling package, not a parent, so default
 * scanning never reached it. This module's own {@code event/PipelineCompletedEventListener}
 * extends {@code infra}'s {@code AsyncEventHandler}, which needs {@code infra}'s own
 * {@code AsyncEventThreadPoolConfig} bean (the `asyncEventExecutor` its {@code @Async} dispatch
 * runs on) to exist in this context — without this annotation that bean was never found, a real,
 * previously-undetected gap across every standalone service in this reactor that uses an
 * {@code infra} bean, caught and fixed reactor-wide in the same pass as this comment (see
 * {@code docs/CHANGELOG.md}). Unlike {@code social-service}'s equivalent gap, this one never
 * silently downgraded to synchronous dispatch — {@code @EnableAsync} was already correctly
 * declared below, so the missing bean would have failed the app outright at startup instead. This
 * module's own package must be listed explicitly too — an explicit {@code @ComponentScan}
 * replaces the implicit single-package default {@code @SpringBootApplication} provides, rather
 * than adding to it.
 *
 * <p>No {@code @EntityScan}/{@code @EnableJpaRepositories} here — this module doesn't touch
 * {@code common.entity.User}/{@code common.repository.UserRepository} at all.
 * {@code security.KeycloakJwtAuthenticationConverter} builds its {@code CustomOAuth2User} principal
 * directly from the verified JWT's claims (no local {@code User} row persisted or read, mirroring
 * {@code task-service}'s/{@code content-service}'s converter) — {@code ChatSession.userUuid}/
 * {@code PipelineMetrics.userUuid} are plain columns, never a foreign key onto a user.
 *
 * <p>{@code @EnableAsync} is declared here (moved from {@code gateway}'s own {@code WebMvcConfig},
 * which is being deleted — its only two responsibilities, the {@code sseStreamExecutor} bean wiring
 * and {@code @CurrentUserId Integer} argument resolution, both belonged to this module alone) so
 * that {@code @Async}-based dispatch (every {@code @EventHandler} listener, e.g.
 * {@code PipelineCompletedEventListener}) keeps working once this module is a separate Spring
 * context. {@code @EnableScheduling} is already declared on this module's own
 * {@code AiServiceConfig} (for {@code CorpusStatisticsServiceImpl}'s centroid refresh), so it isn't
 * repeated here.
 *
 * <p>{@code @ConfigurationPropertiesScan} is required here (unlike when this module ran embedded in
 * {@code gateway}, which already had one covering this package too) so this module's many
 * {@code @ConfigurationProperties} classes (config/*, config/chat/*, config/thread/*) still get bound.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.ttg.devknowledgeplatform.ai", "com.ttg.devknowledgeplatform.infra"})
@ConfigurationPropertiesScan
@EnableAsync
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
