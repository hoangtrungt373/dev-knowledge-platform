package com.ttg.devknowledgeplatform.api;

import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.ContentType;
import com.ttg.devknowledgeplatform.dto.CustomOAuth2User;
import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.admin.ArticleResponse;
import com.ttg.devknowledgeplatform.dto.admin.CreateArticleRequest;
import com.ttg.devknowledgeplatform.dto.admin.UpdateArticleRequest;
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
 * HTTP contract for the admin article management API.
 *
 * <p>Defines URL mappings, accepted media types, parameter bindings, and validation constraints
 * for article CRUD operations. The implementation ({@link com.ttg.devknowledgeplatform.api.impl.ArticleController})
 * contains only delegation logic and carries no HTTP annotations.
 */
@RequestMapping("/api/v1/admin/articles")
public interface ArticleApi {

    /**
     * Creates a new article owned by the authenticated principal.
     *
     * @param principal the authenticated OAuth2 user; used to resolve the author
     * @param request   validated creation payload
     * @return {@code 201} with the created article
     */
    @PostMapping
    ResponseEntity<ArticleResponse> create(
            @AuthenticationPrincipal CustomOAuth2User principal,
            @Valid @RequestBody CreateArticleRequest request);

    /**
     * Updates an existing article by its primary key.
     *
     * @param id      article primary key
     * @param request validated update payload
     * @return {@code 200} with the updated article
     */
    @PutMapping("/{id}")
    ResponseEntity<ArticleResponse> update(
            @PathVariable Integer id, @Valid @RequestBody UpdateArticleRequest request);

    /**
     * Deletes an article by its primary key.
     *
     * @param id article primary key
     * @return {@code 204 No Content}
     */
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Integer id);

    /**
     * Returns a single article by its primary key.
     *
     * @param id article primary key
     * @return {@code 200} with the article
     */
    @GetMapping("/{id}")
    ResponseEntity<ArticleResponse> getById(@PathVariable Integer id);

    /**
     * Returns a paginated, optionally filtered list of articles.
     *
     * @param page    zero-based page number (default 0)
     * @param size    page size (default 20)
     * @param sortBy  field to sort by; allowed values: {@code id}, {@code dteCreation} (default {@code id})
     * @param sortDir sort direction: {@code asc} or {@code desc} (default {@code desc})
     * @param type    optional content-type filter
     * @param status  optional status filter
     * @param q       optional full-text search query
     * @return {@code 200} with a paged list of articles
     */
    @GetMapping
    ResponseEntity<PagedResponse<ArticleResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) ContentType type,
            @RequestParam(required = false) ContentStatus status,
            @RequestParam(required = false) String q);
}
