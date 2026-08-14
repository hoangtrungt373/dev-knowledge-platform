package com.ttg.devknowledgeplatform.infra.security;

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

import lombok.RequiredArgsConstructor;

/**
 * Converts a verified Keycloak {@link Jwt} into the {@link CustomOAuth2User}-backed
 * {@link AbstractAuthenticationToken} a resource server's controllers expect.
 *
 * <p>Builds the principal directly from the token's claims — no local {@code User} row is
 * persisted or read. {@code jwt.getSubject()} (Keycloak's stable, UUID-shaped subject id) stands in
 * for {@code userUuid}; there is no locally-generated one. This is the "Option C" shape (see the
 * {@code project-microservices-extraction-plan} memory) every service with no need to *display*
 * another user's profile data follows — an ownership check only ever needs "is this row's
 * owner/author the caller," answerable from the JWT alone.
 *
 * <p>Shared here rather than duplicated per service — this exact logic used to be copy-pasted
 * identically across {@code gateway}'s, {@code ecommerce-service}'s, {@code task-service}'s,
 * {@code content-service}'s, and {@code ai-service}'s own {@code security} packages. Picked up
 * automatically by each of those services' existing
 * {@code @ComponentScan(basePackages = {"...own...", "com.ttg.devknowledgeplatform.infra"})}.
 *
 * <p><strong>Not used by {@code identity-service} or {@code social-service}</strong> — both
 * genuinely need more than claims alone (JIT-provisioning their own local {@code User}/
 * {@code SocialProfile} row respectively, since both search/list/join across *other* users' data),
 * so both keep their own local converter of the same class name in their own {@code security}
 * package rather than using this one. Those two local converters do still delegate to this
 * module's {@link KeycloakRealmRoleConverter} for the role-mapping half of the work.
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
        if (!KeycloakJwtConstants.ACCESS_TOKEN_TYPE.equals(jwt.getClaimAsString(KeycloakJwtConstants.TYPE_CLAIM))) {
            throw new BadCredentialsException("Token is not an access token");
        }

        Collection<GrantedAuthority> authorities = realmRoleConverter.convert(jwt);

        CustomOAuth2User principal = CustomOAuth2User.builder()
                .userUuid(jwt.getSubject())
                .email(jwt.getClaimAsString(StandardClaimNames.EMAIL))
                .name(jwt.getClaimAsString(StandardClaimNames.PREFERRED_USERNAME))
                .attributes(jwt.getClaims())
                .authorities(authorities)
                .build();

        return new UsernamePasswordAuthenticationToken(principal, jwt, authorities);
    }
}
