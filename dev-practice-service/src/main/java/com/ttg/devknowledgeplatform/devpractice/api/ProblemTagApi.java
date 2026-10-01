package com.ttg.devknowledgeplatform.devpractice.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.ProblemTagRequest;
import com.ttg.devknowledgeplatform.devpractice.dto.ProblemTagResponse;

import jakarta.validation.Valid;

/**
 * HTTP contract for managing the problem-tag catalog — {@code ROLE_ADMIN} only (the
 * {@code /api/v1/admin/**} rule in {@code SecurityConfig}).
 */
@RequestMapping("/api/v1/admin/problem-tags")
public interface ProblemTagApi {

    /**
     * @param request the new tag's name
     * @return {@code 201} with the created tag; {@code 409 PROBLEM_TAG_NAME_CONFLICT} if the name
     *         already exists (case-insensitively)
     */
    @PostMapping
    ResponseEntity<ProblemTagResponse> create(@Valid @RequestBody ProblemTagRequest request);

    /**
     * Renames a tag — visible on every problem using it.
     *
     * @param id      tag primary key
     * @param request the new name
     * @return {@code 200} with the updated tag
     */
    @PutMapping("/{id}")
    ResponseEntity<ProblemTagResponse> update(@PathVariable Integer id, @Valid @RequestBody ProblemTagRequest request);

    /**
     * @param id tag primary key
     * @return {@code 204}; {@code 409 PROBLEM_TAG_IN_USE} while any problem still uses the tag
     */
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Integer id);

    /**
     * Paginated tag list for the admin Tags page.
     *
     * @param page    zero-based page number (default 0)
     * @param size    page size (default 20)
     * @param sortBy  {@code name}, {@code id} or {@code dteCreation} (default {@code name})
     * @param sortDir {@code asc} or {@code desc} (default {@code asc})
     * @param q       optional case-insensitive name search
     * @return {@code 200} with a page of tags
     */
    @GetMapping
    ResponseEntity<PagedResponse<ProblemTagResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String q);

    /**
     * Every tag, sorted by name, unpaginated — for the problem form's tag picker.
     *
     * @return {@code 200} with all tags
     */
    @GetMapping("/all")
    ResponseEntity<List<ProblemTagResponse>> listAll();
}
