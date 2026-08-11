package com.ttg.devknowledgeplatform.social;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;

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
 * <p><b>{@code @ComponentScan(basePackages = ...)}</b> explicitly re-adds
 * {@code com.ttg.devknowledgeplatform.infra} — a sibling package, not a parent, so default
 * scanning never reached it. This module injects {@code infra.service.StorageService} (avatar/
 * attachment presigned URLs, in `mapper/FriendMapper`/`MessagingMapper`) and extends
 * {@code infra.service.seed.CsvSeeder}/uses {@code infra.event.AsyncEventHandler} for its own
 * `FriendRequestSentEventListener`/`FriendRequestAcceptedEventListener` — the latter two need
 * {@code infra}'s own {@code AsyncEventThreadPoolConfig} bean (the `asyncEventExecutor` their
 * `@EventHandler` dispatch runs on) to exist in this context too. None of this had a bean
 * definition available before this annotation — a real, previously-undetected gap across every
 * standalone service in this reactor that uses an {@code infra} bean, caught and fixed
 * reactor-wide in the same pass as this comment (see {@code docs/CHANGELOG.md}). This module's own
 * package must be listed explicitly too — an explicit {@code @ComponentScan} replaces the implicit
 * single-package default {@code @SpringBootApplication} provides, rather than adding to it.
 *
 * <p>No {@code @EntityScan}/{@code @EnableJpaRepositories} here — this module doesn't touch
 * {@code common.entity.User}/{@code common.repository.UserRepository} at all. Every relationship
 * in this module's own entity graph points at {@code entity.SocialProfile}, this module's own lean
 * entity (see its Javadoc for why it isn't a copy of {@code common.entity.User}).
 *
 * <p><b>{@code @EnableAsync}</b> was also missing entirely until this same pass —
 * {@code event/FriendRequestSentEventListener}/{@code FriendRequestAcceptedEventListener} both
 * extend {@code infra}'s {@code AsyncEventHandler}, which dispatches via a plain {@code @Async}
 * method. Without {@code @EnableAsync} declared somewhere in the context, Spring silently ignores
 * {@code @Async} rather than erroring — no crash, just both listeners quietly running
 * synchronously on the calling thread instead of the dedicated {@code asyncEventExecutor} pool the
 * whole point of extending {@code AsyncEventHandler} was to use. Mirrors {@code ai-service}'s own
 * {@code AiServiceApplication}, which already declares this for the identical reason.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.ttg.devknowledgeplatform.social", "com.ttg.devknowledgeplatform.infra"})
@EnableAsync
public class SocialServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SocialServiceApplication.class, args);
    }
}
