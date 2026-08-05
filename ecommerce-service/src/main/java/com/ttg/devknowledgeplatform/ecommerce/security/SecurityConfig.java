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
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * This service's own security filter chain — independent of {@code gateway}'s, since once
 * extracted this app runs on its own port and must guard its own endpoints regardless of whether
 * {@code gateway} is proxying to it. Mirrors {@code gateway}'s {@code /api/v1/admin/**}/
 * {@code /api/v1/public/**} rule shape, minus everything specific to browser OAuth2 login (this
 * service never issues tokens or handles the login flow, only verifies bearer tokens issued
 * elsewhere — see {@link JwtVerifier}).
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/api/v1/public/**").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            );

        return http.build();
    }
}
