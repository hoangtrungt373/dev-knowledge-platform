package com.ttg.devknowledgeplatform.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds the authenticated user's integer primary key to a controller method parameter.
 *
 * <p>Resolved by {@link com.ttg.devknowledgeplatform.config.web.CurrentUserIdArgumentResolver},
 * which reads the {@link com.ttg.devknowledgeplatform.dto.CustomOAuth2User} principal from
 * the {@code SecurityContext} — the same principal that {@code JwtAuthenticationFilter}
 * populates from the JWT token, so no database lookup is performed.
 *
 * <p>Usage:
 * <pre>{@code
 * @GetMapping("/sessions")
 * public ResponseEntity<?> listSessions(@CurrentUserId Integer userId) {
 *     return ResponseEntity.ok(chatSessionService.listSessions(userId));
 * }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUserId {}
