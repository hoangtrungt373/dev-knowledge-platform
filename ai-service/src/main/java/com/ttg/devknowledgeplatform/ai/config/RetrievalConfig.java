package com.ttg.devknowledgeplatform.ai.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties controlling vector retrieval and re-ranking behaviour.
 *
 * <p>Bound from the {@code app.ai.retrieval} prefix. Override via environment variables:
 * {@code RAG_TOP_K}, {@code RAG_SIMILARITY_THRESHOLD}, {@code RAG_OVERSAMPLE_FACTOR},
 * {@code RAG_MMR_LAMBDA}, {@code RETRIEVAL_OUTLIER_GAP_THRESHOLD}.
 */
@ConfigurationProperties(prefix = "app.ai.retrieval")
@Validated
@Getter
@Setter
public class RetrievalConfig {

    /** Number of final chunks selected by MMR and passed to the generation LLM. */
    @Positive
    private int topK = 5;

    /**
     * Minimum cosine similarity a chunk must achieve after scoring to enter the MMR pool.
     * Per-chunk absolute floor. {@code QueryAnomalyStage} may raise this per-request to
     * {@link GuardConfig#anomalySoftSimilarityThreshold} for borderline queries.
     */
    @DecimalMin("0.0") @DecimalMax("1.0")
    private float similarityThreshold = 0.75f;

    /**
     * Multiplier applied to {@code topK} on each retrieval call.
     * Fetching {@code topK × oversampleFactor} candidates from the HNSW index ensures
     * the post-filter and post-MMR pools remain large enough after diversity penalisation.
     */
    @Positive
    private int oversampleFactor = 3;

    /**
     * Lambda (λ) for Maximal Marginal Relevance re-ranking (Carbonell &amp; Goldstein, 1998).
     * {@code 1.0} = pure relevance (no diversity), {@code 0.0} = pure diversity, {@code 0.5} = balanced.
     */
    @DecimalMin("0.0") @DecimalMax("1.0")
    private float mmrLambda = 0.5f;

    /**
     * Minimum score gap between consecutive sorted chunks that triggers outlier pruning
     * in {@code RetrievalAnomalyStage}. A gap of {@code 0.15} discards every chunk that
     * scores 15 pp below the preceding chunk. Set to {@code 0.0} to disable pruning.
     */
    @DecimalMin("0.0") @DecimalMax("1.0")
    private float outlierGapThreshold = 0.15f;
}
