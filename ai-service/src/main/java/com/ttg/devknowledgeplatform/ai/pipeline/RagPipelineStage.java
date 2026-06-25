package com.ttg.devknowledgeplatform.ai.pipeline;

/**
 * A single step in the RAG retrieval pipeline (Pipes-and-Filters pattern).
 *
 * <p>Each stage reads from and writes to the shared {@link RagPipelineContext}. Stages
 * are stateless Spring {@code @Component} beans and are safe to call concurrently from
 * different requests.
 *
 * <p>A stage that cannot produce a meaningful result (e.g. the knowledge base is empty,
 * no chunks pass the similarity threshold) should call
 * {@link RagPipelineContext#abort(String)} with a user-facing message and return immediately.
 * The {@link RagPipelineRunner} will stop processing subsequent stages.
 *
 * <p>Unlike the classic Chain of Responsibility pattern — where each handler decides whether
 * to handle or delegate — every stage in this pipeline always runs unless the context is
 * aborted by a preceding stage.
 */
@FunctionalInterface
public interface RagPipelineStage {

    /**
     * Executes this stage, mutating {@code ctx} with its output.
     *
     * @param ctx shared mutable pipeline context
     */
    void process(RagPipelineContext ctx);

    /**
     * Human-readable stage name used in log messages. Defaults to the simple class name.
     *
     * @return stage name for diagnostic output
     */
    default String name() {
        return getClass().getSimpleName();
    }
}
