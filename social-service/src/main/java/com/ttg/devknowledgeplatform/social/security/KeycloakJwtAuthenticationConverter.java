package com.ttg.devknowledgeplatform.social.security;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ttg.devknowledgeplatform.common.dto.CustomOAuth2User;
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
 * <p>Deliberately duplicated from {@code gateway}'s/{@code ecommerce-service}'s/
 * {@code task-service}'s equivalent converter (not {@code identity-service}'s
 * {@code UserService.findOrCreateFromKeycloak} — that module is a standalone service now and can't
 * be called in-process). Unlike those converters, this one JIT-provisions {@link SocialProfile}, a
 * lean, module-local entity — never {@code common.entity.User} — so it only ever writes the fields
 * that entity actually has: no password placeholder, no OAuth provider, no role, no
 * email-verified/enabled flags. This module genuinely needs the persisted row: every
 * {@code @ManyToOne} in this module's entity graph (friend requests, friendships, blocks, group
 * membership, DM threads/messages) points at {@link SocialProfile}, and search/public-profile
 * lookups need to find *other* users by username/name — a bare JWT claim can't satisfy that the way
 * it does for {@code ecommerce-service}/{@code task-service}.
 */
@Component
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
        if (!"Bearer".equals(jwt.getClaimAsString("typ"))) {
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
        String email = jwt.getClaimAsString("email");
        String username = jwt.getClaimAsString("preferred_username");
        String firstName = jwt.getClaimAsString("given_name");
        String lastName = jwt.getClaimAsString("family_name");

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
