package com.ttg.devknowledgeplatform.ai.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * This service's own security filter chain — independent of {@code gateway}'s, since once
 * extracted this app runs on its own port and must guard its own endpoints regardless of whether
 * {@code gateway} is proxying to it (mirrors {@code identity-service}'s/{@code ecommerce-service}'s/
 * {@code task-service}'s/{@code content-service}'s {@code SecurityConfig}). Keycloak is the
 * identity provider — this service is a pure OAuth2 resource server, verifying bearer tokens
 * against Keycloak's JWKS ({@code spring.security.oauth2.resourceserver.jwt.issuer-uri}); it
 * never issues tokens or handles a login flow.
 *
 * <p>{@code @EnableMethodSecurity} is required here (not just the path-based
 * {@code /api/v1/admin/**} rule below) because {@code IngestionApi} carries a class-level
 * {@code @PreAuthorize("hasRole('ADMIN')")} — {@code gateway}'s own copy of this annotation no
 * longer applies once this module is a separate Spring context.
 *
 * <p>Only two endpoint classes, unlike {@code content-service}'s three-way split — this module has
 * no public/unauthenticated surface and no server-to-server internal API of its own:
 * <ul>
 *   <li>{@code /actuator/**} — permits all.</li>
 *   <li>{@code /api/v1/admin/**} — requires {@code ROLE_ADMIN} (indexing/embedding-index/
 *       pipeline-metrics admin endpoints).</li>
 * </ul>
 * Everything else (the chat endpoints) requires authentication.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CorsConfigurationSource corsConfigurationSource;
    private final KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter;
    private final JsonAuthenticationEntryPoint authenticationEntryPoint;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtAuthenticationConverter))
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
            )
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            );

        return http.build();
    }
}
