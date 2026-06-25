package com.ttg.devknowledgeplatform.ai.pipeline;

import com.ttg.devknowledgeplatform.ai.config.EmbeddingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Pipeline stage that selects the final {@code topK} chunks using
 * Maximal Marginal Relevance (Carbonell &amp; Goldstein, 1998).
 *
 * <p>MMR scores each unselected chunk as:
 * <pre>
 *   MMR = λ × sim(chunk, query) − (1 − λ) × max sim(chunk, already_selected)
 * </pre>
 * The first term rewards relevance to the query; the second penalises redundancy with chunks
 * already chosen. λ = {@link EmbeddingProperties#getMmrLambda()} controls the trade-off:
 * {@code 1.0} = pure relevance (no diversity), {@code 0.0} = pure diversity (ignores scores).
 *
 * <p>The greedy selection always starts with the highest-relevance chunk (already at index 0
 * after {@link ScoringStage} sorting). Each subsequent pick is the candidate that maximises
 * the MMR score given the chunks already selected. If the scored pool has at most
 * {@code topK} entries, MMR is skipped and all are returned as-is.
 *
 * <p>MMR handles both cross-document and within-document diversity naturally: a second chunk
 * from the same source document is penalised heavily because its embedding is similar to the
 * first selected chunk, while a complementary chunk from the same document (covering a different
 * sub-topic) may still be selected if its MMR score exceeds any single chunk from other documents.
 * This is preferable to a hard per-document cap.
 *
 * <p><strong>Reads:</strong> {@link RagPipelineContext#getScoredChunks()},
 * {@link RagPipelineContext#getQueryEmbedding()}.<br>
 * <strong>Writes:</strong> {@link RagPipelineContext#setSelectedChunks(List)}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MmrStage implements RagPipelineStage {

    private final EmbeddingProperties properties;

    @Override
    public void process(RagPipelineContext ctx) {
        List<ScoredChunk> candidates = ctx.getScoredChunks();
        int topK = properties.getTopK();

        if (candidates.size() <= topK) {
            ctx.setSelectedChunks(candidates);
            log.debug("MMR: {} candidates → {} selected (no pruning needed)",
                    candidates.size(), candidates.size());
            return;
        }

        float lambda = properties.getMmrLambda();
        List<ScoredChunk> selected = new ArrayList<>(topK);
        List<ScoredChunk> remaining = new ArrayList<>(candidates);

        selected.add(remaining.removeFirst());

        while (selected.size() < topK && !remaining.isEmpty()) {
            ScoredChunk best = null;
            float bestMmr = Float.NEGATIVE_INFINITY;

            for (ScoredChunk candidate : remaining) {
                float relevance = candidate.score();
                float maxRedundancy = selected.stream()
                        .map(s -> VectorUtils.dotProduct(
                                candidate.chunk().getEmbedding(), s.chunk().getEmbedding()))
                        .max(Float::compareTo)
                        .orElse(0f);
                float mmrScore = lambda * relevance - (1 - lambda) * maxRedundancy;
                if (mmrScore > bestMmr) {
                    bestMmr = mmrScore;
                    best = candidate;
                }
            }

            selected.add(best);
            remaining.remove(best);
        }

        log.debug("MMR: {} candidates → {} selected (topK={})",
                candidates.size(), selected.size(), topK);
        ctx.setSelectedChunks(selected);
    }
}
