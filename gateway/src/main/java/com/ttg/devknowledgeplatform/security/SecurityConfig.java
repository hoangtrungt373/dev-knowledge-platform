package com.ttg.devknowledgeplatform.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

import com.ttg.devknowledgeplatform.infra.security.JsonAuthenticationEntryPoint;
import com.ttg.devknowledgeplatform.infra.security.KeycloakJwtAuthenticationConverter;

/**
 * Every REST request is authenticated as an OAuth2 resource server — Keycloak issues and owns the
 * whole login/registration/token lifecycle now, this app only ever verifies a bearer token against
 * Keycloak's JWKS ({@code spring.security.oauth2.resourceserver.jwt.issuer-uri}, resolved
 * automatically by Spring Boot's auto-config, no manual {@code JwtDecoder} bean needed here).
 * {@link KeycloakJwtAuthenticationConverter} (shared via {@code infra} now, not a local copy —
 * see that class's own Javadoc) builds the {@code CustomOAuth2User} principal straight from the
 * verified JWT's claims and derives {@code GrantedAuthority}s from the token's realm roles; no
 * local {@code User} row is persisted or read anymore.
 *
 * <p>No {@code @EnableMethodSecurity} here anymore — this app's only {@code @PreAuthorize}
 * consumer was {@code ai-service}'s {@code IngestionApi}, which now declares its own
 * {@code @EnableMethodSecurity} in its own standalone {@code SecurityConfig}. The
 * {@code /api/v1/admin/**} path rule below is kept even though this app has zero REST controllers
 * of its own today — harmless if unmatched, and ready if a future embedded module ever needs it
 * again.
 */
@Configuration
@EnableWebSecurity
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

                // Spring Actuator
                .requestMatchers("/actuator/**").permitAll()

                // Admin-only management (currently unmatched — see class Javadoc)
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/public/**").permitAll()
                .requestMatchers("/api/v1/users/public/**").permitAll()

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
            )
            .headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.disable())
            );

        return http.build();
    }
}
