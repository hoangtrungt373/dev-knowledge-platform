package com.ttg.devknowledgeplatform.devutils.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * This module's own filter chain — deliberately permissive, unlike every other service's own
 * {@code SecurityConfig} in this reactor.
 *
 * <p>{@code dev-utils-service} is the one deployable with no authenticated caller at all: every
 * operation is a stateless text transform, nothing here needs protecting. Spring Security is still
 * on this module's classpath regardless — not for its own sake, but because {@code common}'s shared
 * {@code GlobalExceptionHandler} declares {@code @ExceptionHandler} methods over
 * {@link org.springframework.security.access.AccessDeniedException} and
 * {@link org.springframework.security.core.AuthenticationException}, which Spring resolves via
 * reflection when that bean is registered at context startup, regardless of whether this app ever
 * throws either. Without {@code spring-boot-starter-security} on the classpath that bean fails to
 * load with a {@code NoClassDefFoundError} before this app ever serves a request — see this
 * module's own {@code pom.xml} comment. Given the dependency is unavoidable, this class exists so
 * Spring Boot's own autoconfigured default (HTTP Basic + a generated per-boot password,
 * {@code .anyRequest().authenticated()}) never applies to a single endpoint here — replaced with an
 * explicit, visible {@code permitAll()} instead of leaning on the dependency's mere absence the way
 * every other module in this reactor leans on its presence for the opposite effect. Also gates
 * {@code gateway}'s own routing: {@code gateway}'s {@code SecurityConfig} still enforces
 * {@code .anyRequest().authenticated()} on {@code /api/v1/**} before it ever proxies anywhere, so
 * this module's routes need a matching {@code permitAll()} carve-out added there too — see root
 * {@code CLAUDE.md}'s Security section and this module's own {@code CLAUDE.md}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Permits every request unauthenticated and disables CSRF/sessions — there is no login, no
     * form, and no server-side session state for a stateless text-transform API to protect.
     *
     * @param http the security configuration builder
     * @return the filter chain applied to every request
     * @throws Exception propagated from {@link HttpSecurity#build()}
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
