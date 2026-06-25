package com.ttg.devknowledgeplatform.ai.pipeline;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

import com.ttg.devknowledgeplatform.ai.dto.RagPipelineContext;

/**
 * Assembles the ordered RAG pipeline and executes it against a {@link RagPipelineContext}.
 *
 * <p>The pipeline runs stages sequentially. If any stage calls
 * {@link RagPipelineContext#abort(String)}, the runner stops immediately — remaining stages
 * are skipped and the abort reason becomes the user-facing response.
 *
 * <p>Stage order is fixed (contextualize → embed → retrieve → score → MMR → build messages)
 * and matches the information dependency between steps: each stage needs outputs from all
 * previous stages.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RagPipelineRunner {

    private final ContextualizationStage contextualizationStage;
    private final EmbeddingStage embeddingStage;
    private final RetrievalStage retrievalStage;
    private final ScoringStage scoringStage;
    private final MmrStage mmrStage;
    private final MessageBuildingStage messageBuildingStage;

    private List<RagPipelineStage> stages;

    /** Builds the immutable ordered stage list once, after all beans are wired. */
    @PostConstruct
    public void init() {
        stages = List.of(
                contextualizationStage,
                embeddingStage,
                retrievalStage,
                scoringStage,
                mmrStage,
                messageBuildingStage
        );
    }

    /**
     * Runs the pipeline against {@code ctx}, stopping early if any stage aborts.
     *
     * @param ctx mutable pipeline context pre-populated with the request inputs
     * @return the same {@code ctx} instance, populated with stage outputs (or abort state)
     */
    public RagPipelineContext run(RagPipelineContext ctx) {
        for (RagPipelineStage stage : stages) {
            stage.process(ctx);
            if (ctx.isAborted()) {
                log.debug("Pipeline aborted after '{}': {}", stage.name(), ctx.getAbortReason());
                return ctx;
            }
        }
        return ctx;
    }
}
