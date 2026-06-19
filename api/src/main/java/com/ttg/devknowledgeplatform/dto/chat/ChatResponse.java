package com.ttg.devknowledgeplatform.dto.chat;

import com.ttg.devknowledgeplatform.ai.dto.RagAnswer;
import com.ttg.devknowledgeplatform.ai.dto.RagSource;

import java.util.List;

/**
 * Response body for {@code POST /api/v1/chat}.
 *
 * @param answer  LLM-generated answer grounded in the retrieved knowledge-base chunks.
 * @param sources the knowledge-base chunks used as context, ordered by cosine similarity descending;
 *                clients can render these as citations or expand them on demand
 */
public record ChatResponse(
        String answer,
        List<RagSource> sources
) {
    /**
     * Converts the internal {@link RagAnswer} from the AI service into the API response shape.
     *
     * @param ragAnswer result produced by {@link com.ttg.devknowledgeplatform.ai.service.RagQueryService}
     * @return chat response ready for serialization
     */
    public static ChatResponse from(RagAnswer ragAnswer) {
        return new ChatResponse(ragAnswer.answer(), ragAnswer.sources());
    }
}
