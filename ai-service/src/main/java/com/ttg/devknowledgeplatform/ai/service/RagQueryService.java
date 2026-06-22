package com.ttg.devknowledgeplatform.ai.service;

import com.ttg.devknowledgeplatform.ai.dto.RagAnswer;
import com.ttg.devknowledgeplatform.common.dto.ConversationTurn;

import java.util.List;

/**
 * Entry point for the RAG (Retrieval-Augmented Generation) pipeline.
 *
 * <p>Orchestrates three steps for each incoming question:
 * <ol>
 *   <li>Embed the question with the configured embedding model.</li>
 *   <li>Retrieve the top-K most similar chunks from the vector store.</li>
 *   <li>Pass the retrieved context — plus any prior conversation turns — to the LLM
 *       and return the generated answer.</li>
 * </ol>
 *
 * <p>Two modes are provided, each with and without conversation history:
 * <ul>
 *   <li>{@link #query(String)} / {@link #query(String, List)} — blocking.</li>
 *   <li>{@link #queryStream(String, RagStreamHandler)} / {@link #queryStream(String, List, RagStreamHandler)}
 *       — non-blocking, fires {@link RagStreamHandler} callbacks token-by-token.</li>
 * </ul>
 *
 * <p>The no-history overloads are {@code default} methods that delegate to the history-aware
 * variants with an empty list, so existing callers need no changes.
 */
public interface RagQueryService {

    /**
     * Answers {@code question} using the RAG pipeline (blocking), with no prior conversation context.
     *
     * @param question natural-language question from the user; must not be blank
     * @return the LLM answer together with the source chunks used as context
     */
    default RagAnswer query(String question) {
        return query(question, List.of());
    }

    /**
     * Answers {@code question} using the RAG pipeline (blocking), injecting prior conversation
     * turns as additional context messages so the LLM can understand follow-up questions.
     *
     * @param question natural-language question from the user; must not be blank
     * @param history  prior conversation turns, ordered oldest-first; empty list for a fresh session
     * @return the LLM answer together with the source chunks used as context
     */
    RagAnswer query(String question, List<ConversationTurn> history);

    /**
     * Answers {@code question} using the RAG pipeline (streaming), with no prior conversation context.
     *
     * @param question natural-language question from the user; must not be blank
     * @param handler  callbacks to receive sources, tokens, completion, and errors
     */
    default void queryStream(String question, RagStreamHandler handler) {
        queryStream(question, List.of(), handler);
    }

    /**
     * Answers {@code question} using the RAG pipeline (streaming), injecting prior conversation
     * turns as additional context messages.
     *
     * <p>The embed and retrieval steps run synchronously on the calling thread;
     * LLM generation is asynchronous — {@link RagStreamHandler} callbacks fire from
     * a LangChain4j-managed thread after this method returns.
     *
     * @param question natural-language question from the user; must not be blank
     * @param history  prior conversation turns, ordered oldest-first; empty list for a fresh session
     * @param handler  callbacks to receive sources, tokens, completion, and errors
     */
    void queryStream(String question, List<ConversationTurn> history, RagStreamHandler handler);
}
