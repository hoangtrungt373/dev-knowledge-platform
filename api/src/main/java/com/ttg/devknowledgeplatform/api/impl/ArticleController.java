package com.ttg.devknowledgeplatform.api.impl;

import com.ttg.devknowledgeplatform.api.ArticleApi;
import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.content.entity.Article;
import com.ttg.devknowledgeplatform.content.enums.ContentStatus;
import com.ttg.devknowledgeplatform.content.enums.ContentType;
import com.ttg.devknowledgeplatform.content.service.ArticleCommands;
import com.ttg.devknowledgeplatform.content.service.ArticleService;
import com.ttg.devknowledgeplatform.dto.CustomOAuth2User;
import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.content.ArticleResponse;
import com.ttg.devknowledgeplatform.dto.content.CreateArticleRequest;
import com.ttg.devknowledgeplatform.dto.content.UpdateArticleRequest;
import com.ttg.devknowledgeplatform.mapper.ArticleMapper;
import com.ttg.devknowledgeplatform.security.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * Implementation of {@link ArticleApi}.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ArticleController implements ArticleApi {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "dteCreation");

    private final ArticleService articleService;
    private final ArticleMapper articleMapper;
    private final UserService userService;

    @Override
    public ResponseEntity<ArticleResponse> create(CustomOAuth2User principal, CreateArticleRequest request) {
        Integer authorId = resolveAuthorId(principal);
        ArticleCommands.Create command = new ArticleCommands.Create(
                request.getTitle(), request.getType(), request.getBody(),
                request.getStatus(), request.getCategoryId(), request.getTagIds());
        Article created = articleService.create(command, authorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(articleMapper.toResponse(created));
    }

    @Override
    public ResponseEntity<ArticleResponse> update(Integer id, UpdateArticleRequest request) {
        ArticleCommands.Update command = new ArticleCommands.Update(
                request.getTitle(), request.getType(), request.getBody(),
                request.getStatus(), request.getCategoryId(), request.getTagIds());
        Article updated = articleService.update(id, command);
        return ResponseEntity.ok(articleMapper.toResponse(updated));
    }

    @Override
    public ResponseEntity<Void> delete(Integer id) {
        articleService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ArticleResponse> getById(Integer id) {
        return ResponseEntity.ok(articleMapper.toResponse(articleService.getById(id)));
    }

    @Override
    public ResponseEntity<PagedResponse<ArticleResponse>> list(
            int page, int size, String sortBy, String sortDir,
            ContentType type, ContentStatus status, String q) {
        Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir));
        Page<ArticleResponse> responses = articleService.list(pageable, type, status, q)
                .map(articleMapper::toResponse);
        return ResponseEntity.ok(PagedResponse.from(responses));
    }

    private Integer resolveAuthorId(CustomOAuth2User principal) {
        if (principal == null) return null;
        User user = userService.findByEmail(principal.getEmail());
        return user != null ? user.getId() : null;
    }

    private Sort buildSort(String sortBy, String sortDir) {
        String field = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "id";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }
}
