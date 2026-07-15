package com.ttg.devknowledgeplatform.social.dto.messaging;

import java.time.Instant;

import com.ttg.devknowledgeplatform.social.dto.friend.UserSummaryResponse;

/**
 * A single message within a channel.
 *
 * @param id          primary key
 * @param channelId   the owning channel's id
 * @param sender      public profile summary of the sender
 * @param messageType {@code TEXT}, {@code IMAGE}, or {@code FILE} — tags the primary content for
 *                     rendering only; {@code content} and {@code attachment} may both be present
 * @param content     message text; {@code null} if the message is attachment-only
 * @param attachment  attachment metadata; {@code null} if the message is text-only
 * @param createdAt   when the message was sent
 */
public record ChannelMessageResponse(
        Integer id,
        Integer channelId,
        UserSummaryResponse sender,
        String messageType,
        String content,
        MessageAttachmentResponse attachment,
        Instant createdAt
) {
}
