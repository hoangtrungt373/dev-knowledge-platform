package com.ttg.devknowledgeplatform.ai.service;

import com.ttg.devknowledgeplatform.ai.dto.ConversationContext;

/**
 * Guards against sudden topic shifts in a multi-turn conversation (Case 7).
 *
 * <h3>Problem</h3>
 * <p>{@code ContextualizationStage} uses recent conversation turns to resolve pronoun
 * references in the user's question. When the topic shifts abruptly — for example, after
 * ten turns about SQL the user asks "How does encryption work?" — the stage may wrongly
 * import context from the old topic into the new question's standalone rewrite, polluting
 * the query embedding and driving retrieval toward irrelevant results.
 *
 * <h3>Action</h3>
 * <p>When a shift is detected, {@link #guard} returns a modified {@link ConversationContext}
 * with recent turns cleared. {@code ContextualizationStage} then treats the new question as
 * standalone — no pronoun resolution, no old-topic cross-contamination. The rolling summary
 * is preserved; it provides broad background for generation but does not cause pronoun
 * resolution errors because it contains no unresolved references.
 *
 * <h3>Detection</h3>
 * <p>Cosine similarity between the new question embedding and the history fingerprint
 * (summary if available, otherwise concatenated user turns). A sudden hard pivot —
 * Backend to Medicine, SQL to Cooking — produces similarity ~0.05–0.20 and is caught.
 * Gradual topic evolution within the developer domain — Spring to Cryptography — produces
 * similarity ~0.45–0.60 and passes through.
 */
public interface ConversationTopicGuardService {

    /**
     * Evaluates the new question against the conversation history and returns the context
     * that should be passed to the RAG pipeline.
     *
     * <p>Returns the original context unchanged when no shift is detected, when the session
     * has no history yet, or when the guard cannot run (empty history text). Returns a
     * context with recent turns cleared when a sudden topic shift is detected.
     *
     * @param question the raw user question for this request
     * @param context  the full conversation context assembled from the session
     * @return the original context, or a stripped context with recent turns removed on shift
     */
    ConversationContext guard(String question, ConversationContext context);
}
