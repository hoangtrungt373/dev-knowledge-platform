package com.ttg.devknowledgeplatform.service.impl;

import com.ttg.devknowledgeplatform.ai.service.ContentIngestionService;
import com.ttg.devknowledgeplatform.common.entity.Article;
import com.ttg.devknowledgeplatform.common.entity.ContentItem;
import com.ttg.devknowledgeplatform.common.entity.InterviewQuestion;
import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.ContentType;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.repository.ArticleRepository;
import com.ttg.devknowledgeplatform.repository.ContentItemRepository;
import com.ttg.devknowledgeplatform.repository.InterviewQuestionRepository;
import com.ttg.devknowledgeplatform.service.ContentIndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ContentIndexingServiceImpl implements ContentIndexingService {

    private static final String SYSTEM_PRINCIPAL = "system";

    private final ContentItemRepository contentItemRepository;
    private final InterviewQuestionRepository interviewQuestionRepository;
    private final ArticleRepository articleRepository;
    private final ContentIngestionService contentIngestionService;

    @Override
    public void index(Integer contentItemId) {
        ensureSecurityContext();
        ContentItem contentItem = contentItemRepository.findById(contentItemId)
                .orElseThrow(() -> new ResourceNotFoundException(ContentItem.class.getName(), String.valueOf(contentItemId)));
        ingestContentItem(contentItem);
    }

    @Override
    public void indexAll() {
        ensureSecurityContext();
        List<ContentItem> published = contentItemRepository.findByStatus(ContentStatus.PUBLISHED);
        log.info("Bulk indexing {} published content items", published.size());
        published.forEach(this::ingestContentItem);
    }

    @Override
    public void reindex(Integer contentItemId) {
        index(contentItemId);
    }

    @Override
    public void deleteIndex(Integer contentItemId) {
        ensureSecurityContext();
        contentIngestionService.deleteEmbeddings(contentItemId);
    }

    private void ingestContentItem(ContentItem contentItem) {
        // tags apply to all content types — extract once here
        Map<String, Object> commonExtra = buildTagMetadata(contentItem);

        ContentType type = contentItem.getType();
        switch (type) {
            case INTERVIEW_QUESTION -> ingestInterviewQuestion(contentItem, commonExtra);
            case ARTICLE, BLOG_POST -> ingestArticle(contentItem, commonExtra);
            default -> log.warn("Unsupported content type for indexing: {}", type);
        }
    }

    private void ingestInterviewQuestion(ContentItem contentItem, Map<String, Object> commonExtra) {
        InterviewQuestion iq = interviewQuestionRepository
                .findByContentItem_Id(contentItem.getId())
                .orElseThrow(() -> new ResourceNotFoundException("InterviewQuestion for ContentItem", String.valueOf(contentItem.getId())));

        String fullText = buildInterviewQuestionText(contentItem, iq);

        Map<String, Object> extra = new HashMap<>(commonExtra);
        extra.put("difficulty", iq.getDifficulty().name());
        extra.put("isCommon", iq.getIsCommon());

        contentIngestionService.ingest(contentItem, fullText, extra);
    }

    private void ingestArticle(ContentItem contentItem, Map<String, Object> commonExtra) {
        Article article = articleRepository
                .findByContentItem_Id(contentItem.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Article for ContentItem", String.valueOf(contentItem.getId())));

        String fullText = buildArticleText(contentItem, article);
        contentIngestionService.ingest(contentItem, fullText, commonExtra);
    }

    private Map<String, Object> buildTagMetadata(ContentItem contentItem) {
        Map<String, Object> meta = new HashMap<>();
        if (contentItem.getContentItemTags() == null || contentItem.getContentItemTags().isEmpty()) {
            return meta;
        }
        List<Integer> tagIds = contentItem.getContentItemTags().stream()
                .map(cit -> cit.getTag().getId())
                .toList();
        List<String> tagNames = contentItem.getContentItemTags().stream()
                .map(cit -> cit.getTag().getName())
                .toList();
        meta.put("tagIds", tagIds);
        meta.put("tagNames", tagNames);
        return meta;
    }

    private String buildInterviewQuestionText(ContentItem contentItem, InterviewQuestion iq) {
        StringBuilder sb = new StringBuilder();
        sb.append(contentItem.getTitle()).append("\n\n");
        sb.append(iq.getQuestionBody());
        if (iq.getShortAnswer() != null && !iq.getShortAnswer().isBlank()) {
            sb.append("\n\nShort Answer:\n").append(iq.getShortAnswer());
        }
        if (iq.getDetailedAnswer() != null && !iq.getDetailedAnswer().isBlank()) {
            sb.append("\n\nDetailed Answer:\n").append(iq.getDetailedAnswer());
        }
        return sb.toString();
    }

    private String buildArticleText(ContentItem contentItem, Article article) {
        StringBuilder sb = new StringBuilder();
        sb.append(contentItem.getTitle()).append("\n\n");
        if (article.getBody() != null && !article.getBody().isBlank()) {
            sb.append(article.getBody());
        }
        return sb.toString();
    }

    /**
     * Indexing runs in a background thread (@Async) with no HTTP request context.
     * AbstractEntity's @PrePersist reads the Spring Security principal for audit fields —
     * push a synthetic "system" principal so those fields are populated correctly.
     */
    private void ensureSecurityContext() {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(SYSTEM_PRINCIPAL, null, List.of()));
        }
    }
}
