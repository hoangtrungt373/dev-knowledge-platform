package com.ttg.devknowledgeplatform.ai.dto;

/**
 * A single knowledge-base chunk retrieved during similarity search.
 *
 * @param contentItemId ID of the source {@code ContentItem} this chunk belongs to.
 * @param sourceType    Content type of the source (e.g. {@code QUESTION_ANSWER}, {@code ARTICLE}).
 * @param title         Title of the source content item, for display purposes.
 * @param chunkText     The raw text segment that was embedded and matched the user's question.
 * @param similarity    Cosine similarity score in the range [0, 1] — higher means more relevant.
 */
public record RagSource(
        Integer contentItemId,
        String sourceType,
        String title,
        String chunkText,
        float similarity
) {}
