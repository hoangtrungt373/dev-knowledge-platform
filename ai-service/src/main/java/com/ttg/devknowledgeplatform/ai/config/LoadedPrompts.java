package com.ttg.devknowledgeplatform.ai.config;

/**
 * Holds system prompt and utility prompt strings loaded from classpath resources at startup.
 *
 * <p>Produced by {@link PromptsLoader} and exposed as a Spring bean. Consumers that need
 * prompt text inject this record directly rather than carrying the full configuration blob.
 *
 * <p>Java 21 record — immutable data carrier with auto-generated constructor, accessors,
 * {@code equals}, {@code hashCode}, and {@code toString}.
 */
public record LoadedPrompts(
        /** Default RAG system prompt for mixed or unfiltered queries. */
        String system,
        /** System prompt used when the query is scoped exclusively to {@code ARTICLE} content. */
        String article,
        /** System prompt used when the query is scoped exclusively to {@code INTERVIEW_QUESTION} content. */
        String interviewQuestion,
        /** System prompt used when the query is scoped exclusively to {@code BLOG_POST} content. */
        String blogPost,
        /** Prompt sent to the LLM to resolve pronouns and enrich the raw query into a structured form. */
        String inputEnrichment,
        /** Prompt prefix used when compressing old conversation turns into a rolling summary. */
        String summarisation
) {}
