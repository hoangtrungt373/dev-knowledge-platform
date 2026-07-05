package com.ttg.devknowledgeplatform.service.impl;

import com.ttg.devknowledgeplatform.ai.dto.RagFilter;
import com.ttg.devknowledgeplatform.ai.repository.ContentEmbeddingRepository;
import com.ttg.devknowledgeplatform.ai.service.CorpusStatisticsService;
import com.ttg.devknowledgeplatform.ai.utils.VectorUtils;
import com.ttg.devknowledgeplatform.common.enums.ContentType;
import com.ttg.devknowledgeplatform.common.enums.ParamKey;
import com.ttg.devknowledgeplatform.common.service.SysParamService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Default {@link CorpusStatisticsService} implementation.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li><strong>Startup ({@code @PostConstruct})</strong> — loads persisted centroids from
 *       {@code SYS_PARAM} into the in-memory cache. Fast: one SELECT per key, no recomputation.</li>
 *   <li><strong>Schedule ({@code @Scheduled})</strong> — recomputes centroids via a single
 *       SQL {@code avg()} per content type, persists the results, then refreshes the cache.</li>
 * </ol>
 *
 * <h3>Thread safety</h3>
 * <p>Each centroid field is declared {@code volatile}. The scheduler (single writer) assigns
 * a new array reference atomically; pipeline threads (many readers) always see the latest
 * published reference without synchronisation overhead. Mutation of the array contents never
 * occurs after assignment, so no further locking is needed.
 *
 * <h3>Upsert pattern</h3>
 * <p>Persistence (find-or-create, stamp {@code computedAt}, save) is delegated to
 * {@link SysParamService#upsert}, shared with other {@code SYS_PARAM}-backed caches such as
 * {@code PromptGuardStage}'s injection-prototype embedding cache — this class only owns the
 * centroid computation and in-memory cache, not the persistence mechanics.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CorpusStatisticsServiceImpl implements CorpusStatisticsService {

    private final ContentEmbeddingRepository embeddingRepository;
    private final SysParamService sysParamService;

    // ── In-memory cache ─────────────────────────────────────────────────────────
    // One writer (scheduler), many readers (pipeline threads) — volatile is sufficient.

    private volatile float[] centroidAll;
    private volatile float[] centroidArticle;
    private volatile float[] centroidQuestionAnswer;
    private volatile float[] centroidBlogPost;

    // ── Startup ──────────────────────────────────────────────────────────────────

    /**
     * Loads previously computed centroids from {@code SYS_PARAM} at application startup.
     * Avoids recomputation on every restart — the scheduled refresh will pick up any
     * content indexed since the last persisted value.
     */
    @PostConstruct
    public void loadFromDb() {
        centroidAll             = loadCached(ParamKey.CENTROID_ALL);
        centroidArticle         = loadCached(ParamKey.CENTROID_ARTICLE);
        centroidQuestionAnswer  = loadCached(ParamKey.CENTROID_QUESTION_ANSWER);
        centroidBlogPost        = loadCached(ParamKey.CENTROID_BLOG_POST);
        log.info("Corpus centroids loaded from SYS_PARAM: {}/4 available", countLoaded());
    }

    // ── Scheduled refresh ────────────────────────────────────────────────────────

    /**
     * Recomputes all corpus centroids on a configurable schedule.
     * Each centroid is a single SQL {@code avg(embedding)} aggregation — fast even for
     * large corpora because the work is done inside PostgreSQL with SIMD-accelerated pgvector.
     */
    @Scheduled(fixedDelayString = "${app.ai.indexing.centroid-refresh-interval:PT6H}")
    @Override
    public void refresh() {
        log.info("Refreshing corpus centroids...");

        computeAndPersist(ParamKey.CENTROID_ALL,
                embeddingRepository.computeGlobalCentroid());
        computeAndPersist(ParamKey.CENTROID_ARTICLE,
                embeddingRepository.computeCentroidBySourceType(ContentType.ARTICLE.name()));
        computeAndPersist(ParamKey.CENTROID_QUESTION_ANSWER,
                embeddingRepository.computeCentroidBySourceType(ContentType.QUESTION_ANSWER.name()));
        computeAndPersist(ParamKey.CENTROID_BLOG_POST,
                embeddingRepository.computeCentroidBySourceType(ContentType.BLOG_POST.name()));

        centroidAll             = loadCached(ParamKey.CENTROID_ALL);
        centroidArticle         = loadCached(ParamKey.CENTROID_ARTICLE);
        centroidQuestionAnswer  = loadCached(ParamKey.CENTROID_QUESTION_ANSWER);
        centroidBlogPost        = loadCached(ParamKey.CENTROID_BLOG_POST);

        log.info("Corpus centroids refreshed: {}/4 available", countLoaded());
    }

    // ── Public API ───────────────────────────────────────────────────────────────

    @Override
    public Optional<float[]> getCentroidFor(RagFilter filter) {
        return Optional.ofNullable(resolveCentroid(filter));
    }

    // ── Private helpers ──────────────────────────────────────────────────────────

    /**
     * Resolves the most specific centroid for the given filter.
     * A single-type filter returns that type's centroid; mixed or no filter returns the global one.
     */
    private float[] resolveCentroid(RagFilter filter) {
        if (filter.sourceTypes() != null && filter.sourceTypes().size() == 1) {
            return switch (filter.sourceTypes().iterator().next()) {
                case ARTICLE          -> centroidArticle;
                case QUESTION_ANSWER  -> centroidQuestionAnswer;
                case BLOG_POST        -> centroidBlogPost;
            };
        }
        return centroidAll;
    }

    /**
     * Persists a newly computed centroid vector to {@code SYS_PARAM}.
     * Silently skips if {@code vectorText} is null (no embeddings for that content type).
     */
    private void computeAndPersist(ParamKey key, String vectorText) {
        if (vectorText == null) {
            log.debug("Skipping {} — no embeddings found for this content type", key);
            return;
        }
        sysParamService.upsert(key, vectorText);
        log.debug("Persisted {}", key);
    }

    /**
     * Loads a single centroid from {@code SYS_PARAM} and parses it into a {@code float[]}.
     * Returns {@code null} if the row does not exist yet.
     */
    private float[] loadCached(ParamKey key) {
        return sysParamService.getValue(key)
                .map(value -> VectorUtils.normalize(VectorUtils.parseVector(value)))
                .orElse(null);
    }

    private int countLoaded() {
        int count = 0;
        if (centroidAll != null)              count++;
        if (centroidArticle != null)          count++;
        if (centroidQuestionAnswer != null)   count++;
        if (centroidBlogPost != null)         count++;
        return count;
    }
}
