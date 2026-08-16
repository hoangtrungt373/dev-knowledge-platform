package com.ttg.devknowledgeplatform.identity.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for {@code POST /api/v1/auth/register} — creates a new Keycloak user account.
 */
public record RegisterRequest(

        @NotBlank(message = "First name is required")
        @Size(max = 255, message = "First name must be less than 255 characters")
        String firstName,

        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password
) {
}
