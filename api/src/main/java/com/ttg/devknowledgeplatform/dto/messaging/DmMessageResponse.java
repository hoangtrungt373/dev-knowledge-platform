package com.ttg.devknowledgeplatform.dto.messaging;

import java.time.Instant;

import com.ttg.devknowledgeplatform.dto.friend.UserSummaryResponse;

/**
 * A single message within a 1:1 DM thread.
 *
 * @param id          primary key
 * @param threadId    the owning thread's id
 * @param sender      public profile summary of the sender
 * @param messageType {@code TEXT}, {@code IMAGE}, or {@code FILE} — tags the primary content for
 *                     rendering only; {@code content} and {@code attachment} may both be present
 * @param content     message text; {@code null} if the message is attachment-only
 * @param attachment  attachment metadata; {@code null} if the message is text-only
 * @param createdAt   when the message was sent
 */
public record DmMessageResponse(
        Integer id,
        Integer threadId,
        UserSummaryResponse sender,
        String messageType,
        String content,
        MessageAttachmentResponse attachment,
        Instant createdAt
) {
}
