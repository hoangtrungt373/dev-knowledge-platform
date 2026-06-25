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

    // --- Prompt label strings ---

    /**
     * Label prepended to the rolling summary when building the contextualization rewrite prompt.
     * Placed before the summary text so the LLM understands it is historical context.
     */
    @NotBlank
    private String contextSummaryLabel = "Summary of earlier conversation:\n";

    /**
     * Label prepended to the current user question when building the contextualization rewrite prompt.
     * Signals to the LLM that what follows is the ambiguous follow-up to rewrite.
     */
    @NotBlank
    private String contextFollowUpLabel = "\nFollow-up: ";

    /**
     * Label used as the user message that injects the rolling summary into the LLM message list.
     * Placed before the summary text so the model treats it as prior conversation context.
     */
    @NotBlank
    private String historySummaryLabel = "Earlier conversation summary:\n";

    /**
     * Synthetic AI acknowledgement message that follows the injected summary in the message list.
     * Closes the synthetic User/Assistant exchange used to inject compressed history.
     */
    @NotBlank
    private String historySummaryAck = "Understood. I will keep this context in mind while answering.";

    /**
     * Label prepended to the previous summary text in the compression prompt.
     * Instructs the LLM to extend rather than rewrite the existing summary.
     */
    @NotBlank
    private String compressionPreviousSummaryLabel = "\n\nPrevious summary (extend this, do not repeat it verbatim):\n";

    /**
     * Label that separates the previous summary from the new turns in the compression prompt.
     * Signals to the LLM where the new content to compress begins.
     */
    @NotBlank
    private String compressionTurnsLabel = "\n\nConversation turns to compress:\n";

    // --- System prompts ---

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

    /**
     * Prompt sent to the LLM to both resolve pronoun references and enrich the raw user question
     * into a structured four-part form (CONTEXT / TASK / CONSTRAINTS / OUTPUT_FORMAT).
     * The LLM response must contain five labelled lines (STANDALONE, CONTEXT, TASK,
     * CONSTRAINTS, OUTPUT_FORMAT); see {@code ContextualizationStage} for the parse logic.
     */
    @NotBlank
    private String inputEnrichmentPrompt;

    /**
     * Prompt prefix sent to the LLM when compressing old conversation turns into a rolling summary.
     * The implementation appends the previous summary (if any) and the turns to compress after this prefix.
     */
    @NotBlank
    private String summarisationPrompt;
}
