package com.ttg.devknowledgeplatform.ai.pipeline;

import com.ttg.devknowledgeplatform.ai.config.EmbeddingProperties;
import com.ttg.devknowledgeplatform.ai.dto.RagPipelineContext;
import com.ttg.devknowledgeplatform.ai.dto.ScoredChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Pipeline stage that removes statistically anomalous chunks from the scored candidate list
 * using a <strong>gap detection</strong> algorithm.
 *
 * <h3>Why a second filter is needed</h3>
 * <p>{@link ScoringStage} applies a global absolute similarity threshold (e.g. 0.75). This
 * catches clearly irrelevant chunks, but cannot catch <em>relative</em> outliers — chunks that
 * barely clear the floor while the rest of the result set scores significantly higher:
 *
 * <pre>
 *   Chunk        Similarity
 *   Spring DI      0.95   ← relevant
 *   Java beans     0.91   ← relevant
 *   Some blog      0.76   ← passes global threshold, but 0.15 below the cluster
 * </pre>
 *
 * <p>The blog chunk passes the 0.75 floor but is a structural outlier relative to the query.
 * Feeding it to the LLM introduces noise that can degrade answer quality.
 *
 * <h3>Algorithm — largest-gap pruning</h3>
 * <p>Input: sorted-descending {@code scoredChunks} from {@link ScoringStage}.
 * <ol>
 *   <li>Scan consecutive (score[i], score[i+1]) pairs and compute the gap {@code score[i] − score[i+1]}.</li>
 *   <li>Find the index of the largest gap.</li>
 *   <li>If that gap exceeds {@code retrieval-outlier-gap-threshold} (default {@code 0.15}),
 *       discard everything below the gap.</li>
 *   <li>Otherwise leave all chunks intact — no gap is structurally significant.</li>
 * </ol>
 *
 * <h3>Safety guard — minimum one chunk</h3>
 * <p>The stage never discards all chunks. If gap detection would reduce the list to zero,
 * the original list is kept. The "no chunks at all" abort path is owned by {@link ScoringStage};
 * duplicating it here would be redundant and could mask bugs.
 *
 * <h3>Gap threshold intuition</h3>
 * <p>A threshold of {@code 0.15} means: "if the score drops by more than 15 percentage points
 * in a single step, treat everything below that step as a different relevance tier."
 * For a well-populated corpus this value is conservative; tune it by logging
 * {@code "Pruned N chunks — max gap was X"} in staging and inspecting real distributions.
 *
 * <p><strong>Reads:</strong> {@link RagPipelineContext#getScoredChunks()} (sorted desc).<br>
 * <strong>Writes:</strong> {@link RagPipelineContext#setScoredChunks(List)} (pruned list).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RetrievalAnomalyStage implements RagPipelineStage {

    private final EmbeddingProperties properties;

    /**
     * Applies largest-gap pruning to the scored chunk list.
     *
     * @param ctx pipeline context; {@code scoredChunks} must be non-null and sorted descending
     */
    @Override
    public void process(RagPipelineContext ctx) {
        List<ScoredChunk> chunks = ctx.getScoredChunks();

        // Need at least two chunks to find a gap.
        if (chunks.size() < 2) {
            return;
        }

        int maxGapIndex = findMaxGapIndex(chunks);
        float maxGap = chunks.get(maxGapIndex).score() - chunks.get(maxGapIndex + 1).score();

        if (maxGap < properties.getRetrievalOutlierGapThreshold()) {
            log.debug("No significant gap found (max gap={}, threshold={}) — keeping all {} chunks",
                    maxGap, properties.getRetrievalOutlierGapThreshold(), chunks.size());
            return;
        }

        // Everything after maxGapIndex is below the structural break — discard it.
        List<ScoredChunk> pruned = chunks.subList(0, maxGapIndex + 1);

        // Safety: never prune to zero (ScoringStage owns the "no chunks" abort path).
        if (pruned.isEmpty()) {
            log.warn("Gap pruning would have removed all chunks — keeping original list");
            return;
        }

        log.debug("Pruned {} outlier chunk(s) — max gap={} at index {} (scores {} → {})",
                chunks.size() - pruned.size(), maxGap, maxGapIndex,
                chunks.get(maxGapIndex).score(), chunks.get(maxGapIndex + 1).score());

        ctx.setScoredChunks(pruned);
    }

    /**
     * Returns the index {@code i} where the gap {@code score[i] − score[i+1]} is largest.
     * The caller is responsible for ensuring the list has at least two elements.
     */
    private int findMaxGapIndex(List<ScoredChunk> chunks) {
        int maxIndex = 0;
        float maxGap = 0f;
        for (int i = 0; i < chunks.size() - 1; i++) {
            float gap = chunks.get(i).score() - chunks.get(i + 1).score();
            if (gap > maxGap) {
                maxGap = gap;
                maxIndex = i;
            }
        }
        return maxIndex;
    }
}
