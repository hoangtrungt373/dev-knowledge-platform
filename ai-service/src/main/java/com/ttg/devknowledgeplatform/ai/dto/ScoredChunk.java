package com.ttg.devknowledgeplatform.ai.dto;

import com.ttg.devknowledgeplatform.ai.entity.ContentEmbedding;

/**
 * Pairs a retrieved {@link ContentEmbedding} chunk with its pre-computed cosine similarity
 * score against the current query embedding.
 */
public record ScoredChunk(ContentEmbedding chunk, float score) {}
