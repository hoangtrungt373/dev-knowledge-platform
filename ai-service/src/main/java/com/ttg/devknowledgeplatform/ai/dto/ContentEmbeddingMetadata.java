package com.ttg.devknowledgeplatform.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Typed schema for the JSONB {@code METADATA} column on {@code ContentEmbedding}.
 *
 * <p>Replaces the untyped {@code Map<String, Object>} that was previously stored in that column.
 * Hibernate serialises this record to JSON on write and deserialises it on read using the
 * Jackson {@code ObjectMapper} wired by Spring Boot — no manual {@code ObjectMapper} calls needed.
 *
 * <p>All fields are optional ({@code null} when not applicable to the content type).
 * {@link JsonInclude#NON_NULL} suppresses {@code null} entries from the stored JSON to keep
 * the JSONB payload compact.
 *
 * @param type         {@link com.ttg.devknowledgeplatform.content.enums.ContentType} name
 * @param status       {@link com.ttg.devknowledgeplatform.content.enums.ContentStatus} name
 * @param title        human-readable title of the parent content item
 * @param categoryId   database identifier of the assigned category; {@code null} if uncategorised
 * @param categoryName display name of the assigned category; {@code null} if uncategorised
 * @param tagIds       database identifiers of all tags attached to the content item
 * @param tagNames     display names of all tags attached to the content item;
 *                     matched against {@link RagFilter#tags()}
 *                     by {@link com.ttg.devknowledgeplatform.ai.filter.MetadataTagFilterStrategy}
 * @param difficulty   difficulty level name (e.g. {@code "EASY"}); non-null only when a
 *                     {@code QUESTION_ANSWER} genuinely has interview-specific framing
 * @param isCommon     whether this is a frequently asked interview question; non-null only when a
 *                     {@code QUESTION_ANSWER} genuinely has interview-specific framing
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContentEmbeddingMetadata(
        String type,
        String status,
        String title,
        Integer categoryId,
        String categoryName,
        List<Integer> tagIds,
        List<String> tagNames,
        String difficulty,
        Boolean isCommon
) {}
