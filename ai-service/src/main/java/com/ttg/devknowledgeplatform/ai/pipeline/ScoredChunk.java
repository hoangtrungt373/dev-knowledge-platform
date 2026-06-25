package com.ttg.devknowledgeplatform.ai.pipeline;

import com.ttg.devknowledgeplatform.ai.entity.ContentEmbedding;

/**
 * Pairs a retrieved {@link ContentEmbedding} chunk with its pre-computed cosine similarity
 * score against the current query embedding.
 *
 * <p>Package-private: used only within the {@code ai.pipeline} package by the scoring,
 * deduplication, MMR, and message-building stages.
 */
record ScoredChunk(ContentEmbedding chunk, float score) {}
