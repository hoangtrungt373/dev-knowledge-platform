package com.ttg.devknowledgeplatform.social.api;

import java.security.Principal;

import jakarta.validation.Valid;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;

import com.ttg.devknowledgeplatform.common.annotation.CurrentUserId;
import com.ttg.devknowledgeplatform.social.dto.messaging.SendMessageRequest;

/**
 * STOMP contract for live DM push — the messaging counterpart to {@link DmApi}'s
 * {@code POST /api/v1/dms/{recipientUuid}/messages}. The implementation
 * ({@link com.ttg.devknowledgeplatform.social.api.impl.DmMessagingController}) carries no messaging
 * annotations, same interface/impl split as the REST APIs in this package.
 *
 * <p>Deliberately narrower than {@link DmApi}: there's no live-push equivalent of list/history
 * operations, only the send path — reads still go through REST.
 */
public interface DmMessagingApi {

    /**
     * Receives a DM over {@code /app/dms/{recipientUuid}/messages} and delivers the saved result
     * to both participants' private {@code /queue/dms}.
     */
    @MessageMapping("/dms/{recipientUuid}/messages")
    void sendMessage(
            @DestinationVariable String recipientUuid,
            @Valid @Payload SendMessageRequest request,
            @CurrentUserId Integer userId,
            Principal principal);
}
