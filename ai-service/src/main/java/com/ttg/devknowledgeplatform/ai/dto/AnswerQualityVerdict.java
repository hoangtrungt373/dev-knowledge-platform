package com.ttg.devknowledgeplatform.ai.dto;

/**
 * Result of a post-generation answer quality assessment (Case 6 — answer drift detection).
 *
 * <p>Carries two independent similarity scores:
 * <ul>
 *   <li>{@link #contextSimilarity} — cosine similarity between the generated answer and the
 *       centroid of the retrieved chunks injected into the LLM context. Measures whether the
 *       answer is grounded in the retrieved material.</li>
 *   <li>{@link #querySimilarity} — cosine similarity between the generated answer and the
 *       query embedding. Measures whether the answer addresses the user's actual question.</li>
 * </ul>
 *
 * <p>A sentinel value of {@code -1f} on both scores indicates the check was skipped
 * (cold start, no retrieved chunks, or no query embedding available). Use
 * {@link #wasSkipped()} to distinguish this from a real low-score result.
 *
 * <p>Currently used for monitoring only — the verdict is logged at {@code WARN} when drift
 * is detected but does not abort or modify the response. Once threshold values are validated
 * against real traffic, a hard gate or a trailing SSE warning event can be added.
 */
public record AnswerQualityVerdict(boolean drifted, float contextSimilarity, float querySimilarity) {

    /**
     * Returns {@code true} when the check was skipped rather than run.
     * Callers should ignore the {@link #drifted} flag when this is {@code true}.
     */
    public boolean wasSkipped() {
        return contextSimilarity == -1f && querySimilarity == -1f;
    }

    /** Sentinel result used when the check cannot run (no chunks or no query embedding). */
    public static AnswerQualityVerdict skipped() {
        return new AnswerQualityVerdict(false, -1f, -1f);
    }
}
