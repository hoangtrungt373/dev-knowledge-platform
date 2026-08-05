package com.ttg.devknowledgeplatform.identity.security.service;

/**
 * The subset of a verified Keycloak access token's claims {@link UserService#findOrCreateFromKeycloak}
 * needs to JIT-provision or refresh the corresponding local {@code User} row.
 *
 * <p>{@code admin} is a plain boolean, not the raw {@code realm_access.roles} list — resolving
 * "which realm role name means our admin role" is the JWT-claim boundary code's job (the same
 * converter that builds this record from a {@link org.springframework.security.oauth2.jwt.Jwt}
 * also derives the caller's {@code GrantedAuthority} set from that same claim), not this service's.
 *
 * @param subject   the token's {@code sub} claim — Keycloak's stable per-account identifier
 * @param email     the token's {@code email} claim
 * @param username  the token's {@code preferred_username} claim
 * @param firstName the token's {@code given_name} claim, if present
 * @param lastName  the token's {@code family_name} claim, if present
 * @param admin     whether the token's realm roles include this app's admin role
 */
public record KeycloakUserInfo(
        String subject,
        String email,
        String username,
        String firstName,
        String lastName,
        boolean admin) {
}
