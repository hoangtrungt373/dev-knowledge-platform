package com.ttg.devknowledgeplatform.identity.security;

import java.util.Collection;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.common.dto.CustomOAuth2User;
import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.identity.security.service.KeycloakUserInfo;
import com.ttg.devknowledgeplatform.identity.security.service.UserService;

import lombok.RequiredArgsConstructor;

/**
 * Converts a verified Keycloak {@link Jwt} into the {@link CustomOAuth2User}-backed
 * {@link AbstractAuthenticationToken} this service's controllers expect, JIT-provisioning/
 * refreshing the local {@code User} row via {@link UserService#findOrCreateFromKeycloak}.
 *
 * <p>Unlike {@code gateway}'s and {@code ecommerce-service}'s equivalent converters — which inline
 * the find-or-create logic directly via {@code UserRepository} because they have no in-process
 * access to this module's {@code UserService} — this converter can call {@code UserService}
 * directly, since both live in this same standalone app. No duplication needed here; this is the
 * one converter in the reactor that still delegates rather than inlining.
 */
@Component
@RequiredArgsConstructor
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final KeycloakRealmRoleConverter realmRoleConverter;
    private final UserService userService;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // Keycloak access tokens carry typ=Bearer; refresh tokens carry typ=Refresh. Rejecting
        // anything else here mirrors the old JwtTokenProvider-era rule that a refresh token must
        // only ever be exchanged at the token endpoint, never used to authenticate a request.
        if (!"Bearer".equals(jwt.getClaimAsString("typ"))) {
            throw new BadCredentialsException("Token is not an access token");
        }

        Collection<GrantedAuthority> authorities = realmRoleConverter.convert(jwt);
        boolean admin = authorities.stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

        KeycloakUserInfo info = new KeycloakUserInfo(
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("given_name"),
                jwt.getClaimAsString("family_name"),
                admin);

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
