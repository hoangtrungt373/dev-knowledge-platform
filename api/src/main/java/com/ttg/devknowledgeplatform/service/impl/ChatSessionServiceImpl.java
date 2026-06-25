package com.ttg.devknowledgeplatform.service.impl;

import com.ttg.devknowledgeplatform.ai.service.ConversationSummarisationService;
import com.ttg.devknowledgeplatform.common.dto.ConversationContext;
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
 *
 * <h3>Rolling summarisation</h3>
 * <p>After every {@value #SUMMARY_TRIGGER_INTERVAL}th new Q&amp;A pair beyond the initial
 * {@value #SUMMARY_THRESHOLD}, the {@code addTurn} method compresses all turns before the
 * {@value #SUMMARY_RECENT_WINDOW}-pair verbatim window into a rolling summary stored on the
 * session. At the first trigger ({@value #SUMMARY_THRESHOLD} pairs) exactly
 * {@code SUMMARY_THRESHOLD − SUMMARY_RECENT_WINDOW = 7} pairs are compressed — enough content
 * to justify the LLM call. The summarisation call happens synchronously inside the transaction;
 * since it fires only once every {@value #SUMMARY_TRIGGER_INTERVAL} turns, the occasional extra
 * latency is acceptable. For a production system with strict p99 requirements, consider promoting
 * this to an {@code @Async} fire-and-forget call using the existing {@code ragStreamExecutor} pool.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Throwable.class)
public class ChatSessionServiceImpl implements ChatSessionService {

    static final int SESSION_TTL_HOURS = 24;

    /**
     * First trigger: summarise after this many Q&amp;A pairs have been saved.
     *
     * <p>Must satisfy {@code SUMMARY_THRESHOLD > SUMMARY_RECENT_WINDOW} so that the first
     * compression covers at least {@code SUMMARY_THRESHOLD - SUMMARY_RECENT_WINDOW} pairs.
     * With THRESHOLD=12 and WINDOW=5 the first run compresses 7 pairs — enough to justify
     * the LLM call cost. Setting this too low (e.g. 8) would compress only 3 pairs on the
     * first trigger, an unfavourable cost/benefit ratio.
     */
    static final int SUMMARY_THRESHOLD = 12;

    /** Re-trigger: generate a new summary every N new pairs beyond the threshold. */
    static final int SUMMARY_TRIGGER_INTERVAL = 4;

    /** Verbatim window: always keep the last N Q&A pairs uncompressed for near-context recall. */
    static final int SUMMARY_RECENT_WINDOW = 5;

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ConversationSummarisationService conversationSummarisationService;

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
            session.setSummary(null);
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
    public ConversationContext getConversationContext(Integer sessionId, int maxTurns) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CHAT_SESSION_NOT_FOUND));
        List<ConversationTurn> recentTurns = getRecentTurns(sessionId, maxTurns);
        return new ConversationContext(session.getSummary(), recentTurns);
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

        // nextIndex is 0-based; after saving this turn, total pair count = (nextIndex / 2) + 1.
        int pairCount = (nextIndex / 2) + 1;
        if (shouldSummarise(pairCount)) {
            updateRollingSummary(session);
        }

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

    /**
     * Returns {@code true} when a new rolling summary should be generated.
     *
     * <p>Triggers at {@value #SUMMARY_THRESHOLD} pairs and every {@value #SUMMARY_TRIGGER_INTERVAL}
     * pairs thereafter: {@code pairCount == THRESHOLD} OR
     * {@code (pairCount - THRESHOLD) % INTERVAL == 0 && pairCount > THRESHOLD}.
     */
    private boolean shouldSummarise(int pairCount) {
        if (pairCount < SUMMARY_THRESHOLD) return false;
        return (pairCount - SUMMARY_THRESHOLD) % SUMMARY_TRIGGER_INTERVAL == 0;
    }

    /**
     * Loads all messages for the session, compresses the older portion into a rolling summary,
     * and stores it on {@code session} (the caller is responsible for persisting the session).
     *
     * <p>Turns within the {@value #SUMMARY_RECENT_WINDOW}-pair verbatim window are excluded from
     * compression so the RAG pipeline always has fresh near-context available without re-parsing
     * the summary. All turns outside the window — including those already covered by the previous
     * summary — are passed to the LLM together with the previous summary, letting the model build
     * an updated summary incrementally rather than from scratch.
     */
    private void updateRollingSummary(ChatSession session) {
        List<ChatMessage> all = chatMessageRepository.findByChatSession_IdOrderByTurnIndexAsc(session.getId());
        int verbatimCount = SUMMARY_RECENT_WINDOW * 2;
        if (all.size() <= verbatimCount) {
            return;
        }

        List<ConversationTurn> toCompress = all.subList(0, all.size() - verbatimCount).stream()
                .map(m -> new ConversationTurn(m.getRole().name(), m.getContent()))
                .toList();

        String newSummary = conversationSummarisationService.summarise(session.getSummary(), toCompress);
        session.setSummary(newSummary);
        log.info("Updated rolling summary for session {} ({} turns compressed)", session.getId(), toCompress.size());
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
