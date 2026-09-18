package com.ttg.devknowledgeplatform.devpractice.api;

import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.ProblemResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.ProblemSummaryResponse;
import com.ttg.devknowledgeplatform.devpractice.enums.Difficulty;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * HTTP contract for the public-facing problem catalog — read-only, unauthenticated
 * ({@code permitAll()} in {@link com.ttg.devknowledgeplatform.devpractice.security.SecurityConfig}).
 * Only ever surfaces {@code PUBLISHED} problems, and only ever their sample test cases — see
 * {@link com.ttg.devknowledgeplatform.devpractice.service.ProblemService#getPublishedBySlug} and
 * {@link com.ttg.devknowledgeplatform.devpractice.mapper.ProblemMapper#toPublicResponse}.
 */
@RequestMapping("/api/v1/public/problems")
public interface PublicProblemApi {

    /**
     * Returns a paginated, optionally filtered list of published problems.
     *
     * @param page       zero-based page number (default 0)
     * @param size       page size (default 20)
     * @param sortBy     field to sort by; allowed values: {@code id}, {@code dteCreation} (default {@code id})
     * @param sortDir    sort direction: {@code asc} or {@code desc} (default {@code desc})
     * @param difficulty optional difficulty filter
     * @param q          optional title search
     * @return {@code 200} with a paged list of problem summaries
     */
    @GetMapping
    ResponseEntity<PagedResponse<ProblemSummaryResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) Difficulty difficulty,
            @RequestParam(required = false) String q);

    /**
     * Returns a published problem by its URL slug, with sample test cases only.
     *
     * @param slug URL-friendly identifier of the problem
     * @return {@code 200} with the problem
     */
    @GetMapping("/{slug}")
    ResponseEntity<ProblemResponse> getBySlug(@PathVariable String slug);
}
