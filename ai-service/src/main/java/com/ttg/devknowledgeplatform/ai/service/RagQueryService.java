package com.ttg.devknowledgeplatform.ai.service;

import com.ttg.devknowledgeplatform.ai.dto.RagAnswer;
import com.ttg.devknowledgeplatform.ai.filter.RagFilter;
import com.ttg.devknowledgeplatform.common.dto.ConversationTurn;

import java.util.List;

/**
 * Entry point for the RAG (Retrieval-Augmented Generation) pipeline.
 *
 * <p>Orchestrates three steps for each incoming question:
 * <ol>
 *   <li>Embed the question with the configured embedding model.</li>
 *   <li>Retrieve the top-K most similar chunks from the vector store,
 *       optionally filtered by a {@link RagFilter}.</li>
 *   <li>Pass the retrieved context — plus any prior conversation turns — to the LLM
 *       and return the generated answer.</li>
 * </ol>
 *
 * <p>Two modes are provided — blocking and streaming — each available in three overloads:
 * <ul>
 *   <li>question only (no history, no filter)</li>
 *   <li>question + history (no filter)</li>
 *   <li>question + history + {@link RagFilter} (primary; others delegate here)</li>
 * </ul>
 *
 * <p>The two-argument and one-argument overloads are {@code default} methods that delegate to
 * the three-argument primary with {@link RagFilter#none()}, so existing callers require no changes.
 */
public interface RagQueryService {

    // -------------------------------------------------------------------------
    // Blocking
    // -------------------------------------------------------------------------

    /**
     * Answers {@code question} using the RAG pipeline (blocking), with no prior context and no filter.
     *
     * @param question natural-language question; must not be blank
     * @return the LLM answer together with the source chunks used as context
     */
    default RagAnswer query(String question) {
        return query(question, List.of(), RagFilter.none());
    }

    /**
     * Answers {@code question} using the RAG pipeline (blocking), with prior conversation
     * context but no retrieval filter.
     *
     * @param question natural-language question; must not be blank
     * @param history  prior conversation turns, ordered oldest-first; empty list for a fresh session
     * @return the LLM answer together with the source chunks used as context
     */
    default RagAnswer query(String question, List<ConversationTurn> history) {
        return query(question, history, RagFilter.none());
    }

    /**
     * Answers {@code question} using the RAG pipeline (blocking), applying {@code filter} to
     * narrow the vector-search candidate set before scoring and threshold filtering.
     *
     * <p>When {@code filter} is non-empty the retrieval step overshoots by a configured factor
     * to compensate for candidates removed by the filter, then cuts back to {@code topK}.
     *
     * @param question natural-language question; must not be blank
     * @param history  prior conversation turns, ordered oldest-first; empty list for a fresh session
     * @param filter   retrieval filter; use {@link RagFilter#none()} to disable filtering
     * @return the LLM answer together with the source chunks used as context
     */
    RagAnswer query(String question, List<ConversationTurn> history, RagFilter filter);

    // -------------------------------------------------------------------------
    // Streaming
    // -------------------------------------------------------------------------

    /**
     * Answers {@code question} using the RAG pipeline (streaming), with no prior context and no filter.
     *
     * @param question natural-language question; must not be blank
     * @param handler  callbacks to receive sources, tokens, completion, and errors
     */
    default void queryStream(String question, RagStreamHandler handler) {
        queryStream(question, List.of(), RagFilter.none(), handler);
    }

    /**
     * Answers {@code question} using the RAG pipeline (streaming), with prior conversation
     * context but no retrieval filter.
     *
     * @param question natural-language question; must not be blank
     * @param history  prior conversation turns, ordered oldest-first; empty list for a fresh session
     * @param handler  callbacks to receive sources, tokens, completion, and errors
     */
    default void queryStream(String question, List<ConversationTurn> history, RagStreamHandler handler) {
        queryStream(question, history, RagFilter.none(), handler);
    }

    /**
     * Answers {@code question} using the RAG pipeline (streaming), applying {@code filter} to
     * narrow the retrieval candidate set.
     *
     * <p>The embed and retrieval steps run synchronously on the calling thread;
     * LLM generation is asynchronous — {@link RagStreamHandler} callbacks fire from a
     * LangChain4j-managed thread after this method returns.
     *
     * @param question natural-language question; must not be blank
     * @param history  prior conversation turns, ordered oldest-first; empty list for a fresh session
     * @param filter   retrieval filter; use {@link RagFilter#none()} to disable filtering
     * @param handler  callbacks to receive sources, tokens, completion, and errors
     */
    void queryStream(String question, List<ConversationTurn> history, RagFilter filter, RagStreamHandler handler);
}
