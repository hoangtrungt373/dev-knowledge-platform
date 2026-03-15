package com.ttg.devknowledgeplatform.common.util;


import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for getting current user information from Spring Security context.
 *
 * <p>This utility is designed to be used across all services and supports
 * multiple authentication types:</p>
 * <ul>
 *   <li>OAuth2User (OAuth2 login)</li>
 *   <li>JWT token (JWT authentication)</li>
 *   <li>String principal (username)</li>
 * </ul>
 */

/**
 * Utility class for getting current user information from Spring Security context.
 *
 * <p>This utility is designed to be used across all services and supports
 * multiple authentication types:</p>
 * <ul>
 *   <li>OAuth2User (OAuth2 login)</li>
 *   <li>JWT token (JWT authentication)</li>
 *   <li>String principal (username)</li>
 * </ul>
 */
@Slf4j
public final class UserUtils {

    private static final String DEFAULT_USER = "system";

    private UserUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Get the current username from Spring Security context
     *
     * @return username if authenticated, otherwise "system"
     */
    public static String getUserName() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null || !authentication.isAuthenticated()) {
                return DEFAULT_USER;
            }

            Object principal = authentication.getPrincipal();

            // Handle OAuth2User (OAuth2 login)
            if (principal instanceof OAuth2User) {
                OAuth2User oAuth2User = (OAuth2User) principal;
                // Try common attributes for name
                String name = oAuth2User.getAttribute("name");
                if (name != null && !name.trim().isEmpty()) {
                    return name;
                }
                String email = oAuth2User.getAttribute("email");
                if (email != null && !email.trim().isEmpty()) {
                    return email;
                }
                return oAuth2User.getName();
            }

            // Handle JWT token (JWT authentication)
            if (principal instanceof Jwt) {
                Jwt jwt = (Jwt) principal;
                String username = jwt.getClaimAsString("username");
                if (username != null && !username.trim().isEmpty()) {
                    return username;
                }
                // Fallback to subject (email)
                return jwt.getSubject() != null ? jwt.getSubject() : DEFAULT_USER;
            }

            // Handle String principal (username)
            if (principal instanceof String) {
                return (String) principal;
            }

            // Fallback to authentication name
            String name = authentication.getName();
            return name != null && !name.equals("anonymousUser") ? name : DEFAULT_USER;

        } catch (Exception e) {
            log.warn("Error getting current user from security context: {}", e.getMessage());
            return DEFAULT_USER;
        }
    }

    /**
     * Get the current user email from Spring Security context
     *
     * @return email if available, otherwise null
     */
    public static String getUserEmail() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null || !authentication.isAuthenticated()) {
                return null;
            }

            Object principal = authentication.getPrincipal();

            // Handle OAuth2User (OAuth2 login)
            if (principal instanceof OAuth2User) {
                OAuth2User oAuth2User = (OAuth2User) principal;
                return oAuth2User.getAttribute("email");
            }

            // Handle JWT token (JWT authentication)
            if (principal instanceof Jwt) {
                Jwt jwt = (Jwt) principal;
                String email = jwt.getClaimAsString("email");
                if (email != null) {
                    return email;
                }
                // Fallback to subject (email)
                return jwt.getSubject();
            }

            return null;

        } catch (Exception e) {
            log.warn("Error getting current user email from security context: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get the current user ID from Spring Security context
     *
     * @return user ID if available, otherwise null
     */
    public static String getUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null || !authentication.isAuthenticated()) {
                return null;
            }

            Object principal = authentication.getPrincipal();

            // Handle OAuth2User (OAuth2 login)
            if (principal instanceof OAuth2User) {
                OAuth2User oAuth2User = (OAuth2User) principal;
                // Try common ID attributes
                Object id = oAuth2User.getAttribute("id");
                if (id != null) {
                    return id.toString();
                }
                Object sub = oAuth2User.getAttribute("sub");
                if (sub != null) {
                    return sub.toString();
                }
                return oAuth2User.getName();
            }

            // Handle JWT token (JWT authentication)
            if (principal instanceof Jwt) {
                Jwt jwt = (Jwt) principal;
                return jwt.getClaimAsString("userId");
            }

            return null;

        } catch (Exception e) {
            log.warn("Error getting current user ID from security context: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Check if there is an authenticated user
     *
     * @return true if user is authenticated, false otherwise
     */
    public static boolean isAuthenticated() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            return authentication != null && authentication.isAuthenticated()
                    && !authentication.getName().equals("anonymousUser");
        } catch (Exception e) {
            return false;
        }
    }
}
