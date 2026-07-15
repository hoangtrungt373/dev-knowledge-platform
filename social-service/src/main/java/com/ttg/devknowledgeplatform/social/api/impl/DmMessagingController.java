package com.ttg.devknowledgeplatform.social.api.impl;

import java.security.Principal;

import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.CommonErrorCode;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.common.repository.UserRepository;
import com.ttg.devknowledgeplatform.social.api.DmMessagingApi;
import com.ttg.devknowledgeplatform.social.dto.messaging.DmMessageResponse;
import com.ttg.devknowledgeplatform.social.dto.messaging.SendMessageRequest;
import com.ttg.devknowledgeplatform.social.dto.messaging.WsErrorResponse;
import com.ttg.devknowledgeplatform.social.mapper.MessagingMapper;
import com.ttg.devknowledgeplatform.social.entity.DmMessage;
import com.ttg.devknowledgeplatform.social.service.DmService;
import com.ttg.devknowledgeplatform.social.service.MessageAttachmentInput;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link DmMessagingApi}. A DM has no public topic to broadcast to — it's pushed
 * straight to both participants' private per-user queues via {@code convertAndSendToUser}, so
 * there's no subscribe-time authorization gap to close here the way {@code api}'s
 * {@code StompAuthChannelInterceptor} closes one for channel topics.
 *
 * <p><b>Deliberately does not read {@code message.getDmThread().getUser1()/getUser2()}</b> to find
 * the other participant. For an existing thread, {@code DmThread} is loaded fresh by
 * {@code DmServiceImpl.resolveOrCreateThread} via a plain repository query, so its {@code user1}/
 * {@code user2} associations are genuine lazy proxies — fine inside the service's own
 * {@code @Transactional} method, but this controller runs after that transaction has already
 * closed. Unlike REST controllers (covered by Spring Boot's default Open-Session-In-View, which
 * keeps the Hibernate session open for the whole HTTP request), STOMP message handling isn't
 * bound to that servlet-filter mechanism, so touching a lazy association here would risk a
 * {@code LazyInitializationException}. Resolving both usernames directly (the sender's from the
 * already-authenticated {@link Principal}, the recipient's via a fresh repository lookup) sidesteps
 * the whole issue.
 *
 * <p>{@code @MessageExceptionHandler} stays here rather than on {@link DmMessagingApi} —
 * {@code GroupApi}/{@code DmApi} don't declare exception handling on their REST interfaces either
 * (that's centralized in {@code GlobalExceptionHandler}), so this mirrors the same shape.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class DmMessagingController implements DmMessagingApi {

    private static final String DM_QUEUE = "/queue/dms";

    private final DmService dmService;
    private final MessagingMapper messagingMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    @Override
    public void sendMessage(String recipientUuid, SendMessageRequest request, Integer userId, Principal principal) {
        MessageAttachmentInput attachment = messagingMapper.toAttachmentInput(request.attachment());
        DmMessage message = dmService.sendMessage(userId, recipientUuid, request.content(), attachment);
        DmMessageResponse response = messagingMapper.toDmMessageResponse(message);

        messagingTemplate.convertAndSendToUser(principal.getName(), DM_QUEUE, response);
        String recipientUsername = userRepository.findByUserUuid(recipientUuid)
                .orElseThrow(() -> new ResourceNotFoundException(CommonErrorCode.USER_NOT_FOUND))
                .getUsername();
        messagingTemplate.convertAndSendToUser(recipientUsername, DM_QUEUE, response);
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public WsErrorResponse handleError(ApiException ex) {
        log.warn("STOMP DM rejected: {}", ex.getMessage());
        return new WsErrorResponse(ex.getErrorCode().getCode(), ex.getMessage());
    }
}
