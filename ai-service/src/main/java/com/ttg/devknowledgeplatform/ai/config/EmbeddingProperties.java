package com.ttg.devknowledgeplatform.ai.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.ttg.devknowledgeplatform.ai.dto.RagFilter;

@ConfigurationProperties(prefix = "app.ai.embedding")
@Validated
@Getter
@Setter
public class EmbeddingProperties {

    @NotBlank
    private String apiKey;

    @NotBlank
    private String model = "text-embedding-3-small";

    @Positive
    private int dimensions = 1536;

    /** Target token count per chunk. 1 token ≈ 4 characters. */
    @Positive
    private int chunkSize = 512;

    /** Token overlap between consecutive chunks to preserve context at boundaries. */
    @Positive
    private int chunkOverlap = 100;

    // --- Chat / generation ---

    @NotBlank
    private String chatModel = "gpt-4o-mini";

    @Positive
    private int maxTokens = 1024;

    private double temperature = 0.7;

    @Positive
    private int maxRetries = 3;

    // --- RAG ---

    @Positive
    private int topK = 5;

    @DecimalMin("0.0") @DecimalMax("1.0")
    private float similarityThreshold = 0.75f;

    /**
     * Multiplier applied to {@code topK} when a {@link RagFilter}
     * is active. Fetching more candidates than needed ensures the post-filter pool remains large
     * enough to yield {@code topK} results after filtering, compensating for HNSW's inability
     * to perform efficient filtered approximate nearest-neighbour search.
     */
    @Positive
    private int oversampleFactor = 3;

    /**
     * Lambda (λ) for Maximal Marginal Relevance re-ranking.
     *
     * <p>Controls the relevance/diversity trade-off when selecting the final {@code topK} chunks:
     * <ul>
     *   <li>{@code 1.0} — pure relevance; equivalent to sorting by cosine similarity (no diversity)</li>
     *   <li>{@code 0.5} — equal weight between relevance and diversity (default)</li>
     *   <li>{@code 0.0} — pure diversity; ignores relevance scores entirely</li>
     * </ul>
     */
    @DecimalMin("0.0") @DecimalMax("1.0")
    private float mmrLambda = 0.5f;

    @NotBlank
    private String systemPrompt;

    /** System prompt prefix applied when the query is scoped exclusively to {@code ARTICLE} content. */
    @NotBlank
    private String systemPromptArticle;

    /** System prompt prefix applied when the query is scoped exclusively to {@code INTERVIEW_QUESTION} content. */
    @NotBlank
    private String systemPromptInterviewQuestion;

    /** System prompt prefix applied when the query is scoped exclusively to {@code BLOG_POST} content. */
    @NotBlank
    private String systemPromptBlogPost;

    /** Prompt prefix used to rewrite ambiguous follow-up questions into standalone queries. */
    @NotBlank
    private String contextualizationPrompt;

    /**
     * Prompt prefix sent to the LLM when compressing old conversation turns into a rolling summary.
     * The implementation appends the previous summary (if any) and the turns to compress after this prefix.
     */
    @NotBlank
    private String summarisationPrompt;
}
