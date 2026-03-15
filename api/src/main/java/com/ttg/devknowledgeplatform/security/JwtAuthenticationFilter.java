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

import com.ttg.devknowledgeplatform.dto.CustomOAuth2User;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * JWT Authentication Filter
 * 
 * Extracts JWT token from Authorization header and validates it.
 * Sets authentication in SecurityContext if token is valid.
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
                String userId = jwtTokenProvider.getUserIdFromToken(token);
                
                if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    String role = jwtTokenProvider.getClaimFromToken(token, claims -> claims.get("role", String.class));
                    
                    CustomOAuth2User userDetails = CustomOAuth2User.builder()
                            .id(userId)
                            .email(email)
                            .name(jwtTokenProvider.getClaimFromToken(token, claims -> claims.get("username", String.class)))
                            .attributes(Collections.emptyMap())
                            .authorities(Collections.singletonList(
                                    new SimpleGrantedAuthority(role != null ? role : "ROLE_USER")))
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
