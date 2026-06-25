package com.ttg.devknowledgeplatform.ai.pipeline;

import com.ttg.devknowledgeplatform.ai.config.EmbeddingProperties;
import com.ttg.devknowledgeplatform.ai.dto.RagPipelineContext;
import com.ttg.devknowledgeplatform.common.dto.ConversationContext;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Pipeline stage that rewrites an ambiguous follow-up question into a fully
 * self-contained standalone query suitable for vector similarity search.
 *
 * <p>When a user asks <em>"Does PostgreSQL support it?"</em> after discussing HNSW indexing,
 * the pronoun <em>"it"</em> produces a meaningless embedding. This stage resolves such
 * references by asking the LLM to inline all context:
 * <em>"Does PostgreSQL support HNSW indexing?"</em>
 *
 * <p>The rolling summary (if present) is included in the rewrite prompt so references to
 * topics from compressed older turns are resolved correctly.
 *
 * <p>If there is no conversation context (fresh session) the original question is used
 * as-is with no LLM call. On LLM failure the original question is used as a fallback.
 *
 * <p><strong>Context:</strong> writes {@link RagPipelineContext#setContextualizedQuestion}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContextualizationStage implements RagPipelineStage {

    private final ChatLanguageModel chatLanguageModel;
    private final EmbeddingProperties properties;

    @Override
    public void process(RagPipelineContext ctx) {
        ConversationContext conversationContext = ctx.getConversationContext();

        if (conversationContext.recentTurns().isEmpty() && !conversationContext.hasSummary()) {
            ctx.setContextualizedQuestion(ctx.getOriginalQuestion());
            return;
        }

        try {
            StringBuilder prompt = new StringBuilder(properties.getContextualizationPrompt());
            if (conversationContext.hasSummary()) {
                prompt.append("Summary of earlier conversation:\n")
                      .append(conversationContext.summary())
                      .append("\n\n");
            }
            conversationContext.recentTurns().forEach(t ->
                    prompt.append(t.role()).append(": ").append(t.content()).append("\n"));
            prompt.append("\nFollow-up: ").append(ctx.getOriginalQuestion());

            String rewritten = chatLanguageModel.generate(UserMessage.from(prompt.toString()))
                                                .content().text().strip();
            log.debug("Question contextualized: [{}] → [{}]", ctx.getOriginalQuestion(), rewritten);
            ctx.setContextualizedQuestion(rewritten);
        } catch (Exception e) {
            log.warn("Contextualization failed, using original question: {}", e.getMessage());
            ctx.setContextualizedQuestion(ctx.getOriginalQuestion());
        }
    }
}
