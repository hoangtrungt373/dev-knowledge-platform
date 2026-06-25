package com.ttg.devknowledgeplatform.ai.pipeline;

import com.ttg.devknowledgeplatform.ai.config.EmbeddingProperties;
import com.ttg.devknowledgeplatform.ai.entity.ContentEmbedding;
import com.ttg.devknowledgeplatform.ai.filter.RagFilterStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Pipeline stage that applies dynamic filter predicates, scores each candidate chunk by
 * cosine similarity, removes chunks below the configured threshold, and sorts the survivors
 * descending by score.
 *
 * <p>Filter strategies are collected from Spring context as a {@code List<RagFilterStrategy>}.
 * Only strategies whose {@link RagFilterStrategy#isApplicable} returns {@code true} for the
 * current {@link com.ttg.devknowledgeplatform.ai.filter.RagFilter} participate;
 * their predicates are AND-composed.
 *
 * <p>Filtering happens in Java after the pgvector ANN search intentionally: pushing arbitrary
 * predicates into SQL WHERE clauses would bypass the HNSW index and force a sequential scan.
 * The {@link RetrievalStage} oversamples to compensate for chunks removed here.
 *
 * <p>Aborts the pipeline if all chunks are below the similarity threshold or removed by filters.
 *
 * <p><strong>Reads:</strong> {@link RagPipelineContext#getCandidates()},
 * {@link RagPipelineContext#getQueryEmbedding()},
 * {@link RagPipelineContext#getFilter()}.<br>
 * <strong>Writes:</strong> {@link RagPipelineContext#setScoredChunks(List)}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScoringStage implements RagPipelineStage {

    private final List<RagFilterStrategy> filterStrategies;
    private final EmbeddingProperties properties;

    @Override
    public void process(RagPipelineContext ctx) {
        Predicate<ContentEmbedding> compositePredicate = filterStrategies.stream()
                .filter(s -> s.isApplicable(ctx.getFilter()))
                .map(s -> s.predicate(ctx.getFilter()))
                .reduce(Predicate::and)
                .orElse(ce -> true);

        List<ScoredChunk> scored = ctx.getCandidates().stream()
                .filter(compositePredicate)
                .map(ce -> new ScoredChunk(ce,
                        VectorUtils.dotProduct(ctx.getQueryEmbedding(), ce.getEmbedding())))
                .filter(sc -> sc.score() >= properties.getSimilarityThreshold())
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .toList();

        if (scored.isEmpty()) {
            log.warn("No chunks passed filter + similarity threshold {}",
                    properties.getSimilarityThreshold());
            ctx.abort(RagPipelineContext.NO_CONTEXT_ANSWER);
            return;
        }

        ctx.setScoredChunks(scored);
    }
}
