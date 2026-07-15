package com.ttg.devknowledgeplatform.social.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.ttg.devknowledgeplatform.social.dto.messaging.MessageAttachmentInput;
import com.ttg.devknowledgeplatform.social.entity.DmMessage;
import com.ttg.devknowledgeplatform.social.entity.DmThread;

/**
 * Owns 1:1 direct messaging: threads are created lazily on the first message and require an
 * accepted friendship between the two participants (delegates to {@link FriendService} for that
 * check) — the friend-gated counterpart to {@link GroupService}'s open-add multi-user chat.
 *
 * <p>Returns entities rather than REST DTOs, same convention as {@link FriendService}.
 */
public interface DmService {

    /**
     * Sends a message from {@code senderId} to {@code recipientUuid}, creating the
     * {@link DmThread} lazily if this is the first message between the pair.
     *
     * <p>Requires an accepted friendship between the two users. Rejects with the same error
     * whether the reason is "not friends" or "blocked" — never reveals which, preserving the
     * mutual-invisibility guarantee {@link FriendService} already provides.
     *
     * @param content    message text; may be {@code null} if {@code attachment} is present
     * @param attachment attachment metadata; may be {@code null} if {@code content} is present
     */
    DmMessage sendMessage(Integer senderId, String recipientUuid, String content, MessageAttachmentInput attachment);

    /** {@code userId}'s DM conversations, most recently active first. */
    Page<DmThread> listMyThreads(Integer userId, Pageable pageable);

    /**
     * Paginated message history for a thread. Throws the same not-found error whether the thread
     * doesn't exist or {@code userId} isn't one of its two participants.
     */
    Page<DmMessage> listMessages(Integer userId, Integer threadId, Pageable pageable);
}
