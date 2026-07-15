package com.ttg.devknowledgeplatform.social.dto.messaging;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for {@code POST /api/v1/groups/{groupId}/channels}.
 *
 * @param name channel name; must be unique within the group
 */
public record CreateChannelRequest(
        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must not exceed 100 characters")
        String name
) {
}
