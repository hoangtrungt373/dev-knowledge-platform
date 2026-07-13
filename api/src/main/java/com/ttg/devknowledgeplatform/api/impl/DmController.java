package com.ttg.devknowledgeplatform.api.impl;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.ttg.devknowledgeplatform.api.DmApi;
import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.messaging.DmMessageResponse;
import com.ttg.devknowledgeplatform.dto.messaging.DmThreadResponse;
import com.ttg.devknowledgeplatform.dto.messaging.SendMessageRequest;
import com.ttg.devknowledgeplatform.mapper.MessagingMapper;
import com.ttg.devknowledgeplatform.social.service.DmService;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link DmApi}.
 */
@RestController
@RequiredArgsConstructor
public class DmController implements DmApi {

    private final DmService dmService;
    private final MessagingMapper messagingMapper;

    @Override
    public ResponseEntity<DmMessageResponse> sendMessage(Integer userId, String recipientUuid, SendMessageRequest request) {
        var attachment = messagingMapper.toAttachmentInput(request.attachment());
        var message = dmService.sendMessage(userId, recipientUuid, request.content(), attachment);
        return ResponseEntity.status(HttpStatus.CREATED).body(messagingMapper.toDmMessageResponse(message));
    }

    @Override
    public ResponseEntity<PagedResponse<DmThreadResponse>> listMyThreads(Integer userId, int page, int size) {
        var result = dmService.listMyThreads(userId, PageRequest.of(page, size))
                .map(thread -> messagingMapper.toDmThreadResponse(thread, userId));
        return ResponseEntity.ok(PagedResponse.from(result));
    }

    @Override
    public ResponseEntity<PagedResponse<DmMessageResponse>> listMessages(Integer userId, Integer threadId, int page, int size) {
        var result = dmService.listMessages(userId, threadId, PageRequest.of(page, size))
                .map(messagingMapper::toDmMessageResponse);
        return ResponseEntity.ok(PagedResponse.from(result));
    }
}
