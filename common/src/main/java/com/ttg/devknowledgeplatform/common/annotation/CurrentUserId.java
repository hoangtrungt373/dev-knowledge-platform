package com.ttg.devknowledgeplatform.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds the authenticated caller's identifier to a controller method parameter.
 *
 * <p>Every deployable in this reactor (`gateway`, `identity-service`, `social-service`,
 * `task-service`, `content-service`, `ai-service`) has its own local
 * {@code config.web.CurrentUserIdArgumentResolver} implementing this — never a shared class, so
 * each module can resolve to whichever type/strategy fits its own identity model. Two shapes exist
 * today: {@code gateway}/{@code identity-service}/{@code social-service} resolve an {@code Integer}
 * (that deployable's own local numeric PK, JIT-provisioned from the verified JWT); `task-service`/
 * `content-service`/`ai-service` resolve a {@code String} (the caller's Keycloak UUID, read
 * straight off the {@link com.ttg.devknowledgeplatform.common.dto.CustomOAuth2User} principal with
 * no database lookup at all, since those modules have no local `User` copy to resolve against —
 * see each module's own {@code CLAUDE.md} for which shape it uses and why). Lives here (not any
 * one feature module) so every module's controllers can use it without depending on another
 * module's resolver.
 *
 * <p>Usage (claims-based shape):
 * <pre>{@code
 * @GetMapping("/sessions")
 * public ResponseEntity<?> listSessions(@CurrentUserId String userUuid) {
 *     return ResponseEntity.ok(chatSessionService.listSessions(userUuid));
 * }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUserId {}
