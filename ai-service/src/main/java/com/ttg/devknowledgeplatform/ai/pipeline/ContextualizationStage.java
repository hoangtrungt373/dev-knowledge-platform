package com.ttg.devknowledgeplatform.ai.pipeline;

import com.ttg.devknowledgeplatform.ai.config.LabelsConfig;
import com.ttg.devknowledgeplatform.ai.config.LoadedPrompts;
import com.ttg.devknowledgeplatform.ai.dto.RagPipelineContext;
import com.ttg.devknowledgeplatform.ai.service.ChatModelResolver;
import com.ttg.devknowledgeplatform.common.dto.ConversationContext;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pipeline stage that resolves ambiguous references and enriches the raw user question
 * into a structured four-part form following the <em>Context + Task + Constraints + Output Format</em>
 * prompt-engineering pattern.
 *
 * <h3>Why enrichment matters</h3>
 * <p>A raw question like <em>"Does it support it?"</em> produces a low-quality embedding and gives
 * the generation LLM no scope or format guidance. This stage uses a single LLM call to produce
 * a five-labelled block:
 * <pre>{@code
 * STANDALONE:    Does PostgreSQL support HNSW indexing for ANN search?
 * CONTEXT:       Developer studying vector search in a RAG pipeline using pgvector.
 * TASK:          Understand whether PostgreSQL natively supports HNSW and how it performs.
 * CONSTRAINTS:   Intermediate level; focus on pgvector usage; include index creation syntax.
 * OUTPUT_FORMAT: Short answer + code example showing index creation; Markdown.
 * }</pre>
 * <p>The STANDALONE line becomes {@link RagPipelineContext#setContextualizedQuestion} — a clean,
 * self-contained sentence that is embedded for cosine similarity search. The remaining four
 * lines form {@link RagPipelineContext#setEnrichedQuestion} and are passed to the generation
 * LLM as the user message, giving it explicit scope, depth, and output format instructions.
 *
 * <h3>Fallback behaviour</h3>
 * <p>If the LLM call fails or the response cannot be parsed, the original question is used
 * for both fields ({@code enrichedQuestion} is left null). {@code MessageBuildingStage} is
 * aware of this and falls back gracefully to {@code originalQuestion}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContextualizationStage implements RagPipelineStage {

    private static final String STANDALONE_KEY    = "STANDALONE";
    private static final String CONTEXT_KEY       = "CONTEXT";
    private static final String TASK_KEY          = "TASK";
    private static final String CONSTRAINTS_KEY   = "CONSTRAINTS";
    private static final String OUTPUT_FORMAT_KEY = "OUTPUT_FORMAT";

    /**
     * Resolved to the server's default chat model ({@code resolveBlocking(null)}), not the
     * model the caller selected via {@code ChatRequest.chatModel}. Question rewriting is an
     * internal pipeline utility call, not part of the user-facing answer generation the
     * per-request model choice is meant to control — keeping it on one fixed model avoids
     * mixing providers within a single request and avoids threading model choice through
     * every pipeline stage that happens to make an LLM call.
     */
    private final ChatModelResolver chatModelResolver;
    private final LoadedPrompts prompts;
    private final LabelsConfig labels;

    @Override
    public void process(RagPipelineContext ctx) {
        try {
            String prompt = buildPrompt(ctx);
            Response<AiMessage> response = chatModelResolver.resolveBlocking(null).generate(UserMessage.from(prompt));
            recordTokenUsage(ctx, response.tokenUsage());

            EnrichedQuery parsed = parseResponse(response.content().text().strip());
            if (parsed != null) {
                log.debug("Contextualized: [{}] → [{}]", ctx.getOriginalQuestion(), parsed.standalone());
                ctx.setContextualizedQuestion(parsed.standalone());
                ctx.setEnrichedQuestion(parsed.enriched());
            } else {
                log.warn("Could not parse enrichment response — falling back to original question");
                ctx.setContextualizedQuestion(ctx.getOriginalQuestion());
            }
        } catch (Exception e) {
            log.warn("Contextualization/enrichment failed, using original question: {}", e.getMessage());
            ctx.setContextualizedQuestion(ctx.getOriginalQuestion());
        }
    }

    /**
     * Copies input/output token counts from the LangChain4j {@link TokenUsage} onto the
     * pipeline context. All counts are nullable in the LangChain4j API — defensive null
     * checks prevent any monitoring failure from propagating into the main pipeline flow.
     */
    private void recordTokenUsage(RagPipelineContext ctx, TokenUsage usage) {
        if (usage == null) return;
        if (usage.inputTokenCount() != null)  ctx.setContextualizationInputTokens(usage.inputTokenCount());
        if (usage.outputTokenCount() != null) ctx.setContextualizationOutputTokens(usage.outputTokenCount());
    }

    /**
     * Assembles the enrichment prompt, optionally injecting the rolling summary and recent
     * conversation turns before the current question.
     */
    private String buildPrompt(RagPipelineContext ctx) {
        ConversationContext conversationContext = ctx.getConversationContext();
        StringBuilder prompt = new StringBuilder(prompts.inputEnrichment());

        if (conversationContext.hasSummary()) {
            prompt.append(labels.getContextSummaryLabel())
                  .append(conversationContext.summary())
                  .append("\n\n");
        }
        conversationContext.recentTurns().forEach(t ->
                prompt.append(t.role()).append(": ").append(t.content()).append("\n"));
        prompt.append(labels.getContextFollowUpLabel()).append(ctx.getOriginalQuestion());

        return prompt.toString();
    }

    /**
     * Parses the LLM response into an {@link EnrichedQuery}.
     *
     * <p>Each line is expected to start with one of the five keys followed by a colon.
     * Lines that do not match any key are ignored. Returns {@code null} if the STANDALONE
     * value is absent or blank (indicating a malformed response).
     *
     * @param response raw LLM text output
     * @return parsed result, or {@code null} on parse failure
     */
    private EnrichedQuery parseResponse(String response) {
        Map<String, String> sections = new LinkedHashMap<>();
        for (String line : response.split("\n")) {
            line = line.strip();
            int colonIdx = line.indexOf(':');
            if (colonIdx > 0) {
                String key   = line.substring(0, colonIdx).strip().toUpperCase().replace(" ", "_");
                String value = line.substring(colonIdx + 1).strip();
                sections.putIfAbsent(key, value);
            }
        }

        String standalone = sections.get(STANDALONE_KEY);
        if (standalone == null || standalone.isBlank()) {
            return null;
        }

        StringBuilder enriched = new StringBuilder();
        appendSection(enriched, CONTEXT_KEY,       "CONTEXT",       sections);
        appendSection(enriched, TASK_KEY,          "TASK",          sections);
        appendSection(enriched, CONSTRAINTS_KEY,   "CONSTRAINTS",   sections);
        appendSection(enriched, OUTPUT_FORMAT_KEY, "OUTPUT FORMAT", sections);

        return new EnrichedQuery(standalone, enriched.toString().strip());
    }

    private void appendSection(StringBuilder sb, String mapKey, String label, Map<String, String> sections) {
        String value = sections.get(mapKey);
        if (value != null && !value.isBlank()) {
            if (!sb.isEmpty()) sb.append("\n");
            sb.append(label).append(": ").append(value);
        }
    }

    /**
     * Holds the two outputs produced by one LLM enrichment call:
     * the clean standalone question (for embedding) and the structured enriched prompt
     * (for LLM generation).
     *
     * <p>Java 21 record — an immutable data carrier with auto-generated constructor,
     * accessors, {@code equals}, {@code hashCode}, and {@code toString}.
     */
    private record EnrichedQuery(String standalone, String enriched) {}
}
