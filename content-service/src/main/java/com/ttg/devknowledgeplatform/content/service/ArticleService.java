package com.ttg.devknowledgeplatform.content.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.ttg.devknowledgeplatform.content.entity.Article;
import com.ttg.devknowledgeplatform.content.enums.ContentStatus;
import com.ttg.devknowledgeplatform.content.enums.ContentType;

/**
 * Manages the lifecycle of articles and blog posts.
 *
 * <p>Both {@link ContentType#ARTICLE ARTICLE} and {@link ContentType#BLOG_POST BLOG_POST}
 * content types are handled by this service. Each piece of content is backed by a
 * {@code ContentItem} (shared metadata) and an {@code Article} (body text).
 *
 * <p>Slugs are auto-generated from the title and are globally unique across all content items.
 * The {@code publishedAt} timestamp is set automatically the first time a draft transitions
 * to {@link ContentStatus#PUBLISHED PUBLISHED}.
 *
 * <p>Returns entities rather than REST DTOs — {@code api}'s {@code ArticleMapper} does the
 * entity-to-response mapping, matching how {@code social-service}'s {@code FriendService} and
 * {@code ai-service}'s {@code RagQueryService} return internal models that {@code api} maps to
 * its own response types.
 */
public interface ArticleService {

    /**
     * Creates a new article or blog post.
     *
     * @param command  title, body, type, category, tags, and optional initial status
     * @param authorId the primary key of the authenticated author
     * @return the created article
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if {@code type} is not ARTICLE or BLOG_POST
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if the specified category or any tag does not exist
     */
    Article create(ArticleCommands.Create command, Integer authorId);

    /**
     * Updates an existing article.
     *
     * <p>The slug is regenerated only when the title changes. Tags are fully replaced
     * when {@code command.tagIds()} is non-null; a {@code null} value leaves them unchanged.
     *
     * @param id      the article's primary key
     * @param command the fields to update
     * @return the updated article
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if the article, category, or any tag does not exist
     */
    Article update(Integer id, ArticleCommands.Update command);

    /**
     * Returns a single article by its primary key.
     *
     * @param id the article's primary key
     * @return the matching article
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if not found
     */
    Article getById(Integer id);

    /**
     * Returns a single article by its URL slug.
     *
     * @param slug the URL-safe slug
     * @return the matching article
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if not found
     */
    Article getBySlug(String slug);

    /**
     * Returns a paginated, optionally filtered list of articles.
     *
     * @param pageable pagination and sort parameters
     * @param type     filter by content type; {@code null} returns both ARTICLE and BLOG_POST
     * @param status   filter by publication status; {@code null} returns all statuses
     * @param q        case-insensitive title substring filter; {@code null} or blank returns all
     * @return a page of matching articles
     */
    Page<Article> list(Pageable pageable, ContentType type, ContentStatus status, String q);

    /**
     * Permanently deletes an article and its backing content item.
     *
     * @param id the article's primary key
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if not found
     */
    void delete(Integer id);
}
