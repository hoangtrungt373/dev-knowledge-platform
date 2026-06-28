package com.ttg.devknowledgeplatform.ai.pipeline;

import com.ttg.devknowledgeplatform.ai.config.RetrievalConfig;
import com.ttg.devknowledgeplatform.ai.dto.RagPipelineContext;
import com.ttg.devknowledgeplatform.ai.entity.ContentEmbedding;
import com.ttg.devknowledgeplatform.ai.repository.ContentEmbeddingRepository;
import com.ttg.devknowledgeplatform.ai.utils.VectorUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Pipeline stage that fetches candidate chunks from the pgvector index and loads their
 * full content via an eager JOIN FETCH.
 *
 * <p>Two repository calls are issued:
 * <ol>
 *   <li>{@code findTopSimilarIds} — ANN search using the HNSW index ({@code <=>} cosine distance
 *       operator). Always fetches {@code topK × oversampleFactor} candidates to give the downstream
 *       {@link MmrStage} enough diverse material. Oversampling compensates for two independent
 *       reasons candidates are discarded: active filter strategies in {@link ScoringStage} and
 *       MMR's natural diversity penalty de-prioritising redundant chunks.</li>
 *   <li>{@code findAllByIdWithContentItem} — loads the matching rows with their parent
 *       {@code ContentItem} in one query to avoid N+1 selects.</li>
 * </ol>
 *
 * <p>Aborts the pipeline with {@link RagPipelineContext#NO_CONTEXT_ANSWER} if no IDs are returned
 * (the knowledge base is empty or the query embedding is too dissimilar to all stored vectors).
 *
 * <p><strong>Reads:</strong> {@link RagPipelineContext#getQueryEmbedding()},
 * {@link RagPipelineContext#getFilter()}.<br>
 * <strong>Writes:</strong> {@link RagPipelineContext#setCandidates(List)}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RetrievalStage implements RagPipelineStage {

    private final ContentEmbeddingRepository contentEmbeddingRepository;
    private final RetrievalConfig retrieval;

    @Override
    public void process(RagPipelineContext ctx) {
        // Always oversample: gives MmrStage enough diverse candidates regardless of whether
        // a filter is active or many top candidates happen to come from the same document.
        int candidateLimit = retrieval.getTopK() * retrieval.getOversampleFactor();

        List<Integer> ids = contentEmbeddingRepository.findTopSimilarIds(
                VectorUtils.toVectorString(ctx.getQueryEmbedding()), candidateLimit);

        if (ids.isEmpty()) {
            log.warn("No embeddings found in the knowledge base");
            ctx.abort(RagPipelineContext.NO_CONTEXT_ANSWER);
            return;
        }

        List<ContentEmbedding> chunks = contentEmbeddingRepository.findAllByIdWithContentItem(ids);
        ctx.setCandidates(chunks);
    }
}
