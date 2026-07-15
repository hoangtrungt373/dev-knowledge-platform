package com.ttg.devknowledgeplatform.security;

import com.ttg.devknowledgeplatform.identity.security.handler.OAuth2LoginSuccessHandler;
import com.ttg.devknowledgeplatform.identity.security.service.CustomOAuth2UserService;
import com.ttg.devknowledgeplatform.identity.security.service.CustomOidcUserService;
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
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomOidcUserService customOidcUserService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final CorsConfigurationSource corsConfigurationSource;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JsonAuthenticationEntryPoint authenticationEntryPoint;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // Auth endpoints — login, register, refresh, OAuth2 flow, token exchange
                .requestMatchers(
                    "/api/v1/auth/login",
                    "/api/v1/auth/register",
                    "/api/v1/auth/verify-otp",
                    "/api/v1/auth/resend-otp",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/exchange-state",
                    "/api/v1/auth/oauth2/**"
                ).permitAll()

                // Public content browsing
                .requestMatchers("/api/v1/public/**").permitAll()

                // Public user profiles
                .requestMatchers("/api/v1/users/public/**").permitAll()

                // WebSocket/STOMP handshake — browsers can't set an Authorization header on the
                // handshake request itself; real auth happens on the STOMP CONNECT frame via
                // StompAuthChannelInterceptor instead (see WebSocketConfig, this same package)
                .requestMatchers("/ws/**").permitAll()

                // OAuth2 internal redirects
                .requestMatchers("/login/**", "/oauth2/**").permitAll()

                // Spring Actuator
                .requestMatchers("/actuator/**").permitAll()

                // Admin-only management
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                // Current authenticated user info
                .requestMatchers("/api/v1/auth/user").authenticated()

                .anyRequest().authenticated()
            )
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService)
                    .oidcUserService(customOidcUserService)
                )
                .successHandler(oAuth2LoginSuccessHandler)
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
