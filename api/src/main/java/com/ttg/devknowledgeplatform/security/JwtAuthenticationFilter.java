package com.ttg.devknowledgeplatform.security;

import java.io.IOException;
import java.util.Collections;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import io.jsonwebtoken.JwtException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.ttg.devknowledgeplatform.common.dto.CustomOAuth2User;
import com.ttg.devknowledgeplatform.identity.security.JwtTokenProvider;
import com.ttg.devknowledgeplatform.identity.security.jwt.AccessTokenClaims;
import com.ttg.devknowledgeplatform.identity.security.jwt.TokenClaims;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servlet filter that authenticates requests carrying a JWT access token.
 *
 * <p>On each request the filter:
 * <ol>
 *   <li>Reads the {@code Authorization: Bearer <token>} header.</li>
 *   <li>Validates the token's signature and expiration via {@link JwtTokenProvider}.</li>
 *   <li>Reconstructs a {@link CustomOAuth2User} principal from the token claims and
 *       sets it on the {@link org.springframework.security.core.context.SecurityContext}
 *       — only if the claims are an {@link com.ttg.devknowledgeplatform.security.jwt.AccessTokenClaims};
 *       a refresh token presented here is rejected, since it must only ever be exchanged via
 *       {@link JwtTokenProvider#refreshToken(String)}, never used to authenticate a request
 *       directly.</li>
 * </ol>
 *
 * <p>If the token is absent, invalid, expired, or not an access token, the filter passes the
 * request through unauthenticated — Spring Security's access rules then decide whether to allow
 * or deny it. This design avoids short-circuiting the filter chain so that public endpoints
 * continue to work without a token.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        
        try {
            // Extract token from Authorization header
            String token = getTokenFromRequest(request);
            
            if (token != null && validateToken(token)) {
                // Extract user info from token
                String email = jwtTokenProvider.getUsernameFromToken(token);

                if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    TokenClaims claims = jwtTokenProvider.parseClaims(token);

                    if (!(claims instanceof AccessTokenClaims accessClaims)) {
                        log.warn("Rejected non-access token presented for authentication (user: {})", email);
                    } else {
                        CustomOAuth2User userDetails = CustomOAuth2User.builder()
                                .userUuid(accessClaims.userUuid())
                                .email(email)
                                .name(accessClaims.username())
                                .attributes(Collections.emptyMap())
                                .authorities(Collections.singletonList(
                                        new SimpleGrantedAuthority(
                                                accessClaims.role() != null ? accessClaims.role() : "ROLE_USER")))
                                .build();

                        // Create authentication object
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails,
                                        null,
                                        userDetails.getAuthorities()
                                );
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                        // Set authentication in SecurityContext
                        SecurityContextHolder.getContext().setAuthentication(authentication);

                        log.debug("JWT authentication successful for user: {}", email);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
        }
        
        filterChain.doFilter(request, response);
    }

    /**
     * Extract JWT token from Authorization header
     * Format: "Bearer {token}"
     */
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * Validate JWT token (signature and expiration)
     */
    private boolean validateToken(String token) {
        try {
            // Check if token is expired
            if (jwtTokenProvider.isTokenExpired(token)) {
                log.warn("JWT token is expired");
                return false;
            }
            
            // Validate token structure and signature
            jwtTokenProvider.getUsernameFromToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }
}
