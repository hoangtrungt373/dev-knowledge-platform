package com.ttg.devknowledgeplatform.devpractice.api;

import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.devpractice.dto.CreateProblemRequest;
import com.ttg.devknowledgeplatform.devpractice.dto.ProblemResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.ProblemSummaryResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.UpdateProblemRequest;
import com.ttg.devknowledgeplatform.devpractice.enums.Difficulty;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ttg.devknowledgeplatform.common.annotation.CurrentUserId;

/**
 * HTTP contract for the admin problem-catalog management API — {@code ROLE_ADMIN} only (enforced
 * by {@link com.ttg.devknowledgeplatform.devpractice.security.SecurityConfig}). The implementation
 * ({@link com.ttg.devknowledgeplatform.devpractice.api.impl.ProblemController}) carries no HTTP
 * annotations.
 */
@RequestMapping("/api/v1/admin/problems")
public interface ProblemApi {

    /**
     * Creates a new problem, owned (authored) by the calling admin.
     *
     * @param authorUuid the authenticated caller's Keycloak subject id
     * @param request    validated creation payload, including its full test-case set
     * @return {@code 201} with the created problem
     */
    @PostMapping
    ResponseEntity<ProblemResponse> create(
            @CurrentUserId String authorUuid, @Valid @RequestBody CreateProblemRequest request);

    /**
     * Updates an existing problem, replacing its test-case set wholesale.
     *
     * @param id      problem primary key
     * @param request validated update payload
     * @return {@code 200} with the updated problem
     */
    @PutMapping("/{id}")
    ResponseEntity<ProblemResponse> update(@PathVariable Integer id, @Valid @RequestBody UpdateProblemRequest request);

    /**
     * Deletes a problem (and its test cases, via cascade).
     *
     * @param id problem primary key
     * @return {@code 204 No Content}
     */
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Integer id);

    /**
     * Returns a single problem by its primary key, including every test case (sample and hidden).
     *
     * @param id problem primary key
     * @return {@code 200} with the problem
     */
    @GetMapping("/{id}")
    ResponseEntity<ProblemResponse> getById(@PathVariable Integer id);

    /**
     * Returns a paginated, optionally filtered list of problems, in any status.
     *
     * @param page       zero-based page number (default 0)
     * @param size       page size (default 20)
     * @param sortBy     field to sort by; allowed values: {@code id}, {@code dteCreation} (default {@code id})
     * @param sortDir    sort direction: {@code asc} or {@code desc} (default {@code desc})
     * @param difficulty optional difficulty filter
     * @param status     optional status filter
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
            @RequestParam(required = false) ContentStatus status,
            @RequestParam(required = false) String q);
}
