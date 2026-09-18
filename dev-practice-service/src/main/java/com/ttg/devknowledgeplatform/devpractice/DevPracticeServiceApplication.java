package com.ttg.devknowledgeplatform.devpractice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import com.ttg.devknowledgeplatform.common.exception.GlobalExceptionHandler;
import com.ttg.devknowledgeplatform.infra.config.json.JacksonConfig;
import com.ttg.devknowledgeplatform.infra.security.CurrentUserIdArgumentResolver;
import com.ttg.devknowledgeplatform.infra.security.KeycloakJwtAuthenticationConverter;
import com.ttg.devknowledgeplatform.infra.security.KeycloakRealmRoleConverter;
import com.ttg.devknowledgeplatform.infra.service.impl.SlugServiceImpl;
import com.ttg.devknowledgeplatform.infra.tracing.TraceContextFilter;

/**
 * Entry point for {@code dev-practice-service} — the standalone LeetCode/NeetCode-style coding
 * practice platform (problem catalog today; code submission/judging in a follow-up phase).
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
 * rule in this module's own {@code CLAUDE.md}), and {@link GlobalExceptionHandler}. Deliberately
 * does <b>not</b> import {@code infra.config.thread.AsyncEventThreadPoolConfig} — no
 * {@code @EventHandler} is dispatched in this module yet (Phase 1 has no async judging pipeline).
 */
@SpringBootApplication
@Import({JacksonConfig.class, TraceContextFilter.class, SlugServiceImpl.class,
        KeycloakRealmRoleConverter.class, KeycloakJwtAuthenticationConverter.class,
        CurrentUserIdArgumentResolver.class, GlobalExceptionHandler.class})
public class DevPracticeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevPracticeServiceApplication.class, args);
    }
}
