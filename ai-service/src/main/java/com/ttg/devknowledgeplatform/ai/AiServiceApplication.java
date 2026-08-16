package com.ttg.devknowledgeplatform.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;

import com.ttg.devknowledgeplatform.infra.config.json.JacksonConfig;
import com.ttg.devknowledgeplatform.infra.config.thread.AsyncEventThreadPoolConfig;
import com.ttg.devknowledgeplatform.infra.config.thread.AsyncEventThreadPoolProperties;
import com.ttg.devknowledgeplatform.infra.security.JsonAuthenticationEntryPoint;
import com.ttg.devknowledgeplatform.infra.security.KeycloakJwtAuthenticationConverter;
import com.ttg.devknowledgeplatform.infra.security.KeycloakRealmRoleConverter;
import com.ttg.devknowledgeplatform.infra.tracing.TraceContextFilter;

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
 * reactor-wide), {@link JsonAuthenticationEntryPoint} (JSON {@code 401} body, referenced by this
 * module's own {@code security.SecurityConfig.exceptionHandling()}),
 * {@link KeycloakJwtAuthenticationConverter} plus its own collaborator
 * {@link KeycloakRealmRoleConverter} (JWT → {@code CustomOAuth2User} principal), and
 * {@link AsyncEventThreadPoolConfig} — genuinely needed here, unlike `task-service`/
 * `ecommerce-service`/`identity-service`/`content-service` — because this module's own
 * {@code event/PipelineCompletedEventListener} extends {@code infra}'s {@code AsyncEventHandler},
 * whose {@code @Async} dispatch runs on the {@code asyncEventExecutor} bean that class provides.
 * {@code @EnableConfigurationProperties(AsyncEventThreadPoolProperties.class)} registers that
 * config's own constructor dependency — a bare {@code @ConfigurationProperties} POJO with no
 * {@code @Component} of its own, so it needs explicit registration the way a plain
 * {@code @Import} wouldn't reliably guarantee for a properties-only class.
 *
 * <p>{@code @ConfigurationPropertiesScan} (bare, no {@code basePackages}) is kept for this
 * module's own many {@code @ConfigurationProperties} classes ({@code config/*}, {@code config/chat/*}
 * — over a dozen of them), all in subpackages of this class's own, so the default
 * declaring-class-rooted scan already covers every one of them without needing to reach
 * {@code infra} at all (that's the one thing the broad-scan approach above got right for this
 * module; the bug was only ever about the sibling {@code infra} package, handled explicitly above
 * instead now).
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
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableConfigurationProperties(AsyncEventThreadPoolProperties.class)
@Import({JacksonConfig.class, TraceContextFilter.class, JsonAuthenticationEntryPoint.class,
        KeycloakRealmRoleConverter.class, KeycloakJwtAuthenticationConverter.class,
        AsyncEventThreadPoolConfig.class})
@EnableAsync
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
