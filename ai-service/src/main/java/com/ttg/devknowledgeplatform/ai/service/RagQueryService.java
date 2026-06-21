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
 *
 * <p>Two modes are provided:
 * <ul>
 *   <li>{@link #query} — blocking; returns the full answer once generation completes.</li>
 *   <li>{@link #queryStream} — non-blocking; fires {@link RagStreamHandler} callbacks
 *       token-by-token as the LLM generates the response.</li>
 * </ul>
 */
public interface RagQueryService {

    /**
     * Answers {@code question} using the RAG pipeline (blocking).
     *
     * @param question natural-language question from the user; must not be blank
     * @return the LLM answer together with the source chunks used as context
     */
    RagAnswer query(String question);

    /**
     * Answers {@code question} using the RAG pipeline, streaming tokens via {@code handler}.
     *
     * <p>The embed and retrieval steps run synchronously on the calling thread;
     * LLM generation is asynchronous — {@link RagStreamHandler} callbacks fire from
     * a LangChain4j-managed thread after this method returns.
     *
     * @param question natural-language question from the user; must not be blank
     * @param handler  callbacks to receive sources, tokens, completion, and errors
     */
    void queryStream(String question, RagStreamHandler handler);
}
