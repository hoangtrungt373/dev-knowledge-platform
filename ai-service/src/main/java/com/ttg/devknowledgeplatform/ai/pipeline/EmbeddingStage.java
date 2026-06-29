package com.ttg.devknowledgeplatform.ai.pipeline;

import com.ttg.devknowledgeplatform.ai.dto.EmbedResult;
import com.ttg.devknowledgeplatform.ai.dto.RagPipelineContext;
import com.ttg.devknowledgeplatform.ai.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Pipeline stage that converts the contextualized question into a dense embedding vector.
 *
 * <p>Calls OpenAI's text-embedding model via {@link EmbeddingService}. The resulting vector
 * is used downstream by {@link RetrievalStage} (pgvector cosine search) and by
 * {@link ScoringStage} and {@link MmrStage} (dot-product similarity scoring).
 *
 * <p>The token count from the embedding API call is recorded on the context for cost tracking.
 *
 * <p><strong>Reads:</strong> {@link RagPipelineContext#getContextualizedQuestion()}.<br>
 * <strong>Writes:</strong> {@link RagPipelineContext#setQueryEmbedding(float[])},
 * {@link RagPipelineContext#setEmbeddingTokens(int)}.
 */
@Component
@RequiredArgsConstructor
public class EmbeddingStage implements RagPipelineStage {

    private final EmbeddingService embeddingService;

    @Override
    public void process(RagPipelineContext ctx) {
        EmbedResult result = embeddingService.embed(ctx.getContextualizedQuestion());
        ctx.setQueryEmbedding(result.vector());
        ctx.setEmbeddingTokens(result.tokenCount());
    }
}
