package com.ttg.devknowledgeplatform.devpractice.enums;

/**
 * A learner's standing on one problem, derived from their own {@code USER} submissions — never
 * stored. A problem the learner never submitted to has no status at all (absent, not a third value),
 * so the progress list only ever holds problems they touched.
 */
public enum ProblemProgressStatus {

    /** At least one submission, none of them {@code ACCEPTED}. */
    ATTEMPTED,

    /**
     * At least one {@code ACCEPTED} submission. Stays solved if the problem's test cases change
     * later — a past verdict is never re-judged (see {@code Submission}'s Javadoc).
     */
    SOLVED
}
