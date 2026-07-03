package com.ttg.devknowledgeplatform.service.impl;

import com.ttg.devknowledgeplatform.ai.dto.ContentEmbeddingMetadata;
import com.ttg.devknowledgeplatform.ai.service.ContentIngestionService;
import com.ttg.devknowledgeplatform.common.entity.Article;
import com.ttg.devknowledgeplatform.common.entity.Category;
import com.ttg.devknowledgeplatform.common.entity.ContentItem;
import com.ttg.devknowledgeplatform.common.entity.InterviewQuestion;
import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.ContentType;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.repository.ArticleRepository;
import com.ttg.devknowledgeplatform.repository.ContentItemRepository;
import com.ttg.devknowledgeplatform.repository.InterviewQuestionRepository;
import com.ttg.devknowledgeplatform.service.ContentIndexingService;
import com.ttg.devknowledgeplatform.service.IndexingQualityService;
import com.ttg.devknowledgeplatform.service.QualityVerdict;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

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
    private final IndexingQualityService indexingQualityService;

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
        ContentType type = contentItem.getType();
        switch (type) {
            case INTERVIEW_QUESTION -> ingestInterviewQuestion(contentItem);
            case ARTICLE, BLOG_POST -> ingestArticle(contentItem);
            default -> log.warn("Unsupported content type for indexing: {}", type);
        }
        assessAndRecordQuality(contentItem);
    }

    /**
     * Runs the quality check against the embeddings just stored by {@code ContentIngestionService}
     * and persists the raw score onto the {@code ContentItem}. A {@code null} score means the
     * check was skipped (cold-start — no corpus centroid available yet).
     *
     * <p>The score is stored for admin visibility regardless of whether it is below the threshold.
     * Admins can query {@code WHERE quality_score < :threshold} to review flagged documents.
     *
     * <h3>TODO — Low-quality embeddings are currently retained (not yet implemented)</h3>
     * <p>When {@link QualityVerdict#lowQuality()} is {@code true}, the {@code ContentEmbedding}
     * rows written by {@code ContentIngestionService} are <strong>not</strong> removed. This is
     * intentional during the calibration period — automatically discarding content an admin
     * deliberately published before the threshold is validated is too aggressive.
     *
     * <p>The two consequences of retaining bad embeddings are:
     * <ol>
     *   <li><strong>Corpus centroid drift (primary risk)</strong> — {@code CorpusStatisticsService}
     *       computes the centroid as {@code avg(embedding)} over all rows. Near-random vectors
     *       from corrupted documents pull the centroid away from the true domain centre, silently
     *       degrading {@code QueryAnomalyStage} accuracy for every future request. This effect is
     *       cumulative and invisible from query logs.</li>
     *   <li><strong>False-positive retrieval (low risk)</strong> — near-random embeddings score
     *       poorly against real queries and are largely filtered by {@code ScoringStage} (absolute
     *       threshold) and {@code RetrievalAnomalyStage} (relative outlier removal) before reaching
     *       the LLM. Retrieval damage is limited.</li>
     * </ol>
     *
     * <p><strong>Proposed resolution:</strong> once {@link com.ttg.devknowledgeplatform.ai.config.IndexingConfig#getIndexingCoherenceThreshold()}
     * is validated against real traffic, add an admin endpoint
     * {@code DELETE /api/v1/admin/indexing/content?maxQualityScore=:threshold} that bulk-removes
     * embeddings for flagged documents after human review. Additionally, consider calling
     * {@code contentIngestionService.deleteEmbeddings(contentItem.getId())} here when
     * {@code verdict.lowQuality()} is {@code true} — both calls share the same {@code @Transactional}
     * boundary in {@link ContentIndexingServiceImpl}, so the write-then-delete produces no net
     * commit for bad documents.
     */
    private void assessAndRecordQuality(ContentItem contentItem) {
        QualityVerdict verdict = indexingQualityService.assess(contentItem.getId(), contentItem.getType());

        if (verdict.wasSkipped()) {
            return;
        }

        contentItem.setQualityScore(BigDecimal.valueOf(verdict.score()).setScale(4, RoundingMode.HALF_UP));
        contentItemRepository.save(contentItem);

        if (verdict.lowQuality()) {
            log.warn("Content item id={} title='{}' flagged as low quality (score={})",
                    contentItem.getId(), contentItem.getTitle(), verdict.score());
        }
    }

    private void ingestInterviewQuestion(ContentItem contentItem) {
        InterviewQuestion iq = interviewQuestionRepository
                .findByContentItem_Id(contentItem.getId())
                .orElseThrow(() -> new ResourceNotFoundException("InterviewQuestion for ContentItem",
                        String.valueOf(contentItem.getId())));

        ContentEmbeddingMetadata metadata = buildMetadata(contentItem, iq.getDifficulty().name(), iq.getIsCommon());
        contentIngestionService.ingest(contentItem, buildInterviewQuestionText(contentItem, iq), metadata);
    }

    private void ingestArticle(ContentItem contentItem) {
        Article article = articleRepository
                .findByContentItem_Id(contentItem.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Article for ContentItem",
                        String.valueOf(contentItem.getId())));

        ContentEmbeddingMetadata metadata = buildMetadata(contentItem, null, null);
        contentIngestionService.ingest(contentItem, buildArticleText(contentItem, article), metadata);
    }

    /**
     * Constructs the {@link ContentEmbeddingMetadata} stored on every chunk produced from
     * {@code contentItem}. This is the single source of truth for the JSONB metadata schema.
     *
     * <p>{@code difficulty} and {@code isCommon} are non-null only for interview questions;
     * they are {@code null} for articles and blog posts and will be omitted from the JSON
     * by {@link com.fasterxml.jackson.annotation.JsonInclude.Include#NON_NULL}.
     */
    private ContentEmbeddingMetadata buildMetadata(ContentItem contentItem,
                                                   String difficulty, Boolean isCommon) {
        Category category = contentItem.getCategory();

        List<Integer> tagIds = null;
        List<String> tagNames = null;
        if (contentItem.getContentItemTags() != null && !contentItem.getContentItemTags().isEmpty()) {
            tagIds = contentItem.getContentItemTags().stream()
                    .map(cit -> cit.getTag().getId())
                    .toList();
            tagNames = contentItem.getContentItemTags().stream()
                    .map(cit -> cit.getTag().getName())
                    .toList();
        }

        return new ContentEmbeddingMetadata(
                contentItem.getType().name(),
                contentItem.getStatus().name(),
                contentItem.getTitle(),
                category != null ? category.getId() : null,
                category != null ? category.getName() : null,
                tagIds,
                tagNames,
                difficulty,
                isCommon
        );
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
