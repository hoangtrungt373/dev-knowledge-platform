package com.ttg.devknowledgeplatform.ai.dto;

import com.ttg.devknowledgeplatform.ai.entity.ContentEmbedding;
import com.ttg.devknowledgeplatform.ai.pipeline.RagPipelineRunner;
import com.ttg.devknowledgeplatform.ai.pipeline.RagPipelineStage;
import com.ttg.devknowledgeplatform.ai.dto.ConversationContext;
import dev.langchain4j.data.message.ChatMessage;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    // Trace state — generated once per context, immutable
    // -------------------------------------------------------------------------

    /**
     * Unique identifier for this pipeline execution. Included in every emitted log line so
     * that all stage events and the final {@link StageSpan} list can be correlated across log files.
     */
    private final UUID traceId = UUID.randomUUID();

    /** Absolute epoch-ms at which this context was constructed, used to compute total pipeline latency. */
    private final long pipelineStartMs = System.currentTimeMillis();

    /** Ordered list of spans appended by each stage as it completes. */
    private final List<StageSpan> spans = new ArrayList<>();

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

    /**
     * Per-request similarity threshold override set by {@code QueryAnomalyStage} on soft-anomaly
     * detection. When non-null, {@code ScoringStage} uses this value instead of the configured
     * default, requiring retrieved chunks to score higher before passing into the LLM context.
     */
    private Float effectiveSimilarityThreshold;

    /**
     * Arithmetic mean cosine similarity of MMR-selected chunks, set by {@code EvidenceQualityStage}
     * after calling {@code computeMean()}. Stored here so {@link PipelineMetricsRecorder} can
     * persist it without re-computing. {@code null} if the pipeline aborted before that stage.
     */
    private Float evidenceMeanScore;

    // -------------------------------------------------------------------------
    // Cost & latency monitoring (Features 1–3)
    // -------------------------------------------------------------------------

    /** User ID from the authenticated session; {@code null} for anonymous or internal calls. */
    private Integer userId;

    /**
     * Id of the chat model profile actually resolved for this request (see
     * {@code ChatModelResolver}) — never {@code null} once set, since resolution defaults to
     * the configured default model rather than leaving this unset. Recorded before generation
     * runs so it is available for cost attribution even if the pipeline aborts beforehand.
     */
    private String resolvedChatModel;

    /**
     * Wall-clock time in milliseconds for the final LLM generation call.
     * Set by {@link com.ttg.devknowledgeplatform.ai.service.impl.RagQueryServiceImpl} after
     * the model responds; 0 when the pipeline was aborted before generation.
     */
    private long llmGenerationMs;

    /** Input token count from the {@code ContextualizationStage} LLM call; 0 if the stage was skipped or failed. */
    private int contextualizationInputTokens;

    /** Output token count from the {@code ContextualizationStage} LLM call; 0 if the stage was skipped or failed. */
    private int contextualizationOutputTokens;

    /** Token count from the {@code EmbeddingStage} query embedding call; 0 if unavailable. */
    private int embeddingTokens;

    /** Token count from the {@code AnswerQualityService} answer embedding call; 0 if the check was skipped. */
    private int qualityEmbeddingTokens;

    /** Input (prompt) token count from the final LLM generation call; 0 when pipeline aborted. */
    private int generationInputTokens;

    /** Output (completion) token count from the final LLM generation call; 0 when pipeline aborted. */
    private int generationOutputTokens;

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

    // -------------------------------------------------------------------------
    // Trace helpers — called by pipeline infrastructure, not by stage logic
    // -------------------------------------------------------------------------

    /**
     * Appends a completed-stage span to the trace. Called exclusively by
     * {@link RagPipelineStage#execute(RagPipelineContext)} — stage implementations
     * should not call this directly.
     *
     * @param stage      stage name (from {@link RagPipelineStage#name()})
     * @param durationMs wall-clock execution time measured by the runner
     * @param aborted    whether this stage triggered an abort
     */
    public void recordSpan(String stage, long durationMs, boolean aborted) {
        spans.add(new StageSpan(stage, durationMs, aborted));
    }

    /**
     * Wall-clock milliseconds elapsed since this context was constructed.
     *
     * @return total pipeline elapsed time in milliseconds
     */
    public long elapsedMs() {
        return System.currentTimeMillis() - pipelineStartMs;
    }
}
