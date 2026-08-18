package com.ttg.devknowledgeplatform.identity.security.service.impl;

import java.util.List;
import java.util.regex.Pattern;

import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;

import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.common.exception.CommonErrorCode;
import com.ttg.devknowledgeplatform.identity.config.KeycloakAdminProperties;
import com.ttg.devknowledgeplatform.identity.dto.auth.RegisterRequest;
import com.ttg.devknowledgeplatform.identity.exception.IdentityErrorCode;
import com.ttg.devknowledgeplatform.identity.security.service.KeycloakAdminService;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link KeycloakAdminService}, backed by Keycloak's Admin REST API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakAdminServiceImpl implements KeycloakAdminService {

    // Keeps generated usernames on the same [a-z0-9_] alphabet gui/CLAUDE.md's edit-profile
    // validation already requires — anything else in the email's local part (dots, plusses,
    // hyphens) collapses to a single underscore rather than being dropped, so "first.last+tag"
    // doesn't silently become "firstlasttag".
    private static final Pattern USERNAME_INVALID_CHARS = Pattern.compile("[^a-z0-9_]+");
    private static final Pattern USERNAME_EDGE_UNDERSCORES = Pattern.compile("^_+|_+$");
    // Matches the max length gui's own username-edit validation enforces (Dashboard.tsx) — an
    // auto-generated username should never already violate the rule a manual edit is held to.
    private static final int USERNAME_MAX_LENGTH = 30;
    // Astronomically unlikely to ever exhaust (a real local-part collision needs two different
    // emails whose sanitized local part is identical) — just a hard stop against looping forever.
    private static final int USERNAME_MAX_SUFFIX_ATTEMPTS = 50;

    private final Keycloak keycloakAdminClient;
    private final KeycloakAdminProperties properties;

    @Override
    public void createUser(RegisterRequest request) {
        UserRepresentation user = new UserRepresentation();
        user.setEmail(request.email());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEnabled(true);
        // Created unverified — sendVerifyEmail below emails a real Keycloak "Verify Email"
        // action-token link, replacing the previous "created pre-verified" scope choice.
        user.setEmailVerified(false);

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(request.password());
        credential.setTemporary(false);
        user.setCredentials(List.of(credential));

        String usernameBase = deriveUsernameBase(request.email());
        String keycloakUserId = null;
        int suffix = 0;
        while (keycloakUserId == null) {
            user.setUsername(withSuffix(usernameBase, suffix));
            try (Response response = keycloakAdminClient.realm(properties.getRealm()).users().create(user)) {
                if (response.getStatus() == Response.Status.CONFLICT.getStatusCode()) {
                    // Keycloak's own conflict body distinguishes which field collided ("User exists
                    // with same username"/"...same email") — only a username collision should be
                    // retried with the next suffix; the email is never something we generated, so an
                    // email conflict (or an exhausted suffix budget) is a genuine registration
                    // failure the caller needs to see.
                    String detail = response.readEntity(String.class);
                    boolean usernameTaken = detail != null && detail.toLowerCase().contains("username");
                    if (usernameTaken && suffix < USERNAME_MAX_SUFFIX_ATTEMPTS) {
                        suffix++;
                        continue;
                    }
                    throw new BusinessException(IdentityErrorCode.EMAIL_ALREADY_EXISTS, new Object[]{request.email()});
                }
                if (response.getStatus() != Response.Status.CREATED.getStatusCode()) {
                    log.error("Keycloak user creation failed for {}: HTTP {}", request.email(), response.getStatus());
                    throw new BusinessException(IdentityErrorCode.KEYCLOAK_USER_CREATE_FAILED);
                }
                keycloakUserId = CreatedResponseUtil.getCreatedId(response);
            }
        }

        // Best-effort — a mail-server hiccup shouldn't fail registration itself (the account
        // exists either way); the user can always trigger resendVerificationEmail later.
        try {
            sendVerifyEmail(keycloakUserId);
        } catch (Exception e) {
            log.warn("Account {} created but failed to send its verification email: {}", request.email(), e.getMessage());
        }
    }

    /**
     * Sanitizes an email's local part (before {@code @}) into a username candidate matching this
     * app's own username alphabet — lowercase, {@code [a-z0-9_]} only, capped at
     * {@link #USERNAME_MAX_LENGTH}. Falls back to {@code "user"} if nothing usable survives
     * sanitization (e.g. a local part made up entirely of dots/pluses).
     */
    private String deriveUsernameBase(String email) {
        String localPart = email.substring(0, email.indexOf('@')).toLowerCase();
        String sanitized = USERNAME_EDGE_UNDERSCORES.matcher(
                USERNAME_INVALID_CHARS.matcher(localPart).replaceAll("_")
        ).replaceAll("");
        if (sanitized.isEmpty()) {
            sanitized = "user";
        }
        return sanitized.length() > USERNAME_MAX_LENGTH ? sanitized.substring(0, USERNAME_MAX_LENGTH) : sanitized;
    }

    /**
     * Appends a numeric disambiguator to {@code base} on a username collision, truncating the
     * base as needed so the result never exceeds {@link #USERNAME_MAX_LENGTH}. {@code suffix == 0}
     * (the first attempt) returns {@code base} unchanged.
     */
    private String withSuffix(String base, int suffix) {
        if (suffix == 0) {
            return base;
        }
        String suffixStr = String.valueOf(suffix);
        int allowedBaseLength = Math.max(1, USERNAME_MAX_LENGTH - suffixStr.length());
        String truncatedBase = base.length() > allowedBaseLength ? base.substring(0, allowedBaseLength) : base;
        return truncatedBase + suffixStr;
    }

    @Override
    public void resendVerificationEmail(String keycloakSubjectId) {
        try {
            sendVerifyEmail(keycloakSubjectId);
        } catch (Exception e) {
            log.error("Failed to resend verification email for Keycloak subject {}: {}", keycloakSubjectId, e.getMessage());
            throw new BusinessException(IdentityErrorCode.VERIFICATION_EMAIL_SEND_FAILED);
        }
    }

    @Override
    public void updateUsername(String keycloakSubjectId, String newUsername) {
        var userResource = keycloakAdminClient.realm(properties.getRealm()).users().get(keycloakSubjectId);
        UserRepresentation representation = userResource.toRepresentation();
        representation.setUsername(newUsername);
        try {
            // void, not Response — same generated-client shape as sendVerifyEmail below; a non-2xx
            // reply throws rather than returning a status code to check by hand.
            userResource.update(representation);
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == Response.Status.CONFLICT.getStatusCode()) {
                throw new BusinessException(CommonErrorCode.USER_USERNAME_ALREADY_EXISTS, new Object[]{newUsername});
            }
            log.error("Keycloak username update failed for subject {}: HTTP {}", keycloakSubjectId, e.getResponse().getStatus());
            throw new BusinessException(IdentityErrorCode.KEYCLOAK_USER_UPDATE_FAILED);
        }
    }

    private void sendVerifyEmail(String keycloakUserId) {
        // void, not Response — the generated client proxy throws (e.g. WebApplicationException)
        // on a non-2xx reply itself, so there's no status code here to check by hand.
        // client_id/redirect_uri land the user back in this app instead of Keycloak's default
        // target — its own generic "account" client confirmation page, a dead end from this app's
        // perspective. /login rather than /dashboard deliberately: if the user already logged out
        // since registering, GuestRoute would otherwise bounce them /dashboard -> /login anyway (an
        // extra pointless hop to the same place); if they're still logged in, GuestRoute's own
        // already-authenticated redirect sends them on to /dashboard regardless — one target
        // handles both cases via routing logic gui already has. emailVerified=true lets whichever
        // page actually renders (Login.tsx or, via that redirect, Dashboard.tsx) show a one-time
        // confirmation toast instead of silently landing with no feedback either way.
        keycloakAdminClient.realm(properties.getRealm())
                .users().get(keycloakUserId)
                .sendVerifyEmail("gui", properties.getFrontendUrl() + "/login?emailVerified=true");
    }
}
