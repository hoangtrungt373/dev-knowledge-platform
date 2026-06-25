package com.ttg.devknowledgeplatform.ai.pipeline;

import com.ttg.devknowledgeplatform.ai.config.EmbeddingProperties;
import com.ttg.devknowledgeplatform.ai.dto.RagFilter;
import com.ttg.devknowledgeplatform.ai.dto.RagPipelineContext;
import com.ttg.devknowledgeplatform.ai.dto.RagSource;
import com.ttg.devknowledgeplatform.ai.dto.ScoredChunk;
import com.ttg.devknowledgeplatform.common.enums.ContentType;
import com.ttg.devknowledgeplatform.common.dto.ConversationContext;
import com.ttg.devknowledgeplatform.common.dto.ConversationTurn;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Pipeline stage that assembles the final LLM input from the selected chunks and conversation context.
 *
 * <p>Two outputs are produced:
 * <ul>
 *   <li><strong>Sources</strong> — a {@link RagSource} list sent to the client as citations
 *       before the LLM generates its response.</li>
 *   <li><strong>Messages</strong> — the full {@link ChatMessage} list passed to the LLM,
 *       structured as:
 *       <ol>
 *         <li>System prompt with numbered context chunks.</li>
 *         <li>If a rolling summary exists: a synthetic User/Assistant pair that injects it as
 *             the oldest context layer. This is the standard technique for inserting compressed
 *             history into a conversational LLM's context window.</li>
 *         <li>Recent verbatim turns (oldest first) as alternating User/Assistant messages.</li>
 *         <li>The current user question as the final User message.</li>
 *       </ol>
 *   </li>
 * </ul>
 *
 * <p>The system prompt prefix is resolved from {@link RagPipelineContext#getFilter()}: when the
 * filter targets exactly one {@link com.ttg.devknowledgeplatform.common.enums.ContentType} a
 * domain-specific prompt is used; mixed or unfiltered queries fall back to the default prompt.
 *
 * <p><strong>Reads:</strong> {@link RagPipelineContext#getSelectedChunks()},
 * {@link RagPipelineContext#getConversationContext()},
 * {@link RagPipelineContext#getOriginalQuestion()},
 * {@link RagPipelineContext#getFilter()}.<br>
 * <strong>Writes:</strong> {@link RagPipelineContext#setSources(List)},
 * {@link RagPipelineContext#setMessages(List)}.
 */
@Component
@RequiredArgsConstructor
public class MessageBuildingStage implements RagPipelineStage {

    private final EmbeddingProperties properties;

    @Override
    public void process(RagPipelineContext ctx) {
        List<ScoredChunk> selected = ctx.getSelectedChunks();

        ctx.setSources(buildSources(selected));
        ctx.setMessages(buildMessages(selected, ctx.getConversationContext(), ctx.getOriginalQuestion(), ctx.getFilter()));
    }

    private List<RagSource> buildSources(List<ScoredChunk> selected) {
        return selected.stream()
                .map(sc -> new RagSource(
                        sc.chunk().getContentItem().getId(),
                        sc.chunk().getSourceType().name(),
                        sc.chunk().getContentItem().getTitle(),
                        sc.chunk().getChunkText(),
                        sc.score()))
                .toList();
    }

    private List<ChatMessage> buildMessages(List<ScoredChunk> selected,
                                            ConversationContext context,
                                            String originalQuestion,
                                            RagFilter filter) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(buildSystemPrompt(selected, filter)));

        if (context.hasSummary()) {
            messages.add(UserMessage.from(properties.getHistorySummaryLabel() + context.summary()));
            messages.add(AiMessage.from(properties.getHistorySummaryAck()));
        }

        for (ConversationTurn turn : context.recentTurns()) {
            if ("USER".equals(turn.role())) {
                messages.add(UserMessage.from(turn.content()));
            } else {
                messages.add(AiMessage.from(turn.content()));
            }
        }
        messages.add(UserMessage.from(originalQuestion));
        return messages;
    }

    private String buildSystemPrompt(List<ScoredChunk> selected, RagFilter filter) {
        String contextBlock = IntStream.range(0, selected.size())
                .mapToObj(i -> "[" + (i + 1) + "] " + selected.get(i).chunk().getChunkText())
                .collect(Collectors.joining("\n\n"));
        return resolvePromptPrefix(filter) + contextBlock;
    }

    /**
     * Picks the system prompt prefix based on the active filter's content type.
     * Falls back to the default prompt for mixed-type or unfiltered queries.
     */
    private String resolvePromptPrefix(RagFilter filter) {
        if (filter.sourceTypes() == null || filter.sourceTypes().size() != 1) {
            return properties.getSystemPrompt();
        }
        return switch (filter.sourceTypes().iterator().next()) {
            case ARTICLE -> properties.getSystemPromptArticle();
            case INTERVIEW_QUESTION -> properties.getSystemPromptInterviewQuestion();
            case BLOG_POST -> properties.getSystemPromptBlogPost();
        };
    }
}
