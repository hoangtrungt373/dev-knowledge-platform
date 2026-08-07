package com.ttg.devknowledgeplatform.task.security;

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
 * persisted or read, mirroring {@code ecommerce-service}'s converter rather than {@code gateway}'s/
 * {@code identity-service}'s. {@code Project.ownerUuid}/{@code Task.ownerUuid} are plain columns,
 * not foreign keys onto a local {@code User} table: every ownership check this module does is "is
 * this row's owner the caller," answerable by comparing two UUIDs, and this module never needs to
 * *display* another user's profile (username/avatar) — the two things that would otherwise justify
 * a persisted copy (see the {@code project-microservices-extraction-plan} memory's "Option C"
 * discussion, originally written for {@code ecommerce-service} but equally applicable here).
 * {@code jwt.getSubject()} (Keycloak's stable, UUID-shaped subject id) stands in for
 * {@code userUuid} here; there is no locally-generated one. If a future feature needs to show
 * another user's profile info from this module (e.g. shared projects), reach for an event-driven
 * read-model projection ("Option B") rather than resurrecting a persisted {@code User} copy.
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
