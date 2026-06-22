package com.ttg.devknowledgeplatform.service.impl;

import com.ttg.devknowledgeplatform.common.dto.ConversationTurn;
import com.ttg.devknowledgeplatform.common.entity.ChatMessage;
import com.ttg.devknowledgeplatform.common.entity.ChatSession;
import com.ttg.devknowledgeplatform.common.enums.ChatMessageRole;
import com.ttg.devknowledgeplatform.common.exception.ErrorCode;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.common.util.DateUtils;
import com.ttg.devknowledgeplatform.dto.chat.ChatSessionSummaryDto;
import com.ttg.devknowledgeplatform.repository.ChatMessageRepository;
import com.ttg.devknowledgeplatform.repository.ChatSessionRepository;
import com.ttg.devknowledgeplatform.service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Default {@link ChatSessionService} implementation.
 *
 * <p>Session expiry is enforced lazily on access: when a session is retrieved and found
 * to be inactive for more than {@value #SESSION_TTL_HOURS} hours, its messages are cleared
 * via cascade delete (orphanRemoval = true) and {@code lastActivityAt} is reset. The session
 * record itself is kept so the client's stored session ID remains valid.
 *
 * <p>{@link #addTurn} is intentionally safe to call from background threads (e.g. the
 * LangChain4j callback thread in the streaming flow). It opens its own transaction and
 * does not rely on the calling thread's security context — audit columns fall back to
 * {@code "system"} in that case, which is acceptable for message rows.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Throwable.class)
public class ChatSessionServiceImpl implements ChatSessionService {

    static final int SESSION_TTL_HOURS = 24;

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Override
    public Integer getOrCreateSessionId(Integer requestedSessionId, Integer userId) {
        if (requestedSessionId == null) {
            return createNewSession(userId);
        }
        ChatSession session = chatSessionRepository.findByIdAndUserId(requestedSessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CHAT_SESSION_NOT_FOUND));
        if (isExpired(session)) {
            log.info("Session {} expired; clearing history for user {}", requestedSessionId, userId);
            session.getMessages().clear();
            session.setLastActivityAt(DateUtils.getCurrentDateTime());
            chatSessionRepository.save(session);
        }
        return session.getId();
    }

    @Override
    public List<ConversationTurn> getRecentTurns(Integer sessionId, int maxTurns) {
        // Fetch the most recent (maxTurns * 2) messages in descending order, then reverse to chronological.
        List<ChatMessage> recent = chatMessageRepository.findByChatSession_IdOrderByTurnIndexDesc(
                sessionId, PageRequest.of(0, maxTurns * 2));
        List<ChatMessage> chronological = new ArrayList<>(recent);
        Collections.reverse(chronological);
        return chronological.stream()
                .map(m -> new ConversationTurn(m.getRole().name(), m.getContent()))
                .toList();
    }

    @Override
    public void addTurn(Integer sessionId, String question, String answer) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CHAT_SESSION_NOT_FOUND));

        int nextIndex = chatMessageRepository.findMaxTurnIndexBySessionId(sessionId) + 1;

        // Auto-generate the session title from the first question (capped at 100 chars)
        if (nextIndex == 0 && session.getTitle() == null) {
            session.setTitle(question.length() > 100 ? question.substring(0, 100) : question);
        }

        ChatMessage userMsg = new ChatMessage();
        userMsg.setChatSession(session);
        userMsg.setRole(ChatMessageRole.USER);
        userMsg.setContent(question);
        userMsg.setTurnIndex(nextIndex);

        ChatMessage aiMsg = new ChatMessage();
        aiMsg.setChatSession(session);
        aiMsg.setRole(ChatMessageRole.ASSISTANT);
        aiMsg.setContent(answer);
        aiMsg.setTurnIndex(nextIndex + 1);

        chatMessageRepository.save(userMsg);
        chatMessageRepository.save(aiMsg);

        session.setLastActivityAt(DateUtils.getCurrentDateTime());
        chatSessionRepository.save(session);

        log.debug("Saved turn {} to session {}", nextIndex / 2, sessionId);
    }

    @Override
    public List<ChatSessionSummaryDto> listSessions(Integer userId) {
        return chatSessionRepository.findSessionSummariesByUserId(userId);
    }

    @Override
    public List<ChatMessage> getHistory(Integer sessionId, Integer userId) {
        chatSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CHAT_SESSION_NOT_FOUND));
        return chatMessageRepository.findByChatSession_IdOrderByTurnIndexAsc(sessionId);
    }

    private Integer createNewSession(Integer userId) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setLastActivityAt(DateUtils.getCurrentDateTime());
        Integer id = chatSessionRepository.save(session).getId();
        log.debug("Created new chat session {} for user {}", id, userId);
        return id;
    }

    private boolean isExpired(ChatSession session) {
        return session.getLastActivityAt()
                .plusSeconds(SESSION_TTL_HOURS * 3600L)
                .isBefore(DateUtils.getCurrentDateTime());
    }
}
