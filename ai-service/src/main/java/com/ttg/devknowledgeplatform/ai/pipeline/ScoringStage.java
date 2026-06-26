package com.ttg.devknowledgeplatform.ai.pipeline;

import com.ttg.devknowledgeplatform.ai.config.EmbeddingProperties;
import com.ttg.devknowledgeplatform.ai.dto.ContentEmbeddingMetadata;
import com.ttg.devknowledgeplatform.ai.dto.RagFilter;
import com.ttg.devknowledgeplatform.ai.dto.RagPipelineContext;
import com.ttg.devknowledgeplatform.ai.dto.ScoredChunk;
import com.ttg.devknowledgeplatform.ai.entity.ContentEmbedding;
import com.ttg.devknowledgeplatform.ai.utils.VectorUtils;

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
 * <p>Filtering is composed directly from the active {@link RagFilter} dimensions:
 * each non-null dimension adds an AND predicate (source type, tags, category).
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

    private final EmbeddingProperties properties;

    @Override
    public void process(RagPipelineContext ctx) {
        // QueryAnomalyStage may raise the threshold for borderline queries; fall back to config default.
        float threshold = ctx.getEffectiveSimilarityThreshold() != null
                ? ctx.getEffectiveSimilarityThreshold()
                : properties.getSimilarityThreshold();

        Predicate<ContentEmbedding> predicate = buildPredicate(ctx.getFilter());

        List<ScoredChunk> scored = ctx.getCandidates().stream()
                .filter(predicate)
                .map(ce -> new ScoredChunk(ce,
                        VectorUtils.dotProduct(ctx.getQueryEmbedding(), ce.getEmbedding())))
                .filter(sc -> sc.score() >= threshold)
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .toList();

        if (scored.isEmpty()) {
            log.warn("No chunks passed filter + similarity threshold {}", threshold);
            ctx.abort(RagPipelineContext.NO_CONTEXT_ANSWER);
            return;
        }

        ctx.setScoredChunks(scored);
    }

    /**
     * Builds a composite AND predicate from the active filter dimensions.
     * Dimensions that are null or empty contribute no predicate (pass-through).
     */
    private Predicate<ContentEmbedding> buildPredicate(RagFilter filter) {
        Predicate<ContentEmbedding> predicate = ce -> true;

        if (filter.sourceTypes() != null && !filter.sourceTypes().isEmpty()) {
            predicate = predicate.and(ce -> filter.sourceTypes().contains(ce.getSourceType()));
        }

        if (filter.tags() != null && !filter.tags().isEmpty()) {
            predicate = predicate.and(ce -> {
                ContentEmbeddingMetadata metadata = ce.getMetadata();
                if (metadata == null || metadata.tagNames() == null) return false;
                return metadata.tagNames().stream().anyMatch(filter.tags()::contains);
            });
        }

        if (filter.categoryId() != null) {
            predicate = predicate.and(ce -> {
                ContentEmbeddingMetadata metadata = ce.getMetadata();
                if (metadata == null) return false;
                return filter.categoryId().equals(metadata.categoryId());
            });
        }

        return predicate;
    }
}
