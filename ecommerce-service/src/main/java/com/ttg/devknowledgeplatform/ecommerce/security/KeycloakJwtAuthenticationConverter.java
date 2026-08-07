package com.ttg.devknowledgeplatform.ecommerce.security;

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
 * {@link AbstractAuthenticationToken} this service's controllers expect.
 *
 * <p>Builds the principal directly from the token's claims — no local {@code User} row is
 * persisted or read (see the {@code project-microservices-extraction-plan} memory for the
 * "Option C" decision behind this). This module has no entity of its own with a foreign key onto a
 * user (no {@code createdBy}/{@code ownerId}), so the only thing it ever needed from Keycloak was
 * "who is the caller, and are they an admin" — both fully answerable from the verified JWT itself.
 * {@code jwt.getSubject()} (Keycloak's stable, UUID-shaped subject id) stands in for
 * {@code userUuid} here; there is no locally-generated one anymore. If a future feature needs a
 * real local reference to a user (e.g. Epic 5's {@code Review.userId}), don't resurrect a
 * persisted {@code User} row for it — store the bare {@code userUuid}/subject as a plain column
 * with no FK/join, and reach for the event-driven read-model projection ("Option B") only if that
 * feature also needs to *display* another user's info (username/avatar), mirroring this module's
 * existing {@code OutboxEvent}/{@code OutboxRelay} pattern.
 */
@Component
@RequiredArgsConstructor
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final KeycloakRealmRoleConverter realmRoleConverter;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // Keycloak access tokens carry typ=Bearer; refresh tokens carry typ=Refresh. Rejecting
        // anything else here mirrors the old JwtVerifier-era rule that a refresh token must only
        // ever be exchanged at the token endpoint, never used to authenticate a request.
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
