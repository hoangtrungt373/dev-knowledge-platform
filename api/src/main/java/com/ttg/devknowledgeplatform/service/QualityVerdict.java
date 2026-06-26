package com.ttg.devknowledgeplatform.service;

/**
 * Result of an {@link IndexingQualityService#assess} call.
 *
 * <p>{@code score} holds the mean cosine similarity between the document's chunk embeddings
 * and the corpus centroid — always in {@code [0, 1]} when a centroid was available.
 * A score of {@code -1} is the sentinel value used when the check was skipped (no centroid
 * cached yet); in that case {@code lowQuality} is always {@code false}.
 */
public record QualityVerdict(boolean lowQuality, float score) {

    /** Document passed the quality threshold. */
    public static QualityVerdict pass(float score) {
        return new QualityVerdict(false, score);
    }

    /** Document scored below the coherence threshold — flagged for review. */
    public static QualityVerdict flag(float score) {
        return new QualityVerdict(true, score);
    }

    /** Check was skipped because no corpus centroid is available yet (cold-start). */
    public static QualityVerdict skipped() {
        return new QualityVerdict(false, -1f);
    }

    /** Returns {@code true} if this verdict represents a skipped check. */
    public boolean wasSkipped() {
        return score == -1f;
    }
}
