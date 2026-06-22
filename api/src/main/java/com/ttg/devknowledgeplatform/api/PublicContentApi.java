package com.ttg.devknowledgeplatform.api;

import com.ttg.devknowledgeplatform.common.enums.ContentType;
import com.ttg.devknowledgeplatform.common.enums.InterviewQuestionDifficulty;
import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.admin.ArticleResponse;
import com.ttg.devknowledgeplatform.dto.admin.InterviewQuestionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * HTTP contract for the public-facing content API.
 *
 * <p>Exposes read-only, unauthenticated endpoints for browsing published articles and
 * interview questions. The implementation
 * ({@link com.ttg.devknowledgeplatform.api.impl.PublicContentController}) carries no HTTP annotations.
 */
@RequestMapping("/api/v1/public")
public interface PublicContentApi {

    /**
     * Returns a paginated, optionally filtered list of published interview questions.
     *
     * @param page       zero-based page number (default 0)
     * @param size       page size (default 20)
     * @param sortBy     field to sort by; allowed values: {@code id}, {@code dteCreation}, {@code difficulty} (default {@code id})
     * @param sortDir    sort direction: {@code asc} or {@code desc} (default {@code desc})
     * @param difficulty optional difficulty filter
     * @param isCommon   optional flag to filter common questions
     * @param q          optional full-text search query
     * @return {@code 200} with a paged list of published interview questions
     */
    @GetMapping("/interview-questions")
    ResponseEntity<PagedResponse<InterviewQuestionResponse>> listInterviewQuestions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) InterviewQuestionDifficulty difficulty,
            @RequestParam(required = false) Boolean isCommon,
            @RequestParam(required = false) String q);

    /**
     * Returns a published interview question by its URL slug and increments the view count.
     *
     * @param slug URL-friendly identifier of the interview question
     * @return {@code 200} with the interview question
     */
    @GetMapping("/interview-questions/{slug}")
    ResponseEntity<InterviewQuestionResponse> getInterviewQuestionBySlug(@PathVariable String slug);

    /**
     * Returns a paginated, optionally filtered list of published articles.
     *
     * @param page    zero-based page number (default 0)
     * @param size    page size (default 20)
     * @param sortBy  field to sort by; allowed values: {@code id}, {@code dteCreation} (default {@code id})
     * @param sortDir sort direction: {@code asc} or {@code desc} (default {@code desc})
     * @param type    optional content-type filter
     * @param q       optional full-text search query
     * @return {@code 200} with a paged list of published articles
     */
    @GetMapping("/articles")
    ResponseEntity<PagedResponse<ArticleResponse>> listArticles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) ContentType type,
            @RequestParam(required = false) String q);

    /**
     * Returns a published article by its URL slug and increments the view count.
     *
     * @param slug URL-friendly identifier of the article
     * @return {@code 200} with the article
     */
    @GetMapping("/articles/{slug}")
    ResponseEntity<ArticleResponse> getArticleBySlug(@PathVariable String slug);
}
