package com.ttg.devknowledgeplatform.ecommerce.security;

import com.ttg.devknowledgeplatform.common.dto.CustomOAuth2User;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Authenticates requests carrying a bearer JWT, using this module's own {@link JwtVerifier}
 * rather than {@code identity-service}'s {@code JwtTokenProvider} — see that class's Javadoc for
 * why. Mirrors {@code gateway}'s {@code JwtAuthenticationFilter} in shape: on any failure (no
 * token, invalid, expired, a refresh token), the request just passes through unauthenticated —
 * Spring Security's access rules decide whether that's allowed, so public endpoints keep working
 * without a token.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtVerifier jwtVerifier;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            jwtVerifier.verify(token).ifPresent(verified -> authenticate(verified, request));
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(JwtVerifier.VerifiedToken verified, HttpServletRequest request) {
        CustomOAuth2User principal = CustomOAuth2User.builder()
                .userUuid(verified.userUuid())
                .email(verified.email())
                .name(verified.username())
                .attributes(Collections.emptyMap())
                .authorities(Collections.singletonList(
                        new SimpleGrantedAuthority(verified.role() != null ? verified.role() : "ROLE_USER")))
                .build();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
