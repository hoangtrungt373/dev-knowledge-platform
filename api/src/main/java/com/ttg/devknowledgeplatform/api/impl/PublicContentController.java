package com.ttg.devknowledgeplatform.api.impl;

import com.ttg.devknowledgeplatform.api.PublicContentApi;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * Implementation of {@link PublicContentApi}.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class PublicContentController implements PublicContentApi {

    private static final Set<String> IQ_SORT_FIELDS = Set.of("id", "dteCreation", "difficulty");
    private static final Set<String> ARTICLE_SORT_FIELDS = Set.of("id", "dteCreation");

    private final InterviewQuestionService interviewQuestionService;
    private final ArticleService articleService;
    private final ContentItemRepository contentItemRepository;

    @Override
    public ResponseEntity<PagedResponse<InterviewQuestionResponse>> listInterviewQuestions(
            int page, int size, String sortBy, String sortDir,
            InterviewQuestionDifficulty difficulty, Boolean isCommon, String q) {
        Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir, IQ_SORT_FIELDS));
        PagedResponse<InterviewQuestionResponse> response =
                interviewQuestionService.list(pageable, difficulty, ContentStatus.PUBLISHED, isCommon, q);
        return ResponseEntity.ok(response);
    }

    @Override
    @Transactional
    public ResponseEntity<InterviewQuestionResponse> getInterviewQuestionBySlug(String slug) {
        contentItemRepository.findBySlug(slug).ifPresent(ci -> contentItemRepository.incrementViewCount(ci.getId()));
        InterviewQuestionResponse response = interviewQuestionService.getBySlug(slug);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PagedResponse<ArticleResponse>> listArticles(
            int page, int size, String sortBy, String sortDir, ContentType type, String q) {
        Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir, ARTICLE_SORT_FIELDS));
        PagedResponse<ArticleResponse> response =
                articleService.list(pageable, type, ContentStatus.PUBLISHED, q);
        return ResponseEntity.ok(response);
    }

    @Override
    @Transactional
    public ResponseEntity<ArticleResponse> getArticleBySlug(String slug) {
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
