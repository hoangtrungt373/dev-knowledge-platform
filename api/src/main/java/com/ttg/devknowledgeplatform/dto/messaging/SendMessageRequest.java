package com.ttg.devknowledgeplatform.dto.messaging;

import jakarta.validation.Valid;

/**
 * Payload for sending a DM or channel message. At least one of {@code content}/{@code attachment}
 * must be present — enforced by the service, not bean validation, since it's a cross-field rule.
 *
 * @param content    message text; may be omitted if {@code attachment} is present
 * @param attachment attachment metadata; may be omitted if {@code content} is present
 */
public record SendMessageRequest(
        String content,
        @Valid MessageAttachmentRequest attachment
) {
}
