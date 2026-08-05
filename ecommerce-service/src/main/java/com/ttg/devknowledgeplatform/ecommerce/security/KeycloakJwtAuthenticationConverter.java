package com.ttg.devknowledgeplatform.ecommerce.security;

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
import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.common.enums.UserRole;
import com.ttg.devknowledgeplatform.common.enums.UserStatus;
import com.ttg.devknowledgeplatform.common.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Converts a verified Keycloak {@link Jwt} into the {@link CustomOAuth2User}-backed
 * {@link AbstractAuthenticationToken} this service's controllers expect, JIT-provisioning/
 * refreshing the local {@code User} row directly via {@link UserRepository}.
 *
 * <p>Deliberately duplicated from {@code gateway}/{@code identity-service}'s equivalent
 * converter/{@code UserService.findOrCreateFromKeycloak}, not shared — this module has no Maven
 * dependency on either (see this module's own {@code CLAUDE.md}), the same
 * standalone-deployability reasoning that led the now-deleted {@code JwtVerifier} to verify tokens
 * independently rather than depend on {@code identity-service}'s {@code JwtTokenProvider}.
 */
@Component
@RequiredArgsConstructor
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    // Never read/compared — Keycloak owns credentials entirely now. A real (non-null) value is
    // still required: User.password is a bean-validation @NotNull column that predates Keycloak.
    private static final String KEYCLOAK_MANAGED_PASSWORD_PLACEHOLDER = "KEYCLOAK_MANAGED";

    private final KeycloakRealmRoleConverter realmRoleConverter;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // Keycloak access tokens carry typ=Bearer; refresh tokens carry typ=Refresh. Rejecting
        // anything else here mirrors the old JwtVerifier-era rule that a refresh token must only
        // ever be exchanged at the token endpoint, never used to authenticate a request.
        if (!"Bearer".equals(jwt.getClaimAsString("typ"))) {
            throw new BadCredentialsException("Token is not an access token");
        }

        Collection<GrantedAuthority> authorities = realmRoleConverter.convert(jwt);
        boolean admin = authorities.stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

        User user = findOrCreateUser(jwt, admin);

        CustomOAuth2User principal = CustomOAuth2User.builder()
                .userUuid(user.getUserUuid())
                .email(user.getEmail())
                .name(user.getUsername())
                .attributes(jwt.getClaims())
                .authorities(authorities)
                .build();

        return new UsernamePasswordAuthenticationToken(principal, jwt, authorities);
    }

    private User findOrCreateUser(Jwt jwt, boolean admin) {
        String subject = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String username = jwt.getClaimAsString("preferred_username");
        String firstName = jwt.getClaimAsString("given_name");
        String lastName = jwt.getClaimAsString("family_name");

        User user = userRepository.findByKeycloakSubjectId(subject)
                .or(() -> userRepository.findByEmail(email))
                .orElseGet(() -> User.builder()
                        .userUuid(UUID.randomUUID().toString())
                        .password(KEYCLOAK_MANAGED_PASSWORD_PLACEHOLDER)
                        .status(UserStatus.OFFLINE)
                        .build());

        boolean isNew = user.getId() == null;
        UserRole targetRole = admin ? UserRole.ADMIN : UserRole.USER;

        boolean changed = isNew
                || !Objects.equals(user.getKeycloakSubjectId(), subject)
                || !Objects.equals(user.getEmail(), email)
                || !Objects.equals(user.getUsername(), username)
                || !Objects.equals(user.getFirstName(), firstName)
                || !Objects.equals(user.getLastName(), lastName)
                || user.getRole() != targetRole
                || !Boolean.TRUE.equals(user.getEmailVerified())
                || !Boolean.TRUE.equals(user.getEnabled());

        if (!changed) {
            return user;
        }

        user.setKeycloakSubjectId(subject);
        user.setEmail(email);
        user.setUsername(username);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setRole(targetRole);
        user.setEmailVerified(true);
        user.setEnabled(true);

        return userRepository.save(user);
    }
}
