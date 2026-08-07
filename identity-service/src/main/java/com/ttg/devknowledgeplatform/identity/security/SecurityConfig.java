package com.ttg.devknowledgeplatform.identity.security;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * This service's own security filter chain — independent of {@code gateway}'s, since once
 * extracted this app runs on its own port and must guard its own endpoints regardless of whether
 * {@code gateway} is proxying to it (mirrors {@code ecommerce-service}'s {@code SecurityConfig}).
 * Keycloak is the identity provider — this service is a pure OAuth2 resource server, verifying
 * bearer tokens against Keycloak's JWKS ({@code spring.security.oauth2.resourceserver.jwt.issuer-uri});
 * it never issues tokens or handles a login flow.
 *
 * <p>Every endpoint this module owns ({@code AuthApi.getCurrentUser}, {@code UserApi.updateProfile}/
 * {@code uploadAvatar}) requires authentication — unlike {@code content-service}/
 * {@code ecommerce-service}, this module has no public or admin-only surface, so the rule set is
 * just "everything requires a valid token" plus the actuator health-check carve-out.
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
                .requestMatchers("/actuator/**").permitAll()
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
