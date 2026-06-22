package com.ttg.devknowledgeplatform.api;

import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.InterviewQuestionDifficulty;
import com.ttg.devknowledgeplatform.dto.CustomOAuth2User;
import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.admin.CreateInterviewQuestionRequest;
import com.ttg.devknowledgeplatform.dto.admin.InterviewQuestionResponse;
import com.ttg.devknowledgeplatform.dto.admin.UpdateInterviewQuestionRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * HTTP contract for the admin interview-question management API.
 *
 * <p>Defines URL mappings, parameter bindings, and validation constraints for interview question
 * CRUD operations. The implementation ({@link com.ttg.devknowledgeplatform.api.impl.InterviewQuestionController})
 * carries no HTTP annotations.
 */
@RequestMapping("/api/v1/admin/interview-questions")
public interface InterviewQuestionApi {

    /**
     * Creates a new interview question owned by the authenticated principal.
     *
     * @param principal the authenticated OAuth2 user; used to resolve the author
     * @param request   validated creation payload
     * @return {@code 201} with the created interview question
     */
    @PostMapping
    ResponseEntity<InterviewQuestionResponse> create(
            @AuthenticationPrincipal CustomOAuth2User principal,
            @Valid @RequestBody CreateInterviewQuestionRequest request);

    /**
     * Updates an existing interview question by its primary key.
     *
     * @param id      interview question primary key
     * @param request validated update payload
     * @return {@code 200} with the updated interview question
     */
    @PutMapping("/{id}")
    ResponseEntity<InterviewQuestionResponse> update(
            @PathVariable Integer id, @Valid @RequestBody UpdateInterviewQuestionRequest request);

    /**
     * Deletes an interview question by its primary key.
     *
     * @param id interview question primary key
     * @return {@code 204 No Content}
     */
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Integer id);

    /**
     * Returns a single interview question by its primary key.
     *
     * @param id interview question primary key
     * @return {@code 200} with the interview question
     */
    @GetMapping("/{id}")
    ResponseEntity<InterviewQuestionResponse> getById(@PathVariable Integer id);

    /**
     * Returns a paginated, optionally filtered list of interview questions.
     *
     * @param page       zero-based page number (default 0)
     * @param size       page size (default 20)
     * @param sortBy     field to sort by; allowed values: {@code id}, {@code dteCreation}, {@code difficulty} (default {@code id})
     * @param sortDir    sort direction: {@code asc} or {@code desc} (default {@code desc})
     * @param difficulty optional difficulty filter
     * @param status     optional status filter
     * @param isCommon   optional flag to filter by common questions
     * @param q          optional full-text search query
     * @return {@code 200} with a paged list of interview questions
     */
    @GetMapping
    ResponseEntity<PagedResponse<InterviewQuestionResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) InterviewQuestionDifficulty difficulty,
            @RequestParam(required = false) ContentStatus status,
            @RequestParam(required = false) Boolean isCommon,
            @RequestParam(required = false) String q);
}
