package com.ttg.devknowledgeplatform.service;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

import com.ttg.devknowledgeplatform.common.exception.ErrorCode;

public interface SlugService {

    /**
     * Convert input text to a URL-safe slug (lowercase, diacritics stripped, non-alphanumeric → hyphen).
     */
    String toSlug(String input);

    /**
     * Generate a unique slug by appending an incrementing counter until the candidate is not taken.
     *
     * @param input        the text to slugify
     * @param exists       returns {@code true} if a given slug is already in use
     * @param conflictCode error code thrown when {@code MAX_SLUG_ATTEMPTS} is exceeded
     */
    String generateUniqueSlug(String input, Predicate<String> exists, ErrorCode conflictCode);

    /**
     * Generate a unique slug for an update, treating the current entity's own slug as available.
     *
     * @param input            the text to slugify
     * @param existsExcluding  returns {@code true} if the slug is taken by any entity other than {@code excludeId}
     * @param excludeId        the id of the entity being updated
     * @param conflictCode     error code thrown when {@code MAX_SLUG_ATTEMPTS} is exceeded
     */
    String generateUniqueSlug(String input, BiPredicate<String, Integer> existsExcluding,
            Integer excludeId, ErrorCode conflictCode);
}
