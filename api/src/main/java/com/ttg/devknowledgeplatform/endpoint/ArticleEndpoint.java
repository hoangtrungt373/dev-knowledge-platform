package com.ttg.devknowledgeplatform.endpoint;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.ContentType;
import com.ttg.devknowledgeplatform.dto.CustomOAuth2User;
import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.admin.ArticleResponse;
import com.ttg.devknowledgeplatform.dto.admin.CreateArticleRequest;
import com.ttg.devknowledgeplatform.dto.admin.UpdateArticleRequest;
import com.ttg.devknowledgeplatform.service.ArticleService;
import com.ttg.devknowledgeplatform.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/articles")
@RequiredArgsConstructor
@Slf4j
public class ArticleEndpoint {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "dteCreation");

    private final ArticleService articleService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<ArticleResponse> create(
            @AuthenticationPrincipal CustomOAuth2User principal,
            @Valid @RequestBody CreateArticleRequest request) {
        Integer authorId = resolveAuthorId(principal);
        ArticleResponse response = articleService.create(request, authorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ArticleResponse> update(
            @PathVariable Integer id, @Valid @RequestBody UpdateArticleRequest request) {
        ArticleResponse response = articleService.update(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        articleService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ArticleResponse> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(articleService.getById(id));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<ArticleResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) ContentType type,
            @RequestParam(required = false) ContentStatus status,
            @RequestParam(required = false) String q) {

        Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir));
        PagedResponse<ArticleResponse> response = articleService.list(pageable, type, status, q);
        return ResponseEntity.ok(response);
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
