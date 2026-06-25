package com.ttg.devknowledgeplatform.ai.dto;

import com.ttg.devknowledgeplatform.ai.entity.ContentEmbedding;
import com.ttg.devknowledgeplatform.ai.pipeline.RagPipelineRunner;
import com.ttg.devknowledgeplatform.ai.pipeline.RagPipelineStage;
import com.ttg.devknowledgeplatform.common.dto.ConversationContext;
import dev.langchain4j.data.message.ChatMessage;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Mutable context carrier that flows through the RAG pipeline stages.
 *
 * <p>Each {@link RagPipelineStage} reads its required inputs from this object and writes
 * its output back to it. The three constructor parameters are immutable per-request inputs;
 * all other fields start as {@code null} and are populated progressively as stages execute.
 *
 * <p>If any stage determines that the pipeline cannot produce a meaningful answer
 * (e.g. no relevant chunks found), it calls {@link #abort(String)} with a user-facing message.
 * Subsequent stages are skipped and the abort reason is returned to the caller.
 */
@Getter
@Setter
public class RagPipelineContext {

    /** User-facing message returned when no relevant context exists in the knowledge base. */
    public static final String NO_CONTEXT_ANSWER =
            "I don't have relevant information in my knowledge base to answer this question.";

    // -------------------------------------------------------------------------
    // Immutable request inputs — set only via constructor
    // -------------------------------------------------------------------------

    private final String originalQuestion;
    private final ConversationContext conversationContext;
    private final RagFilter filter;

    // -------------------------------------------------------------------------
    // Stage outputs — populated progressively
    // -------------------------------------------------------------------------

    /** Rewritten standalone question from {@code ContextualizationStage}. Used for vector embedding. */
    private String contextualizedQuestion;

    /**
     * Structured, enriched form of the user question produced by {@code ContextualizationStage}.
     *
     * <p>Contains four labelled lines: CONTEXT / TASK / CONSTRAINTS / OUTPUT_FORMAT.
     * When non-null, {@code MessageBuildingStage} uses this instead of {@link #originalQuestion}
     * as the final user message so the LLM receives a fully scoped, format-directed query.
     */
    private String enrichedQuestion;

    /** Dense vector from {@code EmbeddingStage}. */
    private float[] queryEmbedding;

    /** Raw candidate chunks from {@code RetrievalStage} (pre-scoring). */
    private List<ContentEmbedding> candidates;

    /** Filtered, scored, threshold-pruned chunks from {@code ScoringStage} (desc order). */
    private List<ScoredChunk> scoredChunks;

    /** Final topK chunks chosen by {@code MmrStage}. */
    private List<ScoredChunk> selectedChunks;

    /** Source citations built by {@code MessageBuildingStage} — sent to the client. */
    private List<RagSource> sources;

    /** Fully assembled LLM message list from {@code MessageBuildingStage}. */
    private List<ChatMessage> messages;

    // -------------------------------------------------------------------------
    // Abort state
    // -------------------------------------------------------------------------

    private boolean aborted;
    private String abortReason;

    // -------------------------------------------------------------------------
    // Constructor + abort helper
    // -------------------------------------------------------------------------

    /**
     * Creates a fresh pipeline context for a single request.
     *
     * @param originalQuestion   raw question from the user
     * @param conversationContext rolling summary + recent verbatim turns
     * @param filter             optional retrieval filter dimensions
     */
    public RagPipelineContext(String originalQuestion,
                              ConversationContext conversationContext,
                              RagFilter filter) {
        this.originalQuestion = originalQuestion;
        this.conversationContext = conversationContext;
        this.filter = filter;
    }

    /**
     * Signals that the pipeline cannot continue and sets the user-facing abort message.
     *
     * <p>After this call, {@link #isAborted()} returns {@code true} and
     * {@link RagPipelineRunner} will skip all remaining stages.
     *
     * @param reason user-facing explanation; typically {@link #NO_CONTEXT_ANSWER}
     */
    public void abort(String reason) {
        this.aborted = true;
        this.abortReason = reason;
    }
}
