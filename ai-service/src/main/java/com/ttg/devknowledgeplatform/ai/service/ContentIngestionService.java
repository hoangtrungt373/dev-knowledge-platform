package com.ttg.devknowledgeplatform.ai.service;

import com.ttg.devknowledgeplatform.ai.dto.ContentEmbeddingMetadata;
import com.ttg.devknowledgeplatform.content.entity.ContentItem;

/**
 * Chunks and embeds content text, persisting vectors to {@code CONTENT_EMBEDDING}.
 *
 * <p>The caller is responsible for extracting the full text from the type-specific
 * entity (QuestionAnswer, Article, etc.) before calling {@link #ingest}.
 * This keeps ai-service free of api-module repositories.
 *
 * <p>The caller is also responsible for constructing the {@link ContentEmbeddingMetadata}
 * that will be stored on every chunk — keeping the metadata schema contract in one place
 * rather than split between modules.
 */
public interface ContentIngestionService {

    /**
     * Replaces all embeddings for {@code contentItem} under the active model,
     * then re-chunks and re-embeds {@code fullText}.
     *
     * @param contentItem the source content item (used for FK, type, and source type)
     * @param fullText    the assembled text to embed (title + body + answers, etc.)
     * @param metadata    fully populated metadata DTO stored verbatim on every chunk's JSONB column
     */
    void ingest(ContentItem contentItem, String fullText, ContentEmbeddingMetadata metadata);

    /** Removes all embeddings for the given content item across all models. */
    void deleteEmbeddings(Integer contentItemId);
}
