package com.ttg.devknowledgeplatform.identity.security;

import java.util.Collection;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.common.dto.CustomOAuth2User;
import com.ttg.devknowledgeplatform.identity.entity.User;
import com.ttg.devknowledgeplatform.identity.enums.UserRole;
import com.ttg.devknowledgeplatform.identity.security.service.KeycloakUserInfo;
import com.ttg.devknowledgeplatform.identity.security.service.UserService;
import com.ttg.devknowledgeplatform.infra.security.KeycloakJwtConstants;
import com.ttg.devknowledgeplatform.infra.security.KeycloakRealmRoleConverter;

import lombok.RequiredArgsConstructor;

/**
 * Converts a verified Keycloak {@link Jwt} into the {@link CustomOAuth2User}-backed
 * {@link AbstractAuthenticationToken} this service's controllers expect, JIT-provisioning/
 * refreshing the local {@code User} row via {@link UserService#findOrCreateFromKeycloak}.
 *
 * <p>Unlike {@code gateway}'s and {@code ecommerce-service}'s equivalent converters (now a single
 * shared {@code infra.security.KeycloakJwtAuthenticationConverter} rather than per-service copies —
 * see that class's own Javadoc) — this module keeps its own local converter, since it genuinely
 * does more than claims-only work: it JIT-provisions/refreshes a real local {@code User} row via
 * this module's own in-process {@code UserService}, which the shared claims-only converter has no
 * way to do generically. It still delegates role-mapping to {@code infra}'s shared
 * {@link KeycloakRealmRoleConverter} rather than duplicating that half of the work — only the
 * JIT-provisioning logic is genuinely local here.
 *
 * <p><strong>Explicit bean name required.</strong> This module's {@code @ComponentScan} reaches
 * {@code infra}'s sibling package, which also hosts a class named {@code KeycloakJwtAuthenticationConverter}
 * (a different type — {@code infra.security.KeycloakJwtAuthenticationConverter}). Spring's default
 * bean-name generation uses only the simple class name, not the fully-qualified one, so without an
 * explicit name here both classes would register under the identical default name
 * {@code keycloakJwtAuthenticationConverter} and fail context startup with a
 * {@code ConflictingBeanDefinitionException} — injection itself is unaffected either way (it
 * resolves by type, and this module never injects the shared claims-only converter), but bean
 * *registration* requires unique names regardless of whether anything actually looks the name up.
 * {@code social-service}'s own local converter needs the same treatment for the same reason.
 *
 * <p><strong>Admin check composed from {@code UserRole}, not a bare string.</strong>
 * {@code infra.security.KeycloakJwtConstants} intentionally has no {@code ROLE_ADMIN} constant —
 * {@code infra} has zero dependency on any feature module (this module included), and "ADMIN" is a
 * business-domain role name owned by this module's own {@link UserRole} enum, not a generic
 * Keycloak/Spring Security mechanic the way {@code ROLE_} (the authority prefix convention) is.
 * This class composes the check from both: {@code KeycloakJwtConstants.ROLE_PREFIX +
 * UserRole.ADMIN.name()}, so it follows {@code UserRole}'s own enum constant rather than
 * duplicating "ADMIN" as an unrelated literal — the Keycloak realm's own {@code ADMIN} role name
 * (see {@code docker/keycloak/realm-export.json}) still has to be kept in sync with this by hand
 * regardless, since that's a separate system's config, not Java code this compiles against.
 */
@Component("identityKeycloakJwtAuthenticationConverter")
@RequiredArgsConstructor
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final KeycloakRealmRoleConverter realmRoleConverter;
    private final UserService userService;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // Keycloak access tokens carry typ=Bearer; refresh tokens carry typ=Refresh. Rejecting
        // anything else here mirrors the old JwtTokenProvider-era rule that a refresh token must
        // only ever be exchanged at the token endpoint, never used to authenticate a request.
        if (!KeycloakJwtConstants.ACCESS_TOKEN_TYPE.equals(jwt.getClaimAsString(KeycloakJwtConstants.TYPE_CLAIM))) {
            throw new BadCredentialsException("Token is not an access token");
        }

        Collection<GrantedAuthority> authorities = realmRoleConverter.convert(jwt);
        // Composed from the generic ROLE_ prefix (infra) + this module's own UserRole.ADMIN
        // (identity) rather than a bare "ROLE_ADMIN" string, so this check follows UserRole's own
        // enum constant name instead of duplicating it — see this class's own Javadoc for why a
        // business-domain role name doesn't belong in infra's generic KeycloakJwtConstants.
        String adminAuthority = KeycloakJwtConstants.ROLE_PREFIX + UserRole.ADMIN.name();
        boolean admin = authorities.stream().anyMatch(a -> adminAuthority.equals(a.getAuthority()));

        KeycloakUserInfo info = new KeycloakUserInfo(
                jwt.getSubject(),
                jwt.getClaimAsString(StandardClaimNames.EMAIL),
                jwt.getClaimAsString(StandardClaimNames.PREFERRED_USERNAME),
                jwt.getClaimAsString(StandardClaimNames.GIVEN_NAME),
                jwt.getClaimAsString(StandardClaimNames.FAMILY_NAME),
                admin,
                Boolean.TRUE.equals(jwt.getClaimAsBoolean(StandardClaimNames.EMAIL_VERIFIED)));

        User user = userService.findOrCreateFromKeycloak(info);

        CustomOAuth2User principal = CustomOAuth2User.builder()
                .userUuid(user.getUserUuid())
                .email(user.getEmail())
                .name(user.getUsername())
                .attributes(jwt.getClaims())
                .authorities(authorities)
                .build();

        return new UsernamePasswordAuthenticationToken(principal, jwt, authorities);
    }
}
