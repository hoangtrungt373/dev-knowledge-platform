package com.ttg.devknowledgeplatform.service;

import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.QuestionDifficulty;
import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.admin.CreateQuestionAnswerRequest;
import com.ttg.devknowledgeplatform.dto.admin.QuestionAnswerResponse;
import com.ttg.devknowledgeplatform.dto.admin.UpdateQuestionAnswerRequest;
import org.springframework.data.domain.Pageable;

/**
 * Manages the lifecycle of question-and-answer content — general dev-knowledge Q&A, not only
 * interview prep.
 *
 * <p>Each question is backed by a {@code ContentItem} (shared metadata such as title, slug,
 * category, and tags) and a {@code QuestionAnswer} (question body, answers, and the optional
 * {@code difficulty}/{@code isCommon} interview-specific metadata, populated only when a
 * question genuinely has that framing).
 *
 * <p>Slugs are auto-generated from the title and are globally unique across all content items.
 * The {@code publishedAt} timestamp is set automatically the first time a draft transitions
 * to {@link com.ttg.devknowledgeplatform.common.enums.ContentStatus#PUBLISHED PUBLISHED}.
 */
public interface QuestionAnswerService {

    /**
     * Creates a new question-and-answer content item.
     *
     * @param request  title, body, answers, optional difficulty, category, tags, and optional initial status
     * @param authorId the primary key of the authenticated author
     * @return the created question
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if the specified category or any tag does not exist
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if any tag ID is inactive
     */
    QuestionAnswerResponse create(CreateQuestionAnswerRequest request, Integer authorId);

    /**
     * Updates an existing question-and-answer content item.
     *
     * <p>The slug is regenerated only when the title changes. Tags are fully replaced
     * when {@code request.tagIds} is non-null; a {@code null} value leaves them unchanged.
     *
     * @param id      the question's primary key
     * @param request the fields to update
     * @return the updated question
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if the question, category, or any tag does not exist
     */
    QuestionAnswerResponse update(Integer id, UpdateQuestionAnswerRequest request);

    /**
     * Returns a single question by its primary key.
     *
     * @param id the question's primary key
     * @return the matching question
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if not found
     */
    QuestionAnswerResponse getById(Integer id);

    /**
     * Returns a single question by its URL slug.
     *
     * @param slug the URL-safe slug
     * @return the matching question
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if not found
     */
    QuestionAnswerResponse getBySlug(String slug);

    /**
     * Returns a paginated, optionally filtered list of questions.
     *
     * @param pageable   pagination and sort parameters
     * @param difficulty filter by difficulty level; {@code null} returns all difficulties (including those with no difficulty set)
     * @param status     filter by publication status; {@code null} returns all statuses
     * @param isCommon   when {@code true}, returns only commonly-asked questions; {@code null} returns all
     * @param q          case-insensitive title substring filter; {@code null} or blank returns all
     * @return a page of matching questions
     */
    PagedResponse<QuestionAnswerResponse> list(
            Pageable pageable,
            QuestionDifficulty difficulty,
            ContentStatus status,
            Boolean isCommon,
            String q);

    /**
     * Permanently deletes a question and its backing content item.
     *
     * @param id the question's primary key
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if not found
     */
    void delete(Integer id);
}
