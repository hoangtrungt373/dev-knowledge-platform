package com.ttg.devknowledgeplatform.ai.pipeline;

import com.ttg.devknowledgeplatform.ai.config.EmbeddingProperties;
import com.ttg.devknowledgeplatform.ai.dto.RagPipelineContext;
import com.ttg.devknowledgeplatform.ai.dto.ScoredChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Pipeline stage that prevents hallucination by evaluating the collective quality of the
 * evidence selected by {@link MmrStage} before an LLM call is made.
 *
 * <h3>Why a post-MMR evidence check is needed</h3>
 * <p>{@link ScoringStage} applies an absolute similarity floor (e.g. 0.75). Every surviving
 * chunk has individually passed that floor, but "passing the floor" does not mean the corpus
 * can reliably answer the query. Consider chunks scoring 0.76 / 0.77 / 0.76 — all legal,
 * all marginal. Without this stage, the LLM receives weak context and either hallucinates
 * an answer or produces a vague non-answer — after an expensive API call.
 *
 * <p>This stage detects that situation <em>before</em> the LLM call and aborts with a clear
 * "insufficient information" message, eliminating both the cost and the hallucination risk.
 *
 * <h3>Two-guard composite check</h3>
 * <ul>
 *   <li><strong>Mean score guard</strong> — the arithmetic mean of selected chunk scores must
 *       meet {@code evidence-mean-threshold} (default {@code 0.82}). A mean below this value
 *       indicates the retrieved chunks collectively represent borderline evidence, even if each
 *       individual chunk cleared the absolute floor.</li>
 *   <li><strong>Minimum chunk count guard</strong> — at least {@code evidence-min-chunks}
 *       (default {@code 2}) chunks must have survived MMR selection. A single chunk, regardless
 *       of its score, is too thin a base: one source can cover only one sub-topic of the query,
 *       and the LLM may fill in the gaps by hallucinating.</li>
 * </ul>
 *
 * <p>Either guard failing independently triggers the abort — both conditions must hold for the
 * pipeline to continue. This is the <em>composite specification</em> approach: each guard
 * encodes a distinct necessary condition for answer reliability.
 *
 * <h3>Why this stage sits after MMR, not after ScoringStage</h3>
 * <p>MMR re-ranks and selects the final {@code topK} chunks using a diversity penalty. It can
 * and does pick lower-scoring chunks over higher-scoring ones when diversity demands it.
 * Evaluating evidence quality on the <em>final selected set</em> — what the LLM will actually
 * see — is more accurate than evaluating it on the larger post-scoring candidate pool.
 *
 * <h3>Threshold tuning</h3>
 * <p>{@code evidence-mean-threshold} should be set above {@code similarity-threshold} (the
 * absolute floor) but below the typical mean for well-matched queries. Start at {@code 0.82}
 * and adjust downward if legitimate queries are being rejected, or upward if hallucination
 * persists on borderline queries. Both values are env-var overridable without a redeploy.
 *
 * <p><strong>Reads:</strong> {@link RagPipelineContext#getSelectedChunks()}.<br>
 * <strong>Writes:</strong> calls {@link RagPipelineContext#abort(String)} when either guard fails.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EvidenceQualityStage implements RagPipelineStage {

    private final EmbeddingProperties properties;

    /**
     * Evaluates the mean similarity score and chunk count of the MMR-selected chunks.
     * Aborts the pipeline if either the mean falls below {@code evidenceMeanThreshold}
     * or the count falls below {@code evidenceMinChunks}.
     *
     * @param ctx pipeline context; {@code selectedChunks} must be non-null (set by MmrStage)
     */
    @Override
    public void process(RagPipelineContext ctx) {
        List<ScoredChunk> selected = ctx.getSelectedChunks();

        // ── Guard 1: minimum chunk count ────────────────────────────────────────
        if (selected.size() < properties.getEvidenceMinChunks()) {
            log.warn("Insufficient evidence — only {}/{} chunk(s) selected; aborting",
                    selected.size(), properties.getEvidenceMinChunks());
            ctx.abort(properties.getEvidenceInsufficientAnswer());
            return;
        }

        // ── Guard 2: mean similarity score ──────────────────────────────────────
        float mean = computeMean(selected);
        if (mean < properties.getEvidenceMeanThreshold()) {
            log.warn("Insufficient evidence — mean score={} below threshold={}; aborting",
                    mean, properties.getEvidenceMeanThreshold());
            ctx.abort(properties.getEvidenceInsufficientAnswer());
            return;
        }

        log.debug("Evidence quality OK — {} chunks, mean score={}", selected.size(), mean);
    }

    /**
     * Computes the arithmetic mean of scores across all selected chunks.
     * The caller guarantees the list is non-empty.
     */
    private float computeMean(List<ScoredChunk> chunks) {
        float sum = 0f;
        for (ScoredChunk chunk : chunks) {
            sum += chunk.score();
        }
        return sum / chunks.size();
    }
}
