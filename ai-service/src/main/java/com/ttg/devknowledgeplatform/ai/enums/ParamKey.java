package com.ttg.devknowledgeplatform.ai.enums;

/**
 * Named keys for system-managed parameters persisted in the {@code SYS_PARAM} table.
 *
 * <p>Each constant maps to exactly one row identified by {@code NAME = constant.name()}.
 * Three serialization formats are used depending on the parameter type:
 * <ul>
 *   <li><strong>Vector values</strong> (CENTROID_* keys) — pgvector text notation:
 *       {@code [f1,f2,...,f1536]}</li>
 *   <li><strong>Numeric values</strong> (threshold keys) — plain decimal string:
 *       {@code "0.45"}</li>
 *   <li><strong>Fingerprinted vector-list values</strong> ({@code PROMPT_INJECTION_PROTOTYPE_EMBEDDINGS})
 *       — first line is a hex-encoded SHA-256 fingerprint of the embedding model id and prototype
 *       text that produced the vectors; each subsequent line is one pgvector-notation vector.
 *       The fingerprint lets the reader detect a stale cache (prototype list or embedding model
 *       changed) and recompute rather than trusting the stored vectors blindly.</li>
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

    /** Average embedding vector of all {@code QUESTION_ANSWER} content embeddings. */
    CENTROID_QUESTION_ANSWER,

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
    ANOMALY_SOFT_THRESHOLD,

    /**
     * Cached embeddings of {@code app.ai.guards.injection-detection.prototypes}, computed once
     * by {@code PromptGuardStage} and reused across restarts until the prototype list or
     * embedding model changes (detected via the stored fingerprint — see the class Javadoc).
     */
    PROMPT_INJECTION_PROTOTYPE_EMBEDDINGS
}
