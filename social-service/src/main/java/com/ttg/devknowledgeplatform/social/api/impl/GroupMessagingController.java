package com.ttg.devknowledgeplatform.social.api.impl;

import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.social.api.GroupMessagingApi;
import com.ttg.devknowledgeplatform.social.dto.messaging.ChannelMessageResponse;
import com.ttg.devknowledgeplatform.social.dto.messaging.SendMessageRequest;
import com.ttg.devknowledgeplatform.social.dto.messaging.WsErrorResponse;
import com.ttg.devknowledgeplatform.social.mapper.MessagingMapper;
import com.ttg.devknowledgeplatform.social.entity.ChannelMessage;
import com.ttg.devknowledgeplatform.social.service.GroupService;
import com.ttg.devknowledgeplatform.social.service.MessageAttachmentInput;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link GroupMessagingApi} — same {@link GroupService} + {@link MessagingMapper}
 * the REST controller ({@code GroupController}) uses, just a different transport: a client sends to
 * {@code /app/channels/{channelId}/messages} instead of POSTing, and every subscriber of
 * {@code /topic/channels/{channelId}} (membership-gated by {@code api}'s
 * {@code StompAuthChannelInterceptor}) receives the saved message live instead of having to
 * re-fetch.
 *
 * <p>Safe from the lazy-association trap {@link DmMessagingController} has to work around: both
 * {@code ChannelMessage.sender} and {@code .channel} here are the very objects
 * {@code GroupServiceImpl.postMessage} already fetched by ID in this same call, not proxies
 * re-loaded from a bulk query — so mapping them right after the service call returns is safe even
 * without an open Hibernate session (STOMP handling isn't covered by Open-Session-In-View, unlike
 * the REST controllers).
 *
 * <p>{@code @MessageExceptionHandler} stays here rather than on {@link GroupMessagingApi} —
 * {@code GroupApi}/{@code DmApi} don't declare exception handling on their REST interfaces either
 * (that's centralized in {@code GlobalExceptionHandler}), so this mirrors the same shape.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class GroupMessagingController implements GroupMessagingApi {

    private final GroupService groupService;
    private final MessagingMapper messagingMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void postMessage(Integer channelId, SendMessageRequest request, Integer userId) {
        MessageAttachmentInput attachment = messagingMapper.toAttachmentInput(request.attachment());
        ChannelMessage message = groupService.postMessage(userId, channelId, request.content(), attachment);
        ChannelMessageResponse response = messagingMapper.toChannelMessageResponse(message);
        messagingTemplate.convertAndSend("/topic/channels/" + channelId, response);
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public WsErrorResponse handleError(ApiException ex) {
        log.warn("STOMP channel message rejected: {}", ex.getMessage());
        return new WsErrorResponse(ex.getErrorCode().getCode(), ex.getMessage());
    }
}
