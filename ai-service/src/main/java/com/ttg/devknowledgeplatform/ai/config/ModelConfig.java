package com.ttg.devknowledgeplatform.ai.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the embedding model.
 *
 * <p>Bound from the {@code app.ai.embedding-model} prefix. Override via environment variables:
 * {@code OPENAI_API_KEY}, {@code EMBEDDING_MODEL}, {@code EMBEDDING_DIMENSIONS}.
 *
 * <p>Chat model settings live separately in {@code ChatModelsConfig} — this class used to hold
 * both, but embeddings and chat generation can now be served by different providers (only OpenAI
 * offers an embedding product; chat generation can run on OpenAI or Anthropic), so a single
 * {@code apiKey} could no longer represent "the" provider for both concerns.
 */
@ConfigurationProperties(prefix = "app.ai.embedding-model")
@Validated
@Getter
@Setter
public class ModelConfig {

    /** OpenAI API key — the only supported embedding provider. */
    @NotBlank
    private String apiKey;

    /** Embedding model name used for both indexing and query embedding. */
    @NotBlank
    private String model = "text-embedding-3-small";

    /** Vector dimensions produced by {@link #model}. Must match the pgvector column size. */
    @Positive
    private int dimensions = 1536;

    /** Maximum number of retries for failed embedding API calls. */
    @Positive
    private int maxRetries = 3;
}
