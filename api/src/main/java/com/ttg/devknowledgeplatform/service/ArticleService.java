package com.ttg.devknowledgeplatform.service;

import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.ContentType;
import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.admin.ArticleResponse;
import com.ttg.devknowledgeplatform.dto.admin.CreateArticleRequest;
import com.ttg.devknowledgeplatform.dto.admin.UpdateArticleRequest;
import org.springframework.data.domain.Pageable;

/**
 * Manages the lifecycle of articles and blog posts.
 *
 * <p>Both {@link com.ttg.devknowledgeplatform.common.enums.ContentType#ARTICLE ARTICLE} and
 * {@link com.ttg.devknowledgeplatform.common.enums.ContentType#BLOG_POST BLOG_POST} content types
 * are handled by this service. Each piece of content is backed by a {@code ContentItem}
 * (shared metadata) and an {@code Article} (body text).
 *
 * <p>Slugs are auto-generated from the title and are globally unique across all content items.
 * The {@code publishedAt} timestamp is set automatically the first time a draft transitions
 * to {@link com.ttg.devknowledgeplatform.common.enums.ContentStatus#PUBLISHED PUBLISHED}.
 */
public interface ArticleService {

    /**
     * Creates a new article or blog post.
     *
     * @param request  title, body, type, category, tags, and optional initial status
     * @param authorId the primary key of the authenticated author
     * @return the created article
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if {@code type} is not ARTICLE or BLOG_POST
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if the specified category or any tag does not exist
     */
    ArticleResponse create(CreateArticleRequest request, Integer authorId);

    /**
     * Updates an existing article.
     *
     * <p>The slug is regenerated only when the title changes. Tags are fully replaced
     * when {@code request.tagIds} is non-null; a {@code null} value leaves them unchanged.
     *
     * @param id      the article's primary key
     * @param request the fields to update
     * @return the updated article
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if the article, category, or any tag does not exist
     */
    ArticleResponse update(Integer id, UpdateArticleRequest request);

    /**
     * Returns a single article by its primary key.
     *
     * @param id the article's primary key
     * @return the matching article
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if not found
     */
    ArticleResponse getById(Integer id);

    /**
     * Returns a single article by its URL slug.
     *
     * @param slug the URL-safe slug
     * @return the matching article
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if not found
     */
    ArticleResponse getBySlug(String slug);

    /**
     * Returns a paginated, optionally filtered list of articles.
     *
     * @param pageable pagination and sort parameters
     * @param type     filter by content type; {@code null} returns both ARTICLE and BLOG_POST
     * @param status   filter by publication status; {@code null} returns all statuses
     * @param q        case-insensitive title substring filter; {@code null} or blank returns all
     * @return a page of matching articles
     */
    PagedResponse<ArticleResponse> list(Pageable pageable, ContentType type, ContentStatus status, String q);

    /**
     * Permanently deletes an article and its backing content item.
     *
     * @param id the article's primary key
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if not found
     */
    void delete(Integer id);
}
