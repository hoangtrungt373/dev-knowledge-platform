package com.ttg.devknowledgeplatform.content.security;

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
 * persisted or read, mirroring {@code ecommerce-service}'s/{@code task-service}'s converter rather
 * than {@code gateway}'s/{@code identity-service}'s. {@code ContentItem.authorUuid} is a plain
 * column, never a foreign key onto a local {@code User} table: nothing in this module ever reads
 * it back or joins through it (it's stamped once at creation and never displayed), so there is no
 * "show me another user's profile" need that would justify a persisted copy (see the
 * {@code project-microservices-extraction-plan} memory's "Option C" discussion, originally written
 * for {@code ecommerce-service} but equally applicable here). {@code jwt.getSubject()} (Keycloak's
 * stable, UUID-shaped subject id) stands in for {@code userUuid} here; there is no locally-generated
 * one.
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
