package com.ttg.devknowledgeplatform.ai.service;

import com.ttg.devknowledgeplatform.common.entity.ContentItem;

import java.util.Map;

/**
 * Chunks and embeds content text, persisting vectors to {@code CONTENT_EMBEDDING}.
 *
 * <p>The caller is responsible for extracting the full text from the type-specific
 * entity (InterviewQuestion, Article, etc.) before calling {@link #ingest}.
 * This keeps ai-service free of api-module repositories.
 */
public interface ContentIngestionService {

    /**
     * Replaces all embeddings for {@code contentItem} under the active model,
     * then re-chunks and re-embeds {@code fullText}.
     *
     * @param contentItem the source content item (used for FK, type, metadata)
     * @param fullText    the assembled text to embed (title + body + answers, etc.)
     * @param extraMetadata additional key-value pairs merged into the JSONB metadata column
     */
    void ingest(ContentItem contentItem, String fullText, Map<String, Object> extraMetadata);

    /** Convenience overload with no extra metadata. */
    default void ingest(ContentItem contentItem, String fullText) {
        ingest(contentItem, fullText, Map.of());
    }

    /** Removes all embeddings for the given content item across all models. */
    void deleteEmbeddings(Integer contentItemId);
}
