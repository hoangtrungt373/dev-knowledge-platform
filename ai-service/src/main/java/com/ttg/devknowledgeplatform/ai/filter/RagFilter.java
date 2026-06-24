package com.ttg.devknowledgeplatform.ai.filter;

import com.ttg.devknowledgeplatform.common.enums.ContentType;

import java.util.Set;

/**
 * Immutable filter criteria applied to the RAG retrieval step.
 *
 * <p>All fields are optional — {@code null} or empty means "no constraint on that dimension."
 * When multiple fields are populated the resulting predicates are composed with AND semantics:
 * a chunk must satisfy every active constraint.
 *
 * <p>Use the static factory methods for the most common single-dimension cases, or construct
 * the record directly when combining dimensions.
 *
 * @param sourceTypes restrict retrieval to chunks whose {@code sourceType} is in this set;
 *                    {@code null} or empty disables the constraint
 * @param tags        restrict retrieval to chunks that share at least one tag name with this set;
 *                    matched against the {@code tagNames} array stored in the chunk's JSONB metadata;
 *                    {@code null} or empty disables the constraint
 * @param categoryId  restrict retrieval to chunks belonging to this category;
 *                    matched against {@code categoryId} in the chunk's JSONB metadata;
 *                    {@code null} disables the constraint
 */
public record RagFilter(
        Set<ContentType> sourceTypes,
        Set<String> tags,
        Integer categoryId
) {

    /** No filtering — every chunk passes through. */
    public static RagFilter none() {
        return new RagFilter(null, null, null);
    }

    /** Restricts retrieval to the given content types. */
    public static RagFilter bySourceType(ContentType... types) {
        return new RagFilter(Set.of(types), null, null);
    }

    /** Restricts retrieval to chunks belonging to the given category. */
    public static RagFilter byCategory(Integer categoryId) {
        return new RagFilter(null, null, categoryId);
    }

    /**
     * Returns {@code true} when no constraint is active on any dimension.
     * The service uses this check to skip oversampling for unfiltered queries.
     */
    public boolean isEmpty() {
        return (sourceTypes == null || sourceTypes.isEmpty())
                && (tags == null || tags.isEmpty())
                && categoryId == null;
    }
}
