package com.ttg.devknowledgeplatform.service.impl;

import com.ttg.devknowledgeplatform.ai.config.IndexingConfig;
import com.ttg.devknowledgeplatform.ai.dto.RagFilter;
import com.ttg.devknowledgeplatform.ai.entity.ContentEmbedding;
import com.ttg.devknowledgeplatform.ai.repository.ContentEmbeddingRepository;
import com.ttg.devknowledgeplatform.ai.service.CorpusStatisticsService;
import com.ttg.devknowledgeplatform.ai.utils.VectorUtils;
import com.ttg.devknowledgeplatform.common.enums.ContentType;
import com.ttg.devknowledgeplatform.service.IndexingQualityService;
import com.ttg.devknowledgeplatform.service.QualityVerdict;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Default {@link IndexingQualityService} implementation.
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>Load all {@link ContentEmbedding} rows for the given {@code contentItemId}
 *       (just stored by {@code ContentIngestionServiceImpl}).</li>
 *   <li>Resolve the most specific corpus centroid via {@link CorpusStatisticsService#getCentroidFor}.
 *       A single-type filter yields the type-specific centroid; if unavailable it falls back to
 *       the global centroid. If neither is cached, returns {@link QualityVerdict#skipped()}.</li>
 *   <li>Compute the mean cosine similarity across all chunk embeddings.
 *       Both embeddings and the centroid are L2-normalised, so dot product equals cosine similarity.</li>
 *   <li>Compare the mean against {@code app.ai.indexing.indexing-coherence-threshold}.
 *       Below threshold → {@link QualityVerdict#flag(float)};
 *       at or above → {@link QualityVerdict#pass(float)}.</li>
 * </ol>
 *
 * <h3>Why type-specific centroid</h3>
 * <p>Articles, questions, and blog posts occupy overlapping but distinct regions of
 * the embedding space. An article about Java garbage collection should be compared against the
 * article centroid rather than the global one — this gives a more accurate signal and reduces
 * false positives for documents that are legitimately in a niche sub-domain.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class IndexingQualityServiceImpl implements IndexingQualityService {

    private final ContentEmbeddingRepository embeddingRepository;
    private final CorpusStatisticsService corpusStatisticsService;
    private final IndexingConfig indexing;

    @Override
    public QualityVerdict assess(Integer contentItemId, ContentType contentType) {
        List<ContentEmbedding> embeddings = embeddingRepository.findByContentItem_Id(contentItemId);

        if (embeddings.isEmpty()) {
            log.debug("No embeddings found for content item id={} — skipping quality check", contentItemId);
            return QualityVerdict.skipped();
        }

        RagFilter filter = new RagFilter(Set.of(contentType), null, null);
        float[] centroid = corpusStatisticsService.getCentroidFor(filter).orElse(null);

        if (centroid == null) {
            log.debug("No corpus centroid available — skipping quality check for content item id={}", contentItemId);
            return QualityVerdict.skipped();
        }

        float meanSimilarity = computeMeanCentroidSimilarity(embeddings, centroid);
        float threshold = indexing.getIndexingCoherenceThreshold();

        if (meanSimilarity < threshold) {
            log.warn("Low-quality document detected — content item id={} type={} meanSimilarity={} threshold={}",
                    contentItemId, contentType, meanSimilarity, threshold);
            return QualityVerdict.flag(meanSimilarity);
        }

        log.debug("Quality check passed — content item id={} type={} meanSimilarity={}",
                contentItemId, contentType, meanSimilarity);
        return QualityVerdict.pass(meanSimilarity);
    }

    /**
     * Computes the arithmetic mean of cosine similarities between each chunk embedding
     * and the corpus centroid. Both vectors are L2-normalised, so dot product == cosine similarity.
     */
    private float computeMeanCentroidSimilarity(List<ContentEmbedding> embeddings, float[] centroid) {
        float sum = 0f;
        for (ContentEmbedding ce : embeddings) {
            sum += VectorUtils.dotProduct(ce.getEmbedding(), centroid);
        }
        return sum / embeddings.size();
    }
}
