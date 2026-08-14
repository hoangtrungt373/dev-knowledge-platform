package com.ttg.devknowledgeplatform.social.security;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ttg.devknowledgeplatform.common.dto.CustomOAuth2User;
import com.ttg.devknowledgeplatform.infra.security.KeycloakJwtConstants;
import com.ttg.devknowledgeplatform.infra.security.KeycloakRealmRoleConverter;
import com.ttg.devknowledgeplatform.social.entity.SocialProfile;
import com.ttg.devknowledgeplatform.social.enums.ProfileStatus;
import com.ttg.devknowledgeplatform.social.repository.SocialProfileRepository;

import lombok.RequiredArgsConstructor;

/**
 * Converts a verified Keycloak {@link Jwt} into the {@link CustomOAuth2User}-backed
 * {@link AbstractAuthenticationToken} this service's controllers expect, JIT-provisioning/
 * refreshing this app's own local {@link SocialProfile} row directly via
 * {@link SocialProfileRepository}.
 *
 * <p>Kept as this module's own local converter rather than {@code infra}'s shared, claims-only
 * {@code infra.security.KeycloakJwtAuthenticationConverter} (used by {@code gateway}/
 * {@code ecommerce-service}/{@code task-service}/{@code content-service}/{@code ai-service} — see
 * that class's own Javadoc) — not {@code identity-service}'s
 * {@code UserService.findOrCreateFromKeycloak} either, since that module is a standalone service
 * now and can't be called in-process. Unlike the shared converter, this one JIT-provisions
 * {@link SocialProfile}, a lean, module-local entity — never {@code common.entity.User} — so it
 * only ever writes the fields that entity actually has: no password placeholder, no OAuth
 * provider, no role, no email-verified/enabled flags. This module genuinely needs the persisted
 * row: every {@code @ManyToOne} in this module's entity graph (friend requests, friendships,
 * blocks, group membership, DM threads/messages) points at {@link SocialProfile}, and
 * search/public-profile lookups need to find *other* users by username/name — a bare JWT claim
 * can't satisfy that the way it does for {@code ecommerce-service}/{@code task-service}. Still
 * delegates role-mapping to {@code infra}'s shared {@link KeycloakRealmRoleConverter} rather than
 * duplicating that half of the work.
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
 * {@code identity-service}'s own local converter needs the same treatment for the same reason.
 */
@Component("socialKeycloakJwtAuthenticationConverter")
@RequiredArgsConstructor
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final KeycloakRealmRoleConverter realmRoleConverter;
    private final SocialProfileRepository socialProfileRepository;

    @Override
    @Transactional
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // Keycloak access tokens carry typ=Bearer; refresh tokens carry typ=Refresh. Rejecting
        // anything else here mirrors the old JwtTokenProvider-era rule that a refresh token must
        // only ever be exchanged at the token endpoint, never used to authenticate a request.
        if (!KeycloakJwtConstants.ACCESS_TOKEN_TYPE.equals(jwt.getClaimAsString(KeycloakJwtConstants.TYPE_CLAIM))) {
            throw new BadCredentialsException("Token is not an access token");
        }

        Collection<GrantedAuthority> authorities = realmRoleConverter.convert(jwt);

        SocialProfile profile = findOrCreateProfile(jwt);

        CustomOAuth2User principal = CustomOAuth2User.builder()
                .userUuid(profile.getProfileUuid())
                .email(profile.getEmail())
                .name(profile.getUsername())
                .attributes(jwt.getClaims())
                .authorities(authorities)
                .build();

        return new UsernamePasswordAuthenticationToken(principal, jwt, authorities);
    }

    private SocialProfile findOrCreateProfile(Jwt jwt) {
        String subject = jwt.getSubject();
        String email = jwt.getClaimAsString(StandardClaimNames.EMAIL);
        String username = jwt.getClaimAsString(StandardClaimNames.PREFERRED_USERNAME);
        String firstName = jwt.getClaimAsString(StandardClaimNames.GIVEN_NAME);
        String lastName = jwt.getClaimAsString(StandardClaimNames.FAMILY_NAME);

        SocialProfile profile = socialProfileRepository.findByKeycloakSubjectId(subject)
                .or(() -> socialProfileRepository.findByEmail(email))
                .orElseGet(() -> SocialProfile.builder()
                        .profileUuid(UUID.randomUUID().toString())
                        .status(ProfileStatus.OFFLINE)
                        .build());

        boolean isNew = profile.getId() == null;

        boolean changed = isNew
                || !Objects.equals(profile.getKeycloakSubjectId(), subject)
                || !Objects.equals(profile.getEmail(), email)
                || !Objects.equals(profile.getUsername(), username)
                || !Objects.equals(profile.getFirstName(), firstName)
                || !Objects.equals(profile.getLastName(), lastName);

        if (!changed) {
            return profile;
        }

        profile.setKeycloakSubjectId(subject);
        profile.setEmail(email);
        profile.setUsername(username);
        profile.setFirstName(firstName);
        profile.setLastName(lastName);

        return socialProfileRepository.save(profile);
    }
}
