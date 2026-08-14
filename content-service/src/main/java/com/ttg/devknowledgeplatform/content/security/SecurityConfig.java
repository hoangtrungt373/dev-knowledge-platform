package com.ttg.devknowledgeplatform.content.security;

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
 * This service's own security filter chain — independent of {@code gateway}'s, since once
 * extracted this app runs on its own port and must guard its own endpoints regardless of whether
 * {@code gateway} is proxying to it (mirrors {@code identity-service}'s/{@code ecommerce-service}'s/
 * {@code task-service}'s {@code SecurityConfig}). Keycloak is the identity provider — this service
 * is a pure OAuth2 resource server, verifying bearer tokens against Keycloak's JWKS
 * ({@code spring.security.oauth2.resourceserver.jwt.issuer-uri}); it never issues tokens or handles
 * a login flow.
 *
 * <p>Three endpoint classes, mirroring the rule set {@code gateway}'s own {@code SecurityConfig}
 * used to apply to these same paths before this module was extracted:
 * <ul>
 *   <li>{@code /api/v1/public/**} — read-only published-content browsing, unauthenticated.</li>
 *   <li>{@code /internal/**} — server-to-server indexing API, {@code permitAll()} here too since
 *       it carries no end-user JWT at all;
 *       {@link com.ttg.devknowledgeplatform.content.config.security.InternalApiKeyFilter} (not
 *       Spring Security) is what actually enforces the shared-secret header on these paths.</li>
 *   <li>{@code /api/v1/admin/**} — admin CRUD, requires {@code ROLE_ADMIN}.</li>
 * </ul>
 * Everything else requires authentication.
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
                .requestMatchers("/internal/**").permitAll()
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
