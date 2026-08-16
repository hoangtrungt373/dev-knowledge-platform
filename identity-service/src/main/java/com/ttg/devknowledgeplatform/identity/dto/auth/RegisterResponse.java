package com.ttg.devknowledgeplatform.identity.dto.auth;

/**
 * Response payload for {@code POST /api/v1/auth/register} — matches {@code gui}'s
 * {@code RegisterResponse} type ({@code features/auth/types.ts}) exactly. The account is created
 * already enabled and email-verified (see {@code KeycloakAdminService}), so {@code gui}'s
 * {@code SignUp.tsx} logs the user in immediately afterward rather than waiting on this response
 * to carry tokens.
 */
public record RegisterResponse(String email, String message) {
}
