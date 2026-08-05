package com.ttg.devknowledgeplatform.security;

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
 * {@link AbstractAuthenticationToken} every REST/STOMP call site in this reactor already expects —
 * the same principal shape the old {@code JwtAuthenticationFilter} built, so
 * {@code @CurrentUserId}/{@code @AuthenticationPrincipal CustomOAuth2User} elsewhere in the reactor
 * needs no changes. JIT-provisions/refreshes the local {@code User} row via
 * {@link UserService#findOrCreateFromKeycloak} along the way.
 *
 * <p>Shared by both the REST filter chain ({@code SecurityConfig}'s {@code oauth2ResourceServer}
 * wiring) and STOMP {@code CONNECT} authentication ({@code StompAuthChannelInterceptor}) — exactly
 * one JIT-provisioning code path, not two.
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
