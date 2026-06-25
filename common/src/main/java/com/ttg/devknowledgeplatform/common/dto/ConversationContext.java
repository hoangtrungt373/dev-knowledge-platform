package com.ttg.devknowledgeplatform.common.dto;

import java.util.List;

/**
 * Full conversation context injected into the RAG pipeline for a single request.
 *
 * <p>Combines two complementary views of the session history:
 * <ul>
 *   <li>{@code summary} — an LLM-generated rolling summary that compresses older turns into
 *       a single paragraph, keeping older context available without inflating the token budget.</li>
 *   <li>{@code recentTurns} — the last N Q&amp;A pairs kept verbatim so the model can resolve
 *       pronouns and follow-up references in near-context.</li>
 * </ul>
 *
 * <p>When a session is new or below the summarisation threshold, {@code summary} is {@code null}
 * and only the verbatim turns are used.
 *
 * @param summary      rolling LLM summary of turns older than the recent verbatim window;
 *                     {@code null} if the session has not yet been summarised
 * @param recentTurns  most recent Q&amp;A pairs, ordered oldest-first
 */
public record ConversationContext(String summary, List<ConversationTurn> recentTurns) {

    /**
     * Returns {@code true} if a non-blank rolling summary is present.
     *
     * @return whether a summary exists for this context
     */
    public boolean hasSummary() {
        return summary != null && !summary.isBlank();
    }

    /**
     * Convenience factory for sessions that do not yet have a summary.
     *
     * @param turns verbatim recent turns
     * @return a context with no summary
     */
    public static ConversationContext withoutSummary(List<ConversationTurn> turns) {
        return new ConversationContext(null, turns);
    }
}
