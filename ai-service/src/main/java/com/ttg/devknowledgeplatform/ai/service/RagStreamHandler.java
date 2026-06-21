package com.ttg.devknowledgeplatform.ai.service;

import com.ttg.devknowledgeplatform.ai.dto.RagSource;

import java.util.List;

/**
 * Callback interface for receiving streaming output from the RAG pipeline.
 *
 * <p>Decouples the AI service layer from HTTP concerns — the service fires these
 * callbacks; the caller (e.g. a Spring MVC controller) decides how to forward
 * each event to the client (SSE, WebSocket, etc.).
 *
 * <p>Callback order per query:
 * <ol>
 *   <li>{@link #onSources} — fired once, immediately after retrieval and before LLM generation,
 *       so the client can display citations while the answer streams in.</li>
 *   <li>{@link #onToken} — fired once per token as the LLM generates the answer.</li>
 *   <li>{@link #onComplete} — fired once when the stream ends normally.</li>
 * </ol>
 * {@link #onError} replaces {@link #onComplete} if the pipeline fails.
 */
public interface RagStreamHandler {

    /**
     * Called once with the knowledge-base chunks used as context, before any tokens arrive.
     *
     * @param sources retrieved chunks ordered by cosine similarity descending
     */
    void onSources(List<RagSource> sources);

    /**
     * Called once per token as the LLM generates the answer.
     *
     * @param token a single text fragment (may be a word, sub-word, or punctuation)
     */
    void onToken(String token);

    /**
     * Called when the LLM has finished generating the full response.
     */
    void onComplete();

    /**
     * Called if the pipeline fails at any stage — embedding, retrieval, or generation.
     * {@link #onComplete} will NOT be called if this is invoked.
     *
     * @param error the failure cause
     */
    void onError(Throwable error);
}
