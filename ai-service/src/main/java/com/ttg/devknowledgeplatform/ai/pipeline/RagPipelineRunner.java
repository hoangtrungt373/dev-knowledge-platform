package com.ttg.devknowledgeplatform.ai.pipeline;

import com.ttg.devknowledgeplatform.ai.dto.RagPipelineContext;
import com.ttg.devknowledgeplatform.ai.dto.StageSpan;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Assembles the ordered RAG pipeline and executes it against a {@link RagPipelineContext}.
 *
 * <p>The pipeline runs stages sequentially. If any stage calls
 * {@link RagPipelineContext#abort(String)}, the runner stops immediately — remaining stages
 * are skipped and the abort reason becomes the user-facing response.
 *
 * <p>Stage order is fixed (prompt-guard → contextualize → embed → query-anomaly → retrieve → score
 * → retrieval-anomaly → MMR → retrieved-content-guard → evidence-quality → build messages)
 * and matches the information dependency between steps: each stage needs outputs from all
 * previous stages. Two complementary guards protect different injection channels:
 * {@code PromptGuardStage} first (user input), {@code RetrievedContentGuardStage} after MMR
 * (corpus data channel).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RagPipelineRunner {

    private final PromptGuardStage promptGuardStage;
    private final ContextualizationStage contextualizationStage;
    private final EmbeddingStage embeddingStage;
    private final QueryAnomalyStage queryAnomalyStage;
    private final RetrievalStage retrievalStage;
    private final ScoringStage scoringStage;
    private final RetrievalAnomalyStage retrievalAnomalyStage;
    private final MmrStage mmrStage;
    private final RetrievedContentGuardStage retrievedContentGuardStage;
    private final EvidenceQualityStage evidenceQualityStage;
    private final MessageBuildingStage messageBuildingStage;

    private List<RagPipelineStage> stages;

    /** Builds the immutable ordered stage list once, after all beans are wired. */
    @PostConstruct
    public void init() {
        stages = List.of(
                promptGuardStage,         // first — injection guard on raw query; before any LLM call
                contextualizationStage,
                embeddingStage,
                queryAnomalyStage,        // after embedding — needs queryEmbedding; before retrieval
                retrievalStage,
                scoringStage,
                retrievalAnomalyStage,      // after scoring — needs sorted scoredChunks; before MMR
                retrievedContentGuardStage, // cleans scoredChunks before MMR — so MMR fills every slot from safe candidates
                mmrStage,
                evidenceQualityStage,       // validates clean chunk count + mean score; before LLM call
                messageBuildingStage
        );
    }

    /**
     * Runs the pipeline against {@code ctx}, stopping early if any stage aborts.
     *
     * <p>Each stage is invoked via {@link RagPipelineStage#execute(RagPipelineContext)}, which
     * times the call and appends a {@link StageSpan} to the context before returning. A
     * {@code PIPELINE_TRACE} log line is always emitted at INFO level after the loop —
     * regardless of whether the pipeline completed or aborted — so every request produces a
     * single structured latency record.
     *
     * @param ctx mutable pipeline context pre-populated with the request inputs
     * @return the same {@code ctx} instance, populated with stage outputs (or abort state)
     */
    public RagPipelineContext run(RagPipelineContext ctx) {
        for (RagPipelineStage stage : stages) {
            stage.execute(ctx);
            if (ctx.isAborted()) {
                log.debug("Pipeline aborted after '{}': {}", stage.name(), ctx.getAbortReason());
                break;
            }
        }
        log.info("PIPELINE_TRACE [{}]: totalMs={} aborted={} stages=[{}]",
                ctx.getTraceId(),
                ctx.elapsedMs(),
                ctx.isAborted() ? ctx.getSpans().stream()
                        .filter(StageSpan::aborted).map(StageSpan::stage).findFirst().orElse("unknown")
                        : "none",
                ctx.getSpans().stream()
                        .map(s -> s.stage() + "(" + s.durationMs() + "ms" + (s.aborted() ? ",ABORTED" : "") + ")")
                        .collect(Collectors.joining(" → ")));
        return ctx;
    }
}
