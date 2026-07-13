package com.ttg.devknowledgeplatform.social.service.impl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.common.exception.CommonErrorCode;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.common.repository.UserRepository;
import com.ttg.devknowledgeplatform.common.util.DateUtils;
import com.ttg.devknowledgeplatform.social.entity.DmMessage;
import com.ttg.devknowledgeplatform.social.entity.DmThread;
import com.ttg.devknowledgeplatform.social.enums.MessageType;
import com.ttg.devknowledgeplatform.social.enums.RelationshipStatus;
import com.ttg.devknowledgeplatform.social.exception.SocialErrorCode;
import com.ttg.devknowledgeplatform.social.repository.DmMessageRepository;
import com.ttg.devknowledgeplatform.social.repository.DmThreadRepository;
import com.ttg.devknowledgeplatform.social.service.DmService;
import com.ttg.devknowledgeplatform.social.service.FriendService;
import com.ttg.devknowledgeplatform.social.service.MessageAttachmentInput;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Throwable.class)
public class DmServiceImpl implements DmService {

    private final DmThreadRepository dmThreadRepository;
    private final DmMessageRepository dmMessageRepository;
    private final UserRepository userRepository;
    private final FriendService friendService;

    @Override
    public DmMessage sendMessage(Integer senderId, String recipientUuid, String content, MessageAttachmentInput attachment) {
        requireContentOrAttachment(content, attachment);

        User sender = resolveUser(senderId);
        User recipient = resolveUserByUuid(recipientUuid);

        // Single gate for "not friends" and "blocked" alike — reuses FriendService's own
        // mutual-invisibility-preserving lookup instead of re-querying Friendship/UserBlock here.
        if (friendService.getRelationshipStatus(senderId, recipientUuid) != RelationshipStatus.FRIENDS) {
            throw new BusinessException(SocialErrorCode.DM_FRIEND_REQUIRED);
        }

        DmThread thread = resolveOrCreateThread(sender, recipient);

        DmMessage saved = dmMessageRepository.save(DmMessage.builder()
                .dmThread(thread)
                .sender(sender)
                .messageType(resolveMessageType(attachment))
                .content(content)
                .attachmentObjectKey(attachment != null ? attachment.objectKey() : null)
                .attachmentMimeType(attachment != null ? attachment.mimeType() : null)
                .attachmentFileName(attachment != null ? attachment.fileName() : null)
                .attachmentFileSize(attachment != null ? attachment.fileSize() : null)
                .build());

        thread.setLastMessageAt(DateUtils.getCurrentDateTime());
        dmThreadRepository.save(thread);

        log.info("User {} sent DM message {} in thread {}", senderId, saved.getId(), thread.getId());
        return saved;
    }

    @Override
    public Page<DmThread> listMyThreads(Integer userId, Pageable pageable) {
        return dmThreadRepository.findAllForUser(resolveUser(userId), pageable);
    }

    @Override
    public Page<DmMessage> listMessages(Integer userId, Integer threadId, Pageable pageable) {
        DmThread thread = dmThreadRepository.findById(threadId)
                .orElseThrow(() -> new ResourceNotFoundException(SocialErrorCode.DM_THREAD_NOT_FOUND));
        if (!thread.getUser1().getId().equals(userId) && !thread.getUser2().getId().equals(userId)) {
            // Same error as "doesn't exist" — a non-participant shouldn't learn the thread exists.
            throw new ResourceNotFoundException(SocialErrorCode.DM_THREAD_NOT_FOUND);
        }
        return dmMessageRepository.findByDmThreadOrderByDteCreationDesc(thread, pageable);
    }

    private DmThread resolveOrCreateThread(User a, User b) {
        User[] pair = canonicalize(a, b);
        return dmThreadRepository.findByUser1AndUser2(pair[0], pair[1])
                .orElseGet(() -> dmThreadRepository.save(DmThread.builder().user1(pair[0]).user2(pair[1]).build()));
    }

    private static void requireContentOrAttachment(String content, MessageAttachmentInput attachment) {
        if ((content == null || content.isBlank()) && attachment == null) {
            throw new BusinessException(CommonErrorCode.VALIDATION_FIELD_INVALID, "Message must have text or an attachment");
        }
    }

    private static MessageType resolveMessageType(MessageAttachmentInput attachment) {
        if (attachment == null) {
            return MessageType.TEXT;
        }
        return attachment.mimeType() != null && attachment.mimeType().startsWith("image/")
                ? MessageType.IMAGE
                : MessageType.FILE;
    }

    private User[] canonicalize(User a, User b) {
        return a.getId() < b.getId() ? new User[] {a, b} : new User[] {b, a};
    }

    private User resolveUser(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(CommonErrorCode.USER_NOT_FOUND));
    }

    private User resolveUserByUuid(String userUuid) {
        return userRepository.findByUserUuid(userUuid)
                .orElseThrow(() -> new ResourceNotFoundException(CommonErrorCode.USER_NOT_FOUND, "User not found: " + userUuid));
    }
}
