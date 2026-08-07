package com.ttg.devknowledgeplatform.common.enums;

/**
 * Discriminates content *shape* (which JOINed subtype table applies), never subject matter — see
 * root {@code CLAUDE.md}'s "Content domains" section. Moved here from {@code content-service} once
 * {@code content-service} was extracted into a standalone service: {@code ai-service} uses this
 * enum on its own public REST contracts ({@code ChatRequest.sourceTypes}, {@code RagFilter}), not
 * just as {@code content-service}-internal plumbing, so it needed to become a genuinely shared
 * value type rather than a cross-service Java import that a Maven-dependency removal would break.
 */
public enum ContentType {
    QUESTION_ANSWER,
    ARTICLE,
    BLOG_POST
}
