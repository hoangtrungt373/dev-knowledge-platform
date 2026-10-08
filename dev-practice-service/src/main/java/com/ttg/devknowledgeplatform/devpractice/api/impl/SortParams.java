package com.ttg.devknowledgeplatform.devpractice.api.impl;

import java.util.Set;

import org.springframework.data.domain.Sort;

/**
 * Builds a {@link Sort} from a list endpoint's raw {@code sortBy}/{@code sortDir} request params,
 * accepting only an allow-listed field — so a client can't sort by an arbitrary (possibly
 * unindexed, or non-existent) property, and a typo falls back to a sane default instead of a 500.
 *
 * <p>Replaces the private {@code buildSort} each of this module's four list controllers carried. The
 * same helper is copied across ~12 controllers in other services too; it stays here, not in
 * {@code common}, until a second module actually adopts it (see {@code common/CLAUDE.md}).
 */
final class SortParams {

    private SortParams() {
        // Utility class - prevent instantiation
    }

    /**
     * @param sortBy           the requested field; anything outside {@code allowedFields} (or null)
     *                         becomes {@code defaultField}
     * @param sortDir          {@code "asc"}/{@code "desc"}, case-insensitive; anything else (or null)
     *                         becomes {@code defaultDirection}
     * @param allowedFields    the entity properties this endpoint may sort by
     * @param defaultField     used when {@code sortBy} isn't allowed
     * @param defaultDirection used when {@code sortDir} isn't recognized
     * @return a single-property sort
     */
    static Sort of(String sortBy, String sortDir, Set<String> allowedFields, String defaultField,
                   Sort.Direction defaultDirection) {
        String field = sortBy != null && allowedFields.contains(sortBy) ? sortBy : defaultField;
        Sort.Direction direction = Sort.Direction.fromOptionalString(sortDir).orElse(defaultDirection);
        return Sort.by(direction, field);
    }
}
