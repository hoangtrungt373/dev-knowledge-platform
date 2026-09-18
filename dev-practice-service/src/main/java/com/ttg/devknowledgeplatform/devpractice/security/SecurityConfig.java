package com.ttg.devknowledgeplatform.devpractice.security;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import com.ttg.devknowledgeplatform.infra.security.KeycloakJwtAuthenticationConverter;

/**
 * This service's own security filter chain — independent of {@code gateway}'s, since this app
 * runs on its own port and must guard its own endpoints regardless of whether {@code gateway} is
 * proxying to it (mirrors {@code content-service}'s/{@code ecommerce-service}'s
 * {@code SecurityConfig}). Keycloak is the identity provider; this service is a pure OAuth2
 * resource server, verifying bearer tokens against Keycloak's JWKS
 * ({@code spring.security.oauth2.resourceserver.jwt.issuer-uri}).
 *
 * <p>Three endpoint classes, the same shape {@code content-service} uses for its own
 * public/admin split:
 * <ul>
 *   <li>{@code /api/v1/public/**} — read-only published-problem browsing, unauthenticated.</li>
 *   <li>{@code /api/v1/admin/**} — problem-catalog CRUD, requires {@code ROLE_ADMIN}.</li>
 *   <li>everything else (today: {@code /api/v1/submissions/**}) — requires authentication only;
 *       ownership (not role) is what scopes a caller to their own submissions, enforced in
 *       {@link com.ttg.devknowledgeplatform.devpractice.service.impl.SubmissionServiceImpl}.</li>
 * </ul>
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
                .requestMatchers("/api/v1/public/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
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
