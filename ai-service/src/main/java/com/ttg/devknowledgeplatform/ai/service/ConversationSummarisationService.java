package com.ttg.devknowledgeplatform.ai.service;

import com.ttg.devknowledgeplatform.ai.dto.ConversationTurn;

import java.util.List;

/**
 * Compresses a list of conversation turns into a rolling plain-text summary.
 *
 * <p>Used by the chat session layer to periodically collapse older turns into a compact
 * paragraph so the total tokens passed to the LLM stay bounded across long sessions.
 *
 * <p>The rolling pattern: when a previous summary exists, it is passed alongside the new
 * (uncompressed) turns; the LLM weaves both into a single updated summary. This avoids
 * re-summarising already-compressed content while keeping the output coherent.
 */
public interface ConversationSummarisationService {

    /**
     * Produces an updated rolling summary from {@code turnsToCompress}.
     *
     * <p>If {@code previousSummary} is non-null, the resulting summary should incorporate
     * the earlier context rather than replacing it — the implementation achieves this by
     * including the previous summary in the prompt.
     *
     * <p>Implementations must be fault-tolerant: on LLM failure the method should return
     * {@code previousSummary} unchanged rather than propagating the exception, so the session
     * continues to function without a fresh summary.
     *
     * @param previousSummary prior rolling summary, or {@code null} for the first compression
     * @param turnsToCompress turns to compress into the new summary (may include turns already
     *                        covered by {@code previousSummary} — the LLM is prompted to handle this)
     * @return updated rolling summary text; never {@code null}
     */
    String summarise(String previousSummary, List<ConversationTurn> turnsToCompress);
}
