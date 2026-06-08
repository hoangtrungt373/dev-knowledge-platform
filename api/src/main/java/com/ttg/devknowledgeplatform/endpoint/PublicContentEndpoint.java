package com.ttg.devknowledgeplatform.endpoint;

import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.ContentType;
import com.ttg.devknowledgeplatform.common.enums.InterviewQuestionDifficulty;
import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.admin.ArticleResponse;
import com.ttg.devknowledgeplatform.dto.admin.InterviewQuestionResponse;
import com.ttg.devknowledgeplatform.repository.ContentItemRepository;
import com.ttg.devknowledgeplatform.service.ArticleService;
import com.ttg.devknowledgeplatform.service.InterviewQuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
@Slf4j
public class PublicContentEndpoint {

    private static final Set<String> IQ_SORT_FIELDS = Set.of("id", "dteCreation", "difficulty");
    private static final Set<String> ARTICLE_SORT_FIELDS = Set.of("id", "dteCreation");

    private final InterviewQuestionService interviewQuestionService;
    private final ArticleService articleService;
    private final ContentItemRepository contentItemRepository;

    // -------------------------------------------------------------------------
    // Interview Questions
    // -------------------------------------------------------------------------

    @GetMapping("/interview-questions")
    public ResponseEntity<PagedResponse<InterviewQuestionResponse>> listInterviewQuestions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) InterviewQuestionDifficulty difficulty,
            @RequestParam(required = false) Boolean isCommon,
            @RequestParam(required = false) String q) {

        Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir, IQ_SORT_FIELDS));
        PagedResponse<InterviewQuestionResponse> response =
                interviewQuestionService.list(pageable, difficulty, ContentStatus.PUBLISHED, isCommon, q);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/interview-questions/{slug}")
    @Transactional
    public ResponseEntity<InterviewQuestionResponse> getInterviewQuestionBySlug(@PathVariable String slug) {
        contentItemRepository.findBySlug(slug).ifPresent(ci -> contentItemRepository.incrementViewCount(ci.getId()));
        InterviewQuestionResponse response = interviewQuestionService.getBySlug(slug);
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // Articles
    // -------------------------------------------------------------------------

    @GetMapping("/articles")
    public ResponseEntity<PagedResponse<ArticleResponse>> listArticles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) ContentType type,
            @RequestParam(required = false) String q) {

        Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir, ARTICLE_SORT_FIELDS));
        PagedResponse<ArticleResponse> response =
                articleService.list(pageable, type, ContentStatus.PUBLISHED, q);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/articles/{slug}")
    @Transactional
    public ResponseEntity<ArticleResponse> getArticleBySlug(@PathVariable String slug) {
        contentItemRepository.findBySlug(slug).ifPresent(ci -> contentItemRepository.incrementViewCount(ci.getId()));
        ArticleResponse response = articleService.getBySlug(slug);
        return ResponseEntity.ok(response);
    }

    private Sort buildSort(String sortBy, String sortDir, Set<String> allowed) {
        String field = allowed.contains(sortBy) ? sortBy : "id";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }
}
