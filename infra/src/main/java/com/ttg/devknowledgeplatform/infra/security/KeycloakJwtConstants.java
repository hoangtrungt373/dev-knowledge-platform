package com.ttg.devknowledgeplatform.infra.security;

/**
 * Claim/authority string constants used when converting a verified Keycloak {@code Jwt}, for the
 * pieces {@code org.springframework.security.oauth2.core.oidc.StandardClaimNames} doesn't cover —
 * Keycloak's own token-type/realm-role claim shape isn't part of the OIDC standard-claims set, and
 * Spring Security's {@code ROLE_} authority-prefix convention isn't a claim at all.
 *
 * <p>The standard OIDC claims this converter also reads ({@code email}, {@code preferred_username},
 * {@code given_name}, {@code family_name}) intentionally have no constant here — use Spring
 * Security's own {@code StandardClaimNames} for those instead of duplicating them.
 */
public final class KeycloakJwtConstants {

    private KeycloakJwtConstants() {
    }

    /** Distinguishes an access token ({@link #ACCESS_TOKEN_TYPE}) from a refresh token. */
    public static final String TYPE_CLAIM = "typ";
    public static final String ACCESS_TOKEN_TYPE = "Bearer";

    /** Keycloak's nested realm-role claim shape: {@code realm_access: { roles: [...] } }. */
    public static final String REALM_ACCESS_CLAIM = "realm_access";
    public static final String ROLES_CLAIM = "roles";

    /**
     * Spring Security's authority-prefix convention — {@code hasRole("ADMIN")} is shorthand for
     * "has an authority literally named {@code ROLE_ADMIN}". Deliberately no {@code ROLE_ADMIN}
     * constant here: "ADMIN" is a business-domain role name owned by whichever module has its own
     * role enum (e.g. {@code identity-service}'s {@code UserRole}), not a generic mechanic —
     * compose it at the call site instead ({@code ROLE_PREFIX + UserRole.ADMIN.name()}), so that
     * check follows the owning module's own enum rather than duplicating the name as a bare
     * literal. {@code infra} has zero dependency on any feature module, so it can't reference that
     * enum here even if it wanted to.
     */
    public static final String ROLE_PREFIX = "ROLE_";
}
