package com.ttg.devknowledgeplatform.ai.pipeline;

import com.ttg.devknowledgeplatform.ai.config.EmbeddingProperties;
import com.ttg.devknowledgeplatform.ai.dto.RagPipelineContext;
import com.ttg.devknowledgeplatform.ai.service.CorpusStatisticsService;
import com.ttg.devknowledgeplatform.ai.utils.VectorUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Pipeline stage that detects queries outside the platform's knowledge domain by comparing
 * the query embedding against the L2-normalised corpus centroid via cosine similarity.
 *
 * <h3>Why centroid similarity works</h3>
 * <p>Every indexed document was embedded in the same vector space. Their centroid (normalised
 * average) approximates the "centre of mass" of the platform's knowledge domain. A query
 * embedding that lands far from this centre is statistically unlikely to match any chunk well —
 * detecting it early avoids unnecessary retrieval and LLM calls, and prevents the model from
 * hallucinating answers about topics the knowledge base does not cover.
 *
 * <h3>Two-tier anomaly handling</h3>
 * <ul>
 *   <li><strong>Hard anomaly</strong> (similarity &lt; {@code anomaly-hard-threshold}, default 0.20) —
 *       the query is clearly outside the software engineering domain. The pipeline is aborted
 *       immediately with a user-facing out-of-scope message.</li>
 *   <li><strong>Soft anomaly</strong> (similarity in [{@code anomaly-hard-threshold},
 *       {@code anomaly-soft-threshold}), default [0.20, 0.40)) — the query is borderline.
 *       The pipeline continues, but a stricter per-request similarity threshold
 *       ({@code anomaly-soft-similarity-threshold}, default 0.82) is applied during scoring.
 *       This reduces the chance of hallucination by requiring retrieved chunks to be a tighter
 *       match before they enter the LLM context.</li>
 * </ul>
 *
 * <h3>Graceful degradation</h3>
 * <p>If no centroid is cached (e.g. the corpus is empty or {@code CorpusStatisticsService}
 * has not yet run its first refresh), the check is skipped and the query proceeds normally.
 * This avoids blocking legitimate requests on a cold-start system.
 *
 * <h3>TODO — Case 2: Centroid drift detection (not yet implemented)</h3>
 * <p>The centroid used here is refreshed on a fixed schedule (default 6 h via
 * {@code app.ai.embedding.centroid-refresh-interval}). After a large content import the centroid
 * shifts immediately, but this stage stays blind to the change until the next scheduled refresh —
 * potentially misclassifying queries about newly indexed topics as soft anomalies.
 *
 * <p><strong>Proposed solution — Spring {@code ApplicationEventPublisher} + {@code @TransactionalEventListener}:</strong>
 * <ol>
 *   <li>Introduce a {@code ContentIndexedEvent} published by {@code ContentIndexingServiceImpl}
 *       after embeddings are persisted.</li>
 *   <li>{@code CorpusStatisticsServiceImpl} listens with
 *       {@code @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)} — the
 *       AFTER_COMMIT phase is critical: it ensures the new embedding rows are visible to the
 *       SQL {@code avg(embedding)} centroid query before refresh runs. A plain
 *       {@code @EventListener} would fire mid-transaction and read uncommitted rows.</li>
 *   <li>Before refreshing, compare {@code SysParam.computedAt} (the centroid's age) against
 *       a new {@code ContentEmbeddingRepository.findMaxDteCreation()} value. Only refresh when
 *       the lag exceeds a configurable drift threshold (e.g. {@code centroid-drift-threshold: PT1H}).
 *       This prevents a refresh storm when hundreds of articles are imported in a loop — only the
 *       first event that finds the centroid stale triggers a refresh; subsequent events see a fresh
 *       {@code computedAt} and skip.</li>
 * </ol>
 *
 * <p><strong>Files to add/modify:</strong>
 * <ul>
 *   <li>{@code common/event/ContentIndexedEvent.java} — new Spring event record
 *       (fields: {@code contentItemId}, {@code sourceType})</li>
 *   <li>{@code ContentIndexingServiceImpl} — inject {@code ApplicationEventPublisher};
 *       publish {@code ContentIndexedEvent} after {@code embeddingService.store(...)}</li>
 *   <li>{@code EmbeddingProperties} — new {@code centroidDriftThreshold} field (ISO-8601 duration,
 *       default {@code PT1H}); bound to {@code app.ai.embedding.centroid-drift-threshold}</li>
 *   <li>{@code ContentEmbeddingRepository} — new {@code findMaxDteCreation()} native query</li>
 *   <li>{@code CorpusStatisticsServiceImpl} — add {@code @TransactionalEventListener} method;
 *       staleness guard before delegating to {@code refresh()}</li>
 *   <li>{@code application.yml} — {@code centroid-drift-threshold: PT1H}</li>
 * </ul>
 *
 * <p><strong>Reads:</strong> {@link RagPipelineContext#getQueryEmbedding()},
 * {@link RagPipelineContext#getFilter()}.<br>
 * <strong>Writes:</strong> {@link RagPipelineContext#setEffectiveSimilarityThreshold(Float)}
 * (soft anomaly only); calls {@link RagPipelineContext#abort(String)} on hard anomaly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QueryAnomalyStage implements RagPipelineStage {

    private final CorpusStatisticsService corpusStatisticsService;
    private final EmbeddingProperties properties;

    /**
     * Computes the cosine similarity between the query embedding and the corpus centroid,
     * then applies the two-tier anomaly policy described in the class Javadoc.
     *
     * @param ctx pipeline context; must contain a non-null {@code queryEmbedding}
     */
    @Override
    public void process(RagPipelineContext ctx) {
        float[] centroid = corpusStatisticsService.getCentroidFor(ctx.getFilter()).orElse(null);

        if (centroid == null) {
            log.debug("No corpus centroid available — skipping anomaly check");
            return;
        }

        // Both query embedding and centroid are L2-normalised unit vectors, so dot product == cosine similarity.
        float similarity = VectorUtils.dotProduct(ctx.getQueryEmbedding(), centroid);

        log.debug("Query-to-centroid cosine similarity: {}", similarity);

        if (similarity < properties.getAnomalyHardThreshold()) {
            log.warn("Hard anomaly detected — similarity={} below hard threshold={}; aborting pipeline",
                    similarity, properties.getAnomalyHardThreshold());
            ctx.abort(properties.getOutOfScopeAnswer());
            return;
        }

        if (similarity < properties.getAnomalySoftThreshold()) {
            log.warn("Soft anomaly detected — similarity={} below soft threshold={}; " +
                     "applying stricter retrieval threshold={}",
                    similarity, properties.getAnomalySoftThreshold(),
                    properties.getAnomalySoftSimilarityThreshold());
            ctx.setEffectiveSimilarityThreshold(properties.getAnomalySoftSimilarityThreshold());
        }
    }
}
