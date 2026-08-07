package com.ttg.devknowledgeplatform.common.enums;

/**
 * Moved here from {@code content-service} once that module was extracted into a standalone
 * service — {@code ai-service} uses this enum on its own public REST contracts and internal
 * indexing filters, not just as {@code content-service}-internal plumbing, so it needed to become
 * a genuinely shared value type rather than a cross-service Java import.
 */
public enum ContentStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED
}
