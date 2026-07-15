package com.ttg.devknowledgeplatform.ai.service;

/**
 * Orchestrates RAG ingestion for content items.
 *
 * <p>Fetches the full text from the type-specific entity (QuestionAnswer, Article),
 * then delegates chunking, embedding, and persistence to {@code ContentIngestionService}.
 */
public interface ContentIndexingService {

    /**
     * Indexes a single published content item by its ID.
     * Replaces any existing embeddings for the active model.
     *
     * @throws jakarta.persistence.EntityNotFoundException if the content item does not exist
     */
    void index(Integer contentItemId);

    /**
     * Indexes all content items currently in PUBLISHED status.
     * Useful for initial bulk ingestion or full re-index after a model change.
     */
    void indexAll();

    /**
     * Removes all embeddings for the content item and re-ingests it.
     * Use when the content body has changed after initial indexing.
     */
    void reindex(Integer contentItemId);

    /** Removes all embeddings for the content item across all models. */
    void deleteIndex(Integer contentItemId);
}
