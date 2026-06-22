package com.ttg.devknowledgeplatform.dto.chat;

import com.ttg.devknowledgeplatform.ai.dto.RagAnswer;
import com.ttg.devknowledgeplatform.ai.dto.RagSource;

import java.util.List;

/**
 * Response body for {@code POST /api/v1/chat}.
 *
 * @param answer    LLM-generated answer grounded in the retrieved knowledge-base chunks.
 * @param sources   the knowledge-base chunks used as context, ordered by cosine similarity descending;
 *                  clients can render these as citations or expand them on demand
 * @param sessionId the active conversation session ID; pass this back in the next request's
 *                  {@code sessionId} field to maintain conversation continuity
 */
public record ChatResponse(
        String answer,
        List<RagSource> sources,
        Integer sessionId
) {
    /**
     * Converts the internal {@link RagAnswer} from the AI service into the API response shape.
     *
     * @param ragAnswer result produced by {@link com.ttg.devknowledgeplatform.ai.service.RagQueryService}
     * @param sessionId the active session ID to return to the client
     * @return chat response ready for serialization
     */
    public static ChatResponse from(RagAnswer ragAnswer, Integer sessionId) {
        return new ChatResponse(ragAnswer.answer(), ragAnswer.sources(), sessionId);
    }
}
