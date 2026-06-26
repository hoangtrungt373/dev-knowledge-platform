package com.ttg.devknowledgeplatform.common.enums;

/**
 * Named keys for system-managed parameters persisted in the {@code SYS_PARAM} table.
 *
 * <p>Each constant maps to exactly one row identified by {@code NAME = constant.name()}.
 * Two serialization formats are used depending on the parameter type:
 * <ul>
 *   <li><strong>Vector values</strong> (CENTROID_* keys) — pgvector text notation:
 *       {@code [f1,f2,...,f1536]}</li>
 *   <li><strong>Numeric values</strong> (threshold keys) — plain decimal string:
 *       {@code "0.45"}</li>
 * </ul>
 *
 * <p>Keys are persisted as {@code constant.name()} — renaming a constant is a
 * breaking change that requires a data migration on the {@code SYS_PARAM} table.
 */
public enum ParamKey {

    /** Average embedding vector across all {@code ContentEmbedding} rows, regardless of content type. */
    CENTROID_ALL,

    /** Average embedding vector of all {@code ARTICLE} content embeddings. */
    CENTROID_ARTICLE,

    /** Average embedding vector of all {@code INTERVIEW_QUESTION} content embeddings. */
    CENTROID_INTERVIEW_QUESTION,

    /** Average embedding vector of all {@code BLOG_POST} content embeddings. */
    CENTROID_BLOG_POST,

    /**
     * Cosine similarity lower bound for hard anomaly detection.
     * A query whose similarity to the relevant corpus centroid falls below this value
     * triggers an immediate pipeline abort with a domain clarification message.
     */
    ANOMALY_HARD_THRESHOLD,

    /**
     * Cosine similarity lower bound for soft anomaly detection.
     * A query between this value and {@link #ANOMALY_HARD_THRESHOLD} proceeds through
     * the pipeline with a stricter {@code ScoringStage} similarity threshold to reduce
     * retrieval scope to only the most confident chunks.
     */
    ANOMALY_SOFT_THRESHOLD
}
