package com.ttg.devknowledgeplatform.ai.service.impl;

import com.ttg.devknowledgeplatform.ai.config.EmbeddingProperties;
import com.ttg.devknowledgeplatform.ai.service.ConversationTopicGuardService;
import com.ttg.devknowledgeplatform.ai.service.EmbeddingService;
import com.ttg.devknowledgeplatform.ai.utils.VectorUtils;
import com.ttg.devknowledgeplatform.common.dto.ConversationContext;
import com.ttg.devknowledgeplatform.common.dto.ConversationTurn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Default {@link ConversationTopicGuardService} implementation.
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>Return the original context unchanged if no history is present — nothing to compare
 *       against for a fresh session or when the guard cannot produce a meaningful signal.</li>
 *   <li>Build a history fingerprint: use the rolling {@code summary} if one exists (stable,
 *       multi-turn compressed topic representation), otherwise fall back to the concatenated
 *       text of recent {@code USER} turns (the user's questions encode the topic more cleanly
 *       than AI answers, which introduce tangential vocabulary).</li>
 *   <li>Embed the new question and the history fingerprint together in a single
 *       {@link EmbeddingService#embedBatch} call (one API round trip regardless of fingerprint
 *       length).</li>
 *   <li>Compute cosine similarity via dot product. Both vectors are L2-normalised by OpenAI,
 *       so {@link VectorUtils#dotProduct} gives the exact cosine value without division.</li>
 *   <li>If similarity &lt; {@code conversation-topic-shift-threshold}: log {@code WARN} and
 *       return a stripped context with recent turns cleared. {@code ContextualizationStage}
 *       then treats the new question as standalone — no pronoun resolution against old topic
 *       content, no stale retrieval bias.</li>
 * </ol>
 *
 * <h3>Why clear recent turns but keep the summary</h3>
 * <p>Recent turns contain raw Q&amp;A exchanges. {@code ContextualizationStage} uses them to
 * resolve pronouns: "How does <em>it</em> work?" after ten SQL turns becomes
 * "How does SQL indexing work?" — correct in context but catastrophically wrong after a topic
 * shift. Clearing the turns forces a standalone interpretation.
 *
 * <p>The summary is a compressed paragraph about older conversation topics. It does not contain
 * pronouns that would be resolved, and it provides the generation LLM with background that helps
 * it understand the user's technical level. Keeping it causes no retrieval contamination.
 *
 * <h3>Threshold rationale</h3>
 * <p>Hard out-of-domain pivots (Backend → Medicine, SQL → Cooking) produce similarity
 * ~0.05–0.20 against a developer-topic history fingerprint. Developer-adjacent pivots
 * (Spring → Cryptography, Backend → Machine Learning) produce ~0.40–0.60 — these are
 * still legitimate on a developer knowledge platform and should not trigger a reset.
 * The default threshold of {@code 0.35} sits in the gap between those two bands.
 * Calibrate from logs after observing real traffic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationTopicGuardServiceImpl implements ConversationTopicGuardService {

    private final EmbeddingService embeddingService;
    private final EmbeddingProperties properties;

    @Override
    public ConversationContext guard(String question, ConversationContext context) {
        String historyText = buildHistoryFingerprint(context);
        if (historyText.isBlank()) {
            return context;
        }

        List<float[]> embeddings = embeddingService.embedBatch(List.of(question, historyText));
        float[] questionEmbedding = embeddings.get(0);
        float[] historyEmbedding  = embeddings.get(1);

        float similarity = VectorUtils.dotProduct(questionEmbedding, historyEmbedding);
        float threshold  = properties.getConversationTopicShiftThreshold();

        if (similarity < threshold) {
            log.warn("ConversationTopicGuard: topic shift detected — similarity={} threshold={} — "
                            + "recent turns stripped to prevent cross-topic context contamination",
                    similarity, threshold);
            return new ConversationContext(context.summary(), List.of());
        }

        log.debug("ConversationTopicGuard: topic consistent — similarity={}", similarity);
        return context;
    }

    /**
     * Builds the history fingerprint used as the comparison target.
     *
     * <p>Prefers the rolling summary when one exists — it is a stable, multi-turn topic
     * representation that changes slowly as the conversation evolves. When no summary is
     * available (early in a session), falls back to the concatenated text of recent
     * {@code USER} turns. AI turns are excluded because their verbose explanatory prose
     * introduces tangential vocabulary that dilutes the topic signal.
     *
     * <p>Returns a blank string when the context contains neither a summary nor any user
     * turns — the caller skips the guard in that case.
     *
     * @param context the current conversation context
     * @return a single string representing the conversation topic, or blank if no history
     */
    private String buildHistoryFingerprint(ConversationContext context) {
        if (context.hasSummary()) {
            return context.summary();
        }
        return context.recentTurns().stream()
                .filter(t -> "USER".equals(t.role()))
                .map(ConversationTurn::content)
                .collect(Collectors.joining(" "));
    }
}
