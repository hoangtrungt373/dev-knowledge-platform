package com.ttg.devknowledgeplatform.social.api;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ttg.devknowledgeplatform.common.annotation.CurrentUserId;
import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.social.dto.messaging.DmMessageResponse;
import com.ttg.devknowledgeplatform.social.dto.messaging.DmThreadResponse;
import com.ttg.devknowledgeplatform.social.dto.messaging.SendMessageRequest;

/**
 * HTTP contract for 1:1 direct messaging.
 *
 * <p>The implementation ({@link com.ttg.devknowledgeplatform.social.api.impl.DmController}) carries no
 * HTTP annotations. Sending a message requires an accepted friendship between the caller and the
 * recipient — same rejection whether the real reason is "not friends" or "blocked", preserving
 * the mutual-invisibility guarantee {@code FriendService} already provides.
 */
@RequestMapping("/api/v1/dms")
public interface DmApi {

    /**
     * Sends a message to {@code recipientUuid}, creating the DM thread lazily if this is the
     * first message between the pair. {@code content}/{@code attachment} may both be present.
     */
    @PostMapping("/{recipientUuid}/messages")
    ResponseEntity<DmMessageResponse> sendMessage(
            @CurrentUserId Integer userId, @PathVariable String recipientUuid, @Valid @RequestBody SendMessageRequest request);

    /**
     * Returns the caller's DM conversations, most recently active first.
     *
     * @param page zero-based page number (default 0)
     * @param size page size (default 20)
     */
    @GetMapping
    ResponseEntity<PagedResponse<DmThreadResponse>> listMyThreads(
            @CurrentUserId Integer userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size);

    /**
     * Returns a thread's message history, most recent first. Requires the caller to be one of
     * the thread's two participants.
     *
     * @param page zero-based page number (default 0)
     * @param size page size (default 20)
     */
    @GetMapping("/{threadId}/messages")
    ResponseEntity<PagedResponse<DmMessageResponse>> listMessages(
            @CurrentUserId Integer userId,
            @PathVariable Integer threadId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size);
}
