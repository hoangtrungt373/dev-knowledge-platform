package com.ttg.devknowledgeplatform.social.dto.messaging;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for {@code POST /api/v1/groups}.
 *
 * @param name display name for the new group
 */
public record CreateGroupRequest(
        @NotBlank(message = "name is required")
        @Size(max = 255, message = "name must not exceed 255 characters")
        String name
) {
}
