package com.ttg.devknowledgeplatform.ai.service.impl;

import com.ttg.devknowledgeplatform.ai.dto.ContentEmbeddingMetadata;
import com.ttg.devknowledgeplatform.ai.dto.client.ContentItemDto;
import com.ttg.devknowledgeplatform.ai.service.ContentIndexingService;
import com.ttg.devknowledgeplatform.ai.service.ContentIngestionService;
import com.ttg.devknowledgeplatform.ai.service.ContentServiceClient;
import com.ttg.devknowledgeplatform.ai.service.IndexingQualityService;
import com.ttg.devknowledgeplatform.ai.service.QualityVerdict;
import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.ContentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Implementation of {@link ContentIndexingService}.
 *
 * <p>Reads/writes content-item data exclusively through {@link ContentServiceClient} (an HTTP
 * call to {@code content-service}'s internal indexing API) rather than injecting that module's
 * repositories directly — see root {@code CLAUDE.md}'s Long-term direction section for why.
 * {@link ContentItemDto} already carries every field this class needs (category/tag names, and the
 * flattened question/article subtype fields), so — unlike the pre-HTTP version — there is no
 * separate fetch for {@code QuestionAnswer}/{@code Article}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Throwable.class)
public class ContentIndexingServiceImpl implements ContentIndexingService {

    private static final String SYSTEM_PRINCIPAL = "system";

    private final ContentServiceClient contentServiceClient;
    private final ContentIngestionService contentIngestionService;
    private final IndexingQualityService indexingQualityService;

    @Override
    public void index(Integer contentItemId) {
        ensureSecurityContext();
        ContentItemDto contentItem = contentServiceClient.getById(contentItemId);
        ingestContentItem(contentItem);
    }

    @Override
    public void indexAll() {
        ensureSecurityContext();
        List<ContentItemDto> published = contentServiceClient.listByStatus(ContentStatus.PUBLISHED);
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

    private void ingestContentItem(ContentItemDto contentItem) {
        ContentType type = contentItem.getType();
        switch (type) {
            case QUESTION_ANSWER -> ingestQuestionAnswer(contentItem);
            case ARTICLE, BLOG_POST -> ingestArticle(contentItem);
            default -> log.warn("Unsupported content type for indexing: {}", type);
        }
        assessAndRecordQuality(contentItem);
    }

    /**
     * Runs the quality check against the embeddings just stored by {@code ContentIngestionService}
     * and persists the raw score onto {@code content-service}'s {@code ContentItem} row via
     * {@link ContentServiceClient#updateQualityScore}. A {@code null} score means the check was
     * skipped (cold-start — no corpus centroid available yet).
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
     * {@code verdict.lowQuality()} is {@code true}.
     */
    private void assessAndRecordQuality(ContentItemDto contentItem) {
        QualityVerdict verdict = indexingQualityService.assess(contentItem.getId(), contentItem.getType());

        if (verdict.wasSkipped()) {
            return;
        }

        BigDecimal score = BigDecimal.valueOf(verdict.score()).setScale(4, RoundingMode.HALF_UP);
        contentServiceClient.updateQualityScore(contentItem.getId(), score);

        if (verdict.lowQuality()) {
            log.warn("Content item id={} title='{}' flagged as low quality (score={})",
                    contentItem.getId(), contentItem.getTitle(), verdict.score());
        }
    }

    private void ingestQuestionAnswer(ContentItemDto contentItem) {
        String difficulty = contentItem.getDifficulty() != null ? contentItem.getDifficulty().name() : null;
        ContentEmbeddingMetadata metadata = buildMetadata(contentItem, difficulty, contentItem.getIsCommon());
        contentIngestionService.ingest(
                contentItem.getId(), contentItem.getType(), buildQuestionAnswerText(contentItem), metadata);
    }

    private void ingestArticle(ContentItemDto contentItem) {
        ContentEmbeddingMetadata metadata = buildMetadata(contentItem, null, null);
        contentIngestionService.ingest(
                contentItem.getId(), contentItem.getType(), buildArticleText(contentItem), metadata);
    }

    /**
     * Constructs the {@link ContentEmbeddingMetadata} stored on every chunk produced from
     * {@code contentItem}. This is the single source of truth for the JSONB metadata schema.
     *
     * <p>{@code difficulty} and {@code isCommon} are non-null only when a {@code QuestionAnswer}
     * genuinely has interview-specific framing; they are {@code null} for general-knowledge
     * questions, articles, and blog posts, and will be omitted from the JSON
     * by {@link com.fasterxml.jackson.annotation.JsonInclude.Include#NON_NULL}.
     */
    private ContentEmbeddingMetadata buildMetadata(ContentItemDto contentItem,
                                                   String difficulty, Boolean isCommon) {
        return new ContentEmbeddingMetadata(
                contentItem.getType().name(),
                contentItem.getStatus().name(),
                contentItem.getTitle(),
                contentItem.getCategoryId(),
                contentItem.getCategoryName(),
                contentItem.getTagIds(),
                contentItem.getTagNames(),
                difficulty,
                isCommon
        );
    }

    private String buildQuestionAnswerText(ContentItemDto contentItem) {
        StringBuilder sb = new StringBuilder();
        sb.append(contentItem.getTitle()).append("\n\n");
        sb.append(contentItem.getQuestionBody());
        if (contentItem.getShortAnswer() != null && !contentItem.getShortAnswer().isBlank()) {
            sb.append("\n\nShort Answer:\n").append(contentItem.getShortAnswer());
        }
        if (contentItem.getDetailedAnswer() != null && !contentItem.getDetailedAnswer().isBlank()) {
            sb.append("\n\nDetailed Answer:\n").append(contentItem.getDetailedAnswer());
        }
        return sb.toString();
    }

    private String buildArticleText(ContentItemDto contentItem) {
        StringBuilder sb = new StringBuilder();
        sb.append(contentItem.getTitle()).append("\n\n");
        if (contentItem.getBody() != null && !contentItem.getBody().isBlank()) {
            sb.append(contentItem.getBody());
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
