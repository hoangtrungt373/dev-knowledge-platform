package com.ttg.devknowledgeplatform.common.enums;

/**
 * Moved here from {@code content-service} once that module was extracted into a standalone
 * service — {@code ai-service}'s public {@code PublicContentApi} (before it moved back to
 * {@code content-service}) and internal filters used this enum directly, so it needed to become a
 * genuinely shared value type rather than a cross-service Java import.
 */
public enum QuestionDifficulty {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED
}
