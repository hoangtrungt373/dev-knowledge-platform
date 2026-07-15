package com.ttg.devknowledgeplatform.social.dto.messaging;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload for {@code PUT /api/v1/groups/{groupId}/members/{userUuid}/role}.
 *
 * @param role {@code ADMIN} or {@code MEMBER} — {@code OWNER} is rejected by the service, ownership
 *             is not reassignable via this endpoint
 */
public record ChangeRoleRequest(
        @NotBlank(message = "role is required")
        String role
) {
}
