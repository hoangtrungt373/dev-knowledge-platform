package com.ttg.devknowledgeplatform.service;

import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.InterviewQuestionDifficulty;
import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.admin.CreateInterviewQuestionRequest;
import com.ttg.devknowledgeplatform.dto.admin.InterviewQuestionResponse;
import com.ttg.devknowledgeplatform.dto.admin.UpdateInterviewQuestionRequest;
import org.springframework.data.domain.Pageable;

/**
 * Manages the lifecycle of interview questions.
 *
 * <p>Each question is backed by a {@code ContentItem} (shared metadata such as title, slug,
 * category, and tags) and an {@code InterviewQuestion} (difficulty, question body, answers,
 * and the {@code isCommon} flag that marks frequently-asked questions).
 *
 * <p>Slugs are auto-generated from the title and are globally unique across all content items.
 * The {@code publishedAt} timestamp is set automatically the first time a draft transitions
 * to {@link com.ttg.devknowledgeplatform.common.enums.ContentStatus#PUBLISHED PUBLISHED}.
 */
public interface InterviewQuestionService {

    /**
     * Creates a new interview question.
     *
     * @param request  title, body, answers, difficulty, category, tags, and optional initial status
     * @param authorId the primary key of the authenticated author
     * @return the created interview question
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if the specified category or any tag does not exist
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if any tag ID is inactive
     */
    InterviewQuestionResponse create(CreateInterviewQuestionRequest request, Integer authorId);

    /**
     * Updates an existing interview question.
     *
     * <p>The slug is regenerated only when the title changes. Tags are fully replaced
     * when {@code request.tagIds} is non-null; a {@code null} value leaves them unchanged.
     *
     * @param id      the question's primary key
     * @param request the fields to update
     * @return the updated interview question
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if the question, category, or any tag does not exist
     */
    InterviewQuestionResponse update(Integer id, UpdateInterviewQuestionRequest request);

    /**
     * Returns a single interview question by its primary key.
     *
     * @param id the question's primary key
     * @return the matching interview question
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if not found
     */
    InterviewQuestionResponse getById(Integer id);

    /**
     * Returns a single interview question by its URL slug.
     *
     * @param slug the URL-safe slug
     * @return the matching interview question
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if not found
     */
    InterviewQuestionResponse getBySlug(String slug);

    /**
     * Returns a paginated, optionally filtered list of interview questions.
     *
     * @param pageable   pagination and sort parameters
     * @param difficulty filter by difficulty level; {@code null} returns all difficulties
     * @param status     filter by publication status; {@code null} returns all statuses
     * @param isCommon   when {@code true}, returns only commonly-asked questions; {@code null} returns all
     * @param q          case-insensitive title substring filter; {@code null} or blank returns all
     * @return a page of matching interview questions
     */
    PagedResponse<InterviewQuestionResponse> list(
            Pageable pageable,
            InterviewQuestionDifficulty difficulty,
            ContentStatus status,
            Boolean isCommon,
            String q);

    /**
     * Permanently deletes an interview question and its backing content item.
     *
     * @param id the question's primary key
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if not found
     */
    void delete(Integer id);
}