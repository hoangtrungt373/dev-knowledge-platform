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

import lombok.RequiredArgsConstructor;

/**
 * Converts a verified Keycloak {@link Jwt} into the {@link CustomOAuth2User}-backed
 * {@link AbstractAuthenticationToken} this app's security filter chain expects.
 *
 * <p>Builds the principal directly from the token's claims — no local {@code User} row is
 * persisted or read anymore, mirroring {@code ecommerce-service}'s/{@code task-service}'s/
 * {@code content-service}'s/{@code ai-service}'s converters rather than this class's own previous
 * revision (see {@code docs/CHANGELOG.md}). {@code identity-service}'s own {@code identity.USER} is
 * now the sole system-of-record for user identity in this reactor — this app never had a
 * controller that read its own JIT-provisioned row back (zero REST controllers of its own today),
 * and the authorization decision below was always driven by the token's own {@code realm_access.roles}
 * claim via {@link KeycloakRealmRoleConverter}, never by anything in that row. {@code jwt.getSubject()}
 * (Keycloak's stable, UUID-shaped subject id) stands in for {@code userUuid} here; there is no
 * locally-generated one anymore.
 */
@Component
@RequiredArgsConstructor
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final KeycloakRealmRoleConverter realmRoleConverter;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // Keycloak access tokens carry typ=Bearer; refresh tokens carry typ=Refresh. Rejecting
        // anything else here mirrors the old JwtTokenProvider-era rule that a refresh token must
        // only ever be exchanged at the token endpoint, never used to authenticate a request.
        if (!"Bearer".equals(jwt.getClaimAsString("typ"))) {
            throw new BadCredentialsException("Token is not an access token");
        }

        Collection<GrantedAuthority> authorities = realmRoleConverter.convert(jwt);

        CustomOAuth2User principal = CustomOAuth2User.builder()
                .userUuid(jwt.getSubject())
                .email(jwt.getClaimAsString("email"))
                .name(jwt.getClaimAsString("preferred_username"))
                .attributes(jwt.getClaims())
                .authorities(authorities)
                .build();

        return new UsernamePasswordAuthenticationToken(principal, jwt, authorities);
    }
}
