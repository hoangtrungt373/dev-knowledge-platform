package com.ttg.devknowledgeplatform.ai.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the embedding and chat language models.
 *
 * <p>Bound from the {@code app.ai.model} prefix. Override via environment variables:
 * {@code OPENAI_API_KEY}, {@code EMBEDDING_MODEL}, {@code EMBEDDING_DIMENSIONS},
 * {@code CHAT_MODEL}, {@code CHAT_MAX_TOKENS}, {@code CHAT_TEMPERATURE}, {@code AI_MAX_RETRIES}.
 */
@ConfigurationProperties(prefix = "app.ai.model")
@Validated
@Getter
@Setter
public class ModelConfig {

    @NotBlank
    private String apiKey;

    /** Embedding model name used for both indexing and query embedding. */
    @NotBlank
    private String model = "text-embedding-3-small";

    /** Vector dimensions produced by {@link #model}. Must match the pgvector column size. */
    @Positive
    private int dimensions = 1536;

    @NotBlank
    private String chatModel = "gpt-5.4-mini";

    @Positive
    private int maxTokens = 1024;

    private double temperature = 0.7;

    /**
     * Maximum number of retries for failed LLM API calls.
     * Not applied to streaming calls — retrying mid-stream is not meaningful
     * because partial token output cannot be rolled back.
     */
    @Positive
    private int maxRetries = 3;
}
