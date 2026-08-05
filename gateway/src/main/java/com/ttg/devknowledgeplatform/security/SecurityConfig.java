package com.ttg.devknowledgeplatform.security;

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
 * Every REST request is authenticated as an OAuth2 resource server — Keycloak issues and owns the
 * whole login/registration/token lifecycle now, this app only ever verifies a bearer token against
 * Keycloak's JWKS ({@code spring.security.oauth2.resourceserver.jwt.issuer-uri}, resolved
 * automatically by Spring Boot's auto-config, no manual {@code JwtDecoder} bean needed here).
 * {@link KeycloakJwtAuthenticationConverter} both derives {@code GrantedAuthority}s from the
 * token's realm roles and JIT-provisions/refreshes the local {@code User} row — see its own
 * Javadoc.
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

                // Public content browsing
                .requestMatchers("/api/v1/public/**").permitAll()

                // Public user profiles
                .requestMatchers("/api/v1/users/public/**").permitAll()

                // WebSocket/STOMP handshake — browsers can't set an Authorization header on the
                // handshake request itself; real auth happens on the STOMP CONNECT frame via
                // StompAuthChannelInterceptor instead (see WebSocketConfig, this same package)
                .requestMatchers("/ws/**").permitAll()

                // Spring Actuator
                .requestMatchers("/actuator/**").permitAll()

                // Admin-only management
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
            )
            .headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.disable())
            );

        return http.build();
    }
}
