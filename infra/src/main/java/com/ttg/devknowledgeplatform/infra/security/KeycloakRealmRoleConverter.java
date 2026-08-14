package com.ttg.devknowledgeplatform.infra.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Maps a Keycloak-issued JWT's {@code realm_access.roles} claim to Spring Security
 * {@link GrantedAuthority}s, prefixed {@code ROLE_}. Spring's own default
 * {@code JwtGrantedAuthoritiesConverter} only reads a flat {@code scope}/{@code scp} claim —
 * Keycloak nests realm role names under {@code realm_access.roles} instead, so that default
 * converter would silently grant nothing.
 *
 * <p>Shared here rather than duplicated per service (as it used to be, in all seven standalone
 * apps' own {@code security} packages) — this logic is 100% identical across every one of them and
 * has no module-specific dependency at all, unlike {@link KeycloakJwtAuthenticationConverter}'s
 * JIT-provisioning variants. Picked up automatically by every service's existing
 * {@code @ComponentScan(basePackages = {"...own...", "com.ttg.devknowledgeplatform.infra"})} — the
 * same mechanism that already carries {@code config.json.JacksonConfig} to all seven apps.
 */
@Component
public class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim(KeycloakJwtConstants.REALM_ACCESS_CLAIM);
        if (realmAccess == null
                || !(realmAccess.get(KeycloakJwtConstants.ROLES_CLAIM) instanceof List<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(String.class::cast)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(KeycloakJwtConstants.ROLE_PREFIX + role))
                .collect(Collectors.toList());
    }
}
