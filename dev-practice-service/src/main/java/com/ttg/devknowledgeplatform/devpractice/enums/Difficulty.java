package com.ttg.devknowledgeplatform.devpractice.enums;

/**
 * A coding problem's difficulty tier, in the vocabulary this platform's users expect
 * (LeetCode/NeetCode-style), never mapped to/from {@code common.enums.QuestionDifficulty}
 * (BEGINNER/INTERMEDIATE/ADVANCED) — that enum was shared to {@code common} specifically for
 * {@code content-service}'s/{@code ai-service}'s Q&A knowledge-level filtering, a different
 * domain concept that happens to also have three tiers. Reusing it here would force a coding
 * problem's difficulty into labels users don't associate with this kind of content.
 */
public enum Difficulty {
    EASY,
    MEDIUM,
    HARD
}
