package com.ttg.devknowledgeplatform.content.api;

import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.ContentType;
import com.ttg.devknowledgeplatform.content.dto.internal.InternalContentItemResponse;
import com.ttg.devknowledgeplatform.content.dto.internal.UpdateQualityScoreRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * HTTP contract for the internal, server-to-server indexing API — {@code ai-service}'s only way
 * to read/mutate content-item data once this module stops being a Maven dependency it can call
 * in-process. Not part of this module's public/admin REST surface: every path lives under
 * {@code /internal/**}, authenticated by a shared API key
 * ({@code InternalApiKeyFilter}/{@code app.internal-api.key}) rather than an end-user JWT — see
 * {@code content-service/CLAUDE.md} for the full rationale and the alternative considered.
 *
 * <p>The implementation ({@link com.ttg.devknowledgeplatform.content.api.impl.InternalContentController})
 * contains only delegation logic and carries no HTTP annotations.
 */
@RequestMapping("/internal/content-items")
public interface InternalContentApi {

    /**
     * Returns a single content item for on-demand (re)indexing.
     *
     * @param id content item primary key
     * @return {@code 200} with the flattened content item projection
     */
    @GetMapping("/{id}")
    ResponseEntity<InternalContentItemResponse> getById(@PathVariable Integer id);

    /**
     * Returns every content item with the given status, unpaged — backs bulk "index all published
     * content" indexing runs.
     *
     * @param status status to filter by
     * @return {@code 200} with the full matching list
     */
    @GetMapping("/by-status/{status}")
    ResponseEntity<List<InternalContentItemResponse>> listByStatus(@PathVariable ContentStatus status);

    /**
     * Returns a paginated, optionally filtered list of content items — backs the admin
     * embedding-index screen.
     *
     * @param page   zero-based page number (default 0)
     * @param size   page size (default 20)
     * @param q      optional case-insensitive title search
     * @param type   optional content-type filter
     * @param status optional status filter
     * @param ids        optional comma-separated restriction to this exact set of content item ids
     * @param excludeIds optional comma-separated exclusion of this exact set of content item ids
     * @return {@code 200} with a paged list of content items, sorted by id descending
     */
    @GetMapping
    ResponseEntity<PagedResponse<InternalContentItemResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ContentType type,
            @RequestParam(required = false) ContentStatus status,
            @RequestParam(required = false) List<Integer> ids,
            @RequestParam(required = false) List<Integer> excludeIds);

    /**
     * Persists a document's corpus-coherence quality score immediately after indexing.
     *
     * @param id      content item primary key
     * @param request validated request body carrying the score
     * @return {@code 204 No Content}
     */
    @PatchMapping("/{id}/quality-score")
    ResponseEntity<Void> updateQualityScore(
            @PathVariable Integer id, @Valid @RequestBody UpdateQualityScoreRequest request);
}
