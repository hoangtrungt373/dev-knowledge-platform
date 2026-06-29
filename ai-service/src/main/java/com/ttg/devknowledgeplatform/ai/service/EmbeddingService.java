package com.ttg.devknowledgeplatform.ai.service;

import com.ttg.devknowledgeplatform.ai.dto.EmbedResult;

import java.util.List;

/**
 * Converts text into dense vector embeddings using a remote embedding model.
 */
public interface EmbeddingService {

    /**
     * Embeds a single text and returns both the vector and the token count consumed.
     *
     * <p>Token count is 0 when the underlying model does not report usage metadata
     * (e.g. test doubles). Prefer {@link #embedBatch} when processing multiple texts.
     *
     * @param text the text to embed; must not be blank
     * @return {@link EmbedResult} containing the dense vector and the input token count
     */
    EmbedResult embed(String text);

    /**
     * Embeds multiple texts in a single API call.
     * Returned list preserves input order.
     */
    List<float[]> embedBatch(List<String> texts);
}
