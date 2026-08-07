package com.ttg.devknowledgeplatform.ai.service;

import com.ttg.devknowledgeplatform.ai.dto.ContentEmbeddingMetadata;
import com.ttg.devknowledgeplatform.common.enums.ContentType;

/**
 * Chunks and embeds content text, persisting vectors to {@code CONTENT_EMBEDDING}.
 *
 * <p>The caller is responsible for extracting the full text from the type-specific
 * projection ({@code InternalContentItemDto}'s question/article fields) before calling
 * {@link #ingest}. Takes a plain {@code contentItemId}/{@code ContentType} rather than a live
 * {@code ContentItem} entity — that entity lives in {@code content-service}'s own database once
 * that module is extracted into a standalone service, so this module only ever sees it via
 * {@code ContentServiceClient}'s HTTP response, never a JPA reference.
 *
 * <p>The caller is also responsible for constructing the {@link ContentEmbeddingMetadata}
 * that will be stored on every chunk — keeping the metadata schema contract in one place
 * rather than split between modules.
 */
public interface ContentIngestionService {

    /**
     * Replaces all embeddings for {@code contentItemId} under the active model,
     * then re-chunks and re-embeds {@code fullText}.
     *
     * @param contentItemId the source content item's id (stored as a plain column, not an FK)
     * @param sourceType    the source content item's type
     * @param fullText      the assembled text to embed (title + body + answers, etc.)
     * @param metadata      fully populated metadata DTO stored verbatim on every chunk's JSONB column
     */
    void ingest(Integer contentItemId, ContentType sourceType, String fullText, ContentEmbeddingMetadata metadata);

    /** Removes all embeddings for the given content item across all models. */
    void deleteEmbeddings(Integer contentItemId);
}
