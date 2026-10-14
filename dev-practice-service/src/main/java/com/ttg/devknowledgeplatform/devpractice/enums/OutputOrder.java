package com.ttg.devknowledgeplatform.devpractice.enums;

/**
 * Whether a problem's answer must come back in one exact order, or any order is accepted — part of
 * the grading contract, so changing it bumps {@code Problem.contractVersion} like a test-data edit.
 *
 * <p>{@link #ANY_ORDER} ignores order at <em>every</em> array level of the answer: both the outer list
 * and each nested list (Group Anagrams accepts the groups in any order and each group's words in any
 * order, as LeetCode does). A problem needing "outer list in any order, inner lists exact" can't be
 * expressed with this flag; that would need a per-problem checker.
 */
public enum OutputOrder {

    /** The answer must match the expected output element for element, in order. */
    ORDERED,

    /** The answer may list its elements, at every nesting level, in any order (duplicates still count). */
    ANY_ORDER
}
