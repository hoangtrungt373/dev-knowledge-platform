package com.ttg.devknowledgeplatform.ai.service;

import java.util.List;

/**
 * Splits a long text into overlapping chunks suitable for embedding.
 * Chunk size and overlap are controlled by {@code app.ai.indexing.chunk-size}
 * and {@code app.ai.indexing.chunk-overlap}.
 */
public interface TextChunkingService {

    /**
     * Splits {@code text} into overlapping chunks.
     * Returns a single-element list when the text fits within one chunk,
     * or an empty list when the text is blank.
     */
    List<String> chunk(String text);

    /** Approximates the token count for a text (1 token ≈ 4 characters). */
    int estimateTokens(String text);
}
