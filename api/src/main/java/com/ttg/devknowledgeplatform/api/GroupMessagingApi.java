package com.ttg.devknowledgeplatform.api;

import jakarta.validation.Valid;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;

import com.ttg.devknowledgeplatform.annotation.CurrentUserId;
import com.ttg.devknowledgeplatform.dto.messaging.SendMessageRequest;

/**
 * STOMP contract for live channel-message push — the messaging counterpart to {@link GroupApi}'s
 * {@code POST /api/v1/channels/{channelId}/messages}. The implementation
 * ({@link com.ttg.devknowledgeplatform.api.impl.GroupMessagingController}) carries no messaging
 * annotations, same interface/impl split as the REST APIs in this package.
 *
 * <p>Deliberately narrower than {@link GroupApi}: there's no live-push equivalent of list/history
 * operations, only the send path — reads still go through REST.
 */
public interface GroupMessagingApi {

    /**
     * Receives a channel message over {@code /app/channels/{channelId}/messages} and broadcasts
     * the saved result to every {@code /topic/channels/{channelId}} subscriber.
     */
    @MessageMapping("/channels/{channelId}/messages")
    void postMessage(
            @DestinationVariable Integer channelId,
            @Valid @Payload SendMessageRequest request,
            @CurrentUserId Integer userId);
}
