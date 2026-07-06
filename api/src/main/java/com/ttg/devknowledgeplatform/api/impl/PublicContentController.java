package com.ttg.devknowledgeplatform.api.impl;

import com.ttg.devknowledgeplatform.api.PublicContentApi;
import com.ttg.devknowledgeplatform.content.enums.ContentStatus;
import com.ttg.devknowledgeplatform.content.enums.ContentType;
import com.ttg.devknowledgeplatform.content.enums.QuestionDifficulty;
import com.ttg.devknowledgeplatform.content.service.ArticleService;
import com.ttg.devknowledgeplatform.content.service.QuestionAnswerService;
import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.content.ArticleResponse;
import com.ttg.devknowledgeplatform.dto.content.QuestionAnswerResponse;
import com.ttg.devknowledgeplatform.content.repository.ContentItemRepository;
import com.ttg.devknowledgeplatform.mapper.ArticleMapper;
import com.ttg.devknowledgeplatform.mapper.QuestionAnswerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
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

    private static final Set<String> QA_SORT_FIELDS = Set.of("id", "dteCreation", "difficulty");
    private static final Set<String> ARTICLE_SORT_FIELDS = Set.of("id", "dteCreation");

    private final QuestionAnswerService questionAnswerService;
    private final QuestionAnswerMapper questionAnswerMapper;
    private final ArticleService articleService;
    private final ArticleMapper articleMapper;
    private final ContentItemRepository contentItemRepository;

    @Override
    public ResponseEntity<PagedResponse<QuestionAnswerResponse>> listQuestionAnswers(
            int page, int size, String sortBy, String sortDir,
            QuestionDifficulty difficulty, Boolean isCommon, String q) {
        Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir, QA_SORT_FIELDS));
        Page<QuestionAnswerResponse> responses =
                questionAnswerService.list(pageable, difficulty, ContentStatus.PUBLISHED, isCommon, q)
                        .map(questionAnswerMapper::toResponse);
        return ResponseEntity.ok(PagedResponse.from(responses));
    }

    @Override
    @Transactional
    public ResponseEntity<QuestionAnswerResponse> getQuestionAnswerBySlug(String slug) {
        contentItemRepository.findBySlug(slug).ifPresent(ci -> contentItemRepository.incrementViewCount(ci.getId()));
        QuestionAnswerResponse response = questionAnswerMapper.toResponse(questionAnswerService.getBySlug(slug));
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PagedResponse<ArticleResponse>> listArticles(
            int page, int size, String sortBy, String sortDir, ContentType type, String q) {
        Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir, ARTICLE_SORT_FIELDS));
        Page<ArticleResponse> responses =
                articleService.list(pageable, type, ContentStatus.PUBLISHED, q)
                        .map(articleMapper::toResponse);
        return ResponseEntity.ok(PagedResponse.from(responses));
    }

    @Override
    @Transactional
    public ResponseEntity<ArticleResponse> getArticleBySlug(String slug) {
        contentItemRepository.findBySlug(slug).ifPresent(ci -> contentItemRepository.incrementViewCount(ci.getId()));
        ArticleResponse response = articleMapper.toResponse(articleService.getBySlug(slug));
        return ResponseEntity.ok(response);
    }

    private Sort buildSort(String sortBy, String sortDir, Set<String> allowed) {
        String field = allowed.contains(sortBy) ? sortBy : "id";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }
}
