package com.ttg.devknowledgeplatform.ai.filter;

import com.ttg.devknowledgeplatform.ai.dto.RagFilter;
import com.ttg.devknowledgeplatform.ai.entity.ContentEmbedding;

import java.util.function.Predicate;

/**
 * Strategy that produces a {@link Predicate} for post-retrieval filtering of embedding chunks.
 *
 * <p>Each implementation handles exactly one filter dimension (e.g., source type, tags, category).
 * Spring collects all {@code @Component} implementations as a {@code List<RagFilterStrategy>},
 * which {@code RagQueryServiceImpl} composes with {@link Predicate#and(Predicate)} so that every
 * active strategy must pass for a chunk to survive retrieval.
 *
 * <p>{@link #isApplicable(RagFilter)} guards composition: when a strategy's dimension is absent
 * from the filter it returns {@code false} so no no-op predicate is added to the chain.
 */
public interface RagFilterStrategy {

    /**
     * Returns a predicate that accepts chunks satisfying this strategy's filter dimension.
     *
     * <p>Only called when {@link #isApplicable(RagFilter)} returned {@code true}.
     *
     * @param filter the active filter criteria
     * @return predicate returning {@code true} for chunks that pass this constraint
     */
    Predicate<ContentEmbedding> predicate(RagFilter filter);

    /**
     * Returns {@code true} when {@code filter} contains enough data for this strategy to act.
     *
     * <p>Implementations should check only whether their own dimension is populated — they must
     * not inspect other dimensions so that composition remains independent.
     *
     * @param filter the active filter criteria
     * @return {@code true} if this strategy should contribute its predicate to the chain
     */
    boolean isApplicable(RagFilter filter);
}
