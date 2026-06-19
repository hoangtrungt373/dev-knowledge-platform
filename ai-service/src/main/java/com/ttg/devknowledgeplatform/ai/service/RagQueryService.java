package com.ttg.devknowledgeplatform.ai.service;

import com.ttg.devknowledgeplatform.ai.dto.RagAnswer;

/**
 * Entry point for the RAG (Retrieval-Augmented Generation) pipeline.
 *
 * <p>Orchestrates three steps for each incoming question:
 * <ol>
 *   <li>Embed the question with the configured embedding model.</li>
 *   <li>Retrieve the top-K most similar chunks from the vector store.</li>
 *   <li>Pass the retrieved context to the LLM and return the generated answer.</li>
 * </ol>
 */
public interface RagQueryService {

    /**
     * Answers {@code question} using the RAG pipeline.
     *
     * @param question natural-language question from the user; must not be blank
     * @return the LLM answer together with the source chunks used as context
     */
    RagAnswer query(String question);
}
