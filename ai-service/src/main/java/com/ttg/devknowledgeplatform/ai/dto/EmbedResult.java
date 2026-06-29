package com.ttg.devknowledgeplatform.ai.dto;

/**
 * Return value of {@link com.ttg.devknowledgeplatform.ai.service.EmbeddingService#embed}.
 *
 * <p>Pairs the dense vector with the token count reported by the embedding model so that
 * callers can record API usage for cost monitoring without making a second API call.
 *
 * <p>Java 21 record — immutable, value-based semantics. {@code tokenCount} is 0 when the
 * model does not report usage (e.g. mock/stub implementations used in tests).
 *
 * @param vector     dense embedding vector produced by the model
 * @param tokenCount number of input tokens consumed by this embedding call; 0 if unknown
 */
public record EmbedResult(float[] vector, int tokenCount) {}
