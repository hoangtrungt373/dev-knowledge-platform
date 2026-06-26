package com.ttg.devknowledgeplatform.ai.service;

import com.ttg.devknowledgeplatform.ai.dto.AnswerQualityVerdict;
import com.ttg.devknowledgeplatform.ai.dto.RagPipelineContext;

/**
 * Evaluates the quality of a generated answer by comparing it against the retrieved context
 * and the original query embedding (Case 6 — answer drift detection).
 *
 * <h3>Two-sided check</h3>
 * <ol>
 *   <li><strong>Context similarity</strong> — embeds the generated answer and computes its cosine
 *       similarity against the centroid of the MMR-selected chunks that were injected into the
 *       LLM context window. A low score means the answer did not come from the retrieved material
 *       — the LLM likely fell back to training data (hallucination).</li>
 *   <li><strong>Query similarity</strong> — computes cosine similarity between the answer embedding
 *       and the query embedding already stored in the pipeline context. A low score means the answer
 *       addressed a different topic than what was asked.</li>
 * </ol>
 *
 * <h3>Usage</h3>
 * <p>Called from {@code RagQueryServiceImpl} after LLM generation completes — both in the
 * blocking path (before returning {@code RagAnswer}) and in the streaming {@code onComplete}
 * callback (after all tokens have been sent). Currently monitoring-only: the verdict is logged
 * but does not modify or block the response.
 */
public interface AnswerQualityService {

    /**
     * Assesses whether the generated answer is grounded in the retrieved context and on-topic
     * with respect to the user's query.
     *
     * @param answer      the complete generated answer text
     * @param pipelineCtx the pipeline context containing the query embedding and selected chunks
     * @return a verdict with context and query similarity scores; {@link AnswerQualityVerdict#skipped()}
     *         when the check cannot run (no chunks or no query embedding available)
     */
    AnswerQualityVerdict assess(String answer, RagPipelineContext pipelineCtx);
}
