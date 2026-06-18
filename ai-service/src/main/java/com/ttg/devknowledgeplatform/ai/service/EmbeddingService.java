package com.ttg.devknowledgeplatform.ai.service;

import java.util.List;

/**
 * Converts text into dense vector embeddings using a remote embedding model.
 */
public interface EmbeddingService {

    /** Embeds a single text. Prefer {@link #embedBatch} when processing multiple texts. */
    float[] embed(String text);

    /**
     * Embeds multiple texts in a single API call.
     * Returned list preserves input order.
     */
    List<float[]> embedBatch(List<String> texts);
}
