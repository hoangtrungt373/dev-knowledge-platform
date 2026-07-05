package com.ttg.devknowledgeplatform.ai.service.impl;

import com.ttg.devknowledgeplatform.ai.config.GuardConfig;
import com.ttg.devknowledgeplatform.ai.dto.AnswerQualityVerdict;
import com.ttg.devknowledgeplatform.ai.dto.EmbedResult;
import com.ttg.devknowledgeplatform.ai.dto.RagPipelineContext;
import com.ttg.devknowledgeplatform.ai.dto.ScoredChunk;
import com.ttg.devknowledgeplatform.ai.service.AnswerQualityService;
import com.ttg.devknowledgeplatform.ai.service.EmbeddingService;
import com.ttg.devknowledgeplatform.ai.utils.VectorUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Default {@link AnswerQualityService} implementation.
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>Guard: return {@link AnswerQualityVerdict#skipped()} if there are no selected chunks
 *       or no query embedding (pipeline aborted before retrieval or embedding).</li>
 *   <li>Embed the generated answer via {@link EmbeddingService#embed} — one API call.</li>
 *   <li>Compute the <em>context centroid</em>: arithmetic mean of all MMR-selected chunk
 *       embeddings, then L2-normalise. The mean of L2-normalised vectors is not itself
 *       L2-normalised (same issue as the corpus centroid in {@code CorpusStatisticsService}),
 *       so {@link VectorUtils#normalize} is applied before the dot product.</li>
 *   <li>Compute {@code contextSimilarity = dotProduct(answerEmbedding, contextCentroid)}.</li>
 *   <li>Compute {@code querySimilarity = dotProduct(answerEmbedding, queryEmbedding)}.
 *       The query embedding is already stored in the pipeline context — no extra API call.</li>
 *   <li>Set {@code drifted = true} if either score falls below its configured threshold.</li>
 * </ol>
 *
 * <h3>Why two checks are better than one</h3>
 * <p>Context similarity alone catches hallucination (answer not grounded in retrieved chunks)
 * but misses the case where the LLM answers a different question using on-topic material —
 * for example, rephrasing the context into an answer about a subtly different concept.
 * Query similarity catches that case independently. Together they guard both dimensions of
 * answer quality.
 *
 * <h3>Cost and latency</h3>
 * <p>One embedding API call is made per request (to embed the answer text). The context
 * centroid and query similarity use embeddings already in memory. In the streaming path this
 * call happens inside {@code StreamingResponseHandler.onComplete}, after all tokens have been
 * sent — the only observable effect is a small delay (~100–200 ms) before the {@code done}
 * SSE event is emitted.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Throwable.class)
public class AnswerQualityServiceImpl implements AnswerQualityService {

    private final EmbeddingService embeddingService;
    private final GuardConfig guards;

    @Override
    public AnswerQualityVerdict assess(String answer, RagPipelineContext pipelineCtx) {
        List<ScoredChunk> selected = pipelineCtx.getSelectedChunks();
        float[] queryEmbedding = pipelineCtx.getQueryEmbedding();

        if (selected == null || selected.isEmpty() || queryEmbedding == null) {
            log.debug("AnswerQualityService: skipping check — no selected chunks or query embedding available");
            return AnswerQualityVerdict.skipped();
        }

        EmbedResult answerEmbedResult = embeddingService.embed(answer);
        float[] answerEmbedding = answerEmbedResult.vector();
        pipelineCtx.setQualityEmbeddingTokens(answerEmbedResult.tokenCount());
        float[] contextCentroid = computeNormalizedContextCentroid(selected);

        float contextSimilarity = VectorUtils.dotProduct(answerEmbedding, contextCentroid);
        float querySimilarity   = VectorUtils.dotProduct(answerEmbedding, queryEmbedding);

        boolean contextDrift = contextSimilarity < guards.getAnswerContextSimilarityThreshold();
        boolean queryDrift   = querySimilarity   < guards.getAnswerQuerySimilarityThreshold();
        boolean drifted      = contextDrift || queryDrift;

        if (drifted) {
            log.warn("AnswerQualityService: drift detected — contextSimilarity={} (threshold={}) "
                            + "querySimilarity={} (threshold={}) contextDrift={} queryDrift={}",
                    contextSimilarity, guards.getAnswerContextSimilarityThreshold(),
                    querySimilarity,   guards.getAnswerQuerySimilarityThreshold(),
                    contextDrift, queryDrift);
        } else {
            log.debug("AnswerQualityService: answer grounded — contextSimilarity={} querySimilarity={}",
                    contextSimilarity, querySimilarity);
        }

        return new AnswerQualityVerdict(drifted, contextSimilarity, querySimilarity);
    }

    /**
     * Computes the L2-normalised centroid of the MMR-selected chunk embeddings.
     *
     * <p>The chunk embeddings are L2-normalised individually (OpenAI guarantees unit vectors),
     * but their arithmetic mean is not — the mean of unit vectors points in the right direction
     * but has a norm less than 1. {@link VectorUtils#normalize} restores the unit-length property
     * so that the subsequent {@link VectorUtils#dotProduct} equals cosine similarity.
     *
     * @param chunks the MMR-selected chunks whose embeddings define the context
     * @return a unit-length centroid vector representing the collective direction of the context
     */
    private float[] computeNormalizedContextCentroid(List<ScoredChunk> chunks) {
        int dimensions = chunks.get(0).chunk().getEmbedding().length;
        float[] centroid = new float[dimensions];
        for (ScoredChunk sc : chunks) {
            float[] embedding = sc.chunk().getEmbedding();
            for (int i = 0; i < dimensions; i++) {
                centroid[i] += embedding[i];
            }
        }
        for (int i = 0; i < dimensions; i++) {
            centroid[i] /= chunks.size();
        }
        return VectorUtils.normalize(centroid);
    }
}
