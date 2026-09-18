package com.ttg.devknowledgeplatform.devpractice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;

import com.ttg.devknowledgeplatform.common.exception.GlobalExceptionHandler;
import com.ttg.devknowledgeplatform.devpractice.config.JudgeClientProperties;
import com.ttg.devknowledgeplatform.infra.config.json.JacksonConfig;
import com.ttg.devknowledgeplatform.infra.config.thread.AsyncEventThreadPoolConfig;
import com.ttg.devknowledgeplatform.infra.config.thread.AsyncEventThreadPoolProperties;
import com.ttg.devknowledgeplatform.infra.security.CurrentUserIdArgumentResolver;
import com.ttg.devknowledgeplatform.infra.security.KeycloakJwtAuthenticationConverter;
import com.ttg.devknowledgeplatform.infra.security.KeycloakRealmRoleConverter;
import com.ttg.devknowledgeplatform.infra.service.impl.SlugServiceImpl;
import com.ttg.devknowledgeplatform.infra.tracing.TraceContextFilter;

/**
 * Entry point for {@code dev-practice-service} — the standalone LeetCode/NeetCode-style coding
 * practice platform (problem catalog + async Judge0-backed submission judging).
 *
 * <p>Package root {@code com.ttg.devknowledgeplatform.devpractice} means default component
 * scanning never reaches sibling feature modules' packages, including {@code infra} — per this
 * reactor's post-hardening convention (see root {@code CLAUDE.md}'s "Post-extraction hardening"
 * section and {@code infra/CLAUDE.md}), this class names the exact {@code infra} beans it needs
 * via {@code @Import} instead of a broad {@code @ComponentScan} into {@code infra}: {@link
 * JacksonConfig} + {@link TraceContextFilter} (every non-{@code gateway} app needs both), {@link
 * SlugServiceImpl} (problem slug generation, mirroring {@code content-service}'s use of it for
 * category/tag slugs), the Keycloak converter pair for {@link
 * com.ttg.devknowledgeplatform.devpractice.security.SecurityConfig}, {@link
 * CurrentUserIdArgumentResolver} (resolves {@code @CurrentUserId String} controller parameters
 * straight off the JWT's {@code sub} claim, no database lookup — see the "No local User copy"
 * rule in this module's own {@code CLAUDE.md}), {@link GlobalExceptionHandler}, and — since Phase 2
 * added {@code event.SubmissionJudgeEventListener}, this module's first real
 * {@code AsyncEventHandler} subclass — {@link AsyncEventThreadPoolConfig} (registered via
 * {@code @Import} since it is itself a {@code @Component}) plus
 * {@link AsyncEventThreadPoolProperties} (registered separately via
 * {@code @EnableConfigurationProperties}, since it is a bare {@code @ConfigurationProperties} POJO
 * with no {@code @Component} of its own). {@code @EnableAsync} is required alongside both — without
 * it, {@code @Async} is silently ignored and every event handler would run synchronously on the
 * publishing thread instead of the dedicated {@code asyncEventExecutor} pool (the exact bug
 * {@code social-service}'s own extraction caught and documents in its own {@code CLAUDE.md}).
 * {@link JudgeClientProperties} is this module's own local {@code @ConfigurationProperties} class
 * (Judge0 base URL/poll tuning), registered the same way.
 */
@SpringBootApplication
@EnableAsync
@Import({JacksonConfig.class, TraceContextFilter.class, SlugServiceImpl.class,
        KeycloakRealmRoleConverter.class, KeycloakJwtAuthenticationConverter.class,
        CurrentUserIdArgumentResolver.class, GlobalExceptionHandler.class,
        AsyncEventThreadPoolConfig.class})
@EnableConfigurationProperties({AsyncEventThreadPoolProperties.class, JudgeClientProperties.class})
public class DevPracticeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevPracticeServiceApplication.class, args);
    }
}
