package com.ttg.devknowledgeplatform.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds the authenticated user's integer primary key to a controller method parameter.
 *
 * <p>Resolved by {@code com.ttg.devknowledgeplatform.config.web.CurrentUserIdArgumentResolver}
 * (`api`), which reads the {@link com.ttg.devknowledgeplatform.common.dto.CustomOAuth2User}
 * principal from the {@code SecurityContext} — the same principal that `JwtAuthenticationFilter`
 * populates from the JWT token, so no database lookup is performed. Lives here (not `api`) so
 * every feature module's controllers can use it without depending on `api`.
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
