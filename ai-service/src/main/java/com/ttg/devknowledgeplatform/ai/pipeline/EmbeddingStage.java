package com.ttg.devknowledgeplatform.ai.pipeline;

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
 * <p><strong>Reads:</strong> {@link RagPipelineContext#getContextualizedQuestion()}.<br>
 * <strong>Writes:</strong> {@link RagPipelineContext#setQueryEmbedding(float[])}.
 */
@Component
@RequiredArgsConstructor
public class EmbeddingStage implements RagPipelineStage {

    private final EmbeddingService embeddingService;

    @Override
    public void process(RagPipelineContext ctx) {
        float[] embedding = embeddingService.embed(ctx.getContextualizedQuestion());
        ctx.setQueryEmbedding(embedding);
    }
}
