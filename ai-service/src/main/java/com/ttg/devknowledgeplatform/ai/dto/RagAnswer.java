package com.ttg.devknowledgeplatform.ai.dto;

import java.util.List;

/**
 * The result of a RAG (Retrieval-Augmented Generation) query.
 *
 * @param answer  LLM-generated answer grounded in the retrieved context.
 * @param sources Knowledge-base chunks that were retrieved and used as context,
 *                ordered by cosine similarity descending.
 */
public record RagAnswer(
        String answer,
        List<RagSource> sources
) {}
