package com.ttg.devknowledgeplatform.ecommerce.security;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import com.ttg.devknowledgeplatform.infra.security.KeycloakJwtAuthenticationConverter;

/**
 * This service's own security filter chain — independent of {@code gateway}'s, since once
 * extracted this app runs on its own port and must guard its own endpoints regardless of whether
 * {@code gateway} is proxying to it. Mirrors {@code gateway}'s {@code /api/v1/admin/**}/
 * {@code /api/v1/public/**} rule shape. Keycloak is the identity provider — this service is a pure
 * OAuth2 resource server, verifying bearer tokens against Keycloak's JWKS
 * ({@code spring.security.oauth2.resourceserver.jwt.issuer-uri}); it never issues tokens or
 * handles a login flow. {@link KeycloakJwtAuthenticationConverter} is shared via {@code infra} now
 * (see that class's own Javadoc), not a local copy.
 *
 * <p>{@code /webhooks/**} (Epic 4 Phase 5, US-4.5) is {@code permitAll()} for the same reason
 * {@code content-service}'s own {@code /internal/**} is: it carries no end-user JWT at all —
 * Stripe's own servers call it, authenticated via an HMAC signature over the raw request body
 * (see {@code webhook.StripeWebhookService}), which Spring Security's JWT-based filter chain plays
 * no part in verifying. Unlike {@code /internal/**}'s shared-secret header (enforceable by a
 * header-only {@code OncePerRequestFilter}), Stripe's signature needs the raw body too, so
 * verification happens inside the handler itself rather than a separate filter.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/webhooks/**").permitAll()
                .requestMatchers("/api/v1/public/**").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtAuthenticationConverter))
            )
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            );

        return http.build();
    }
}
