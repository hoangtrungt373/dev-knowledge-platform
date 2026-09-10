package com.ttg.devknowledgeplatform.devutils.dto;

import java.util.List;

/**
 * Response body for Text Diff Checker — genuinely richer than the single-string
 * {@link DevUtilResponse} every other operation shares (a real, ordered line-by-line diff, not
 * one flat block of text), the same scenario {@link HashResponse}/{@link StringCaseResponse}
 * already establish for their own operations.
 *
 * @param lines        every line of the diff, in order, exactly as it should be rendered/read —
 *                     unlike a raw unified-diff text block, this is never re-parsed by a caller;
 *                     each line already carries its own {@link DiffLineType}.
 * @param addedCount   number of {@link DiffLineType#ADDED} lines in {@code lines}.
 * @param removedCount number of {@link DiffLineType#REMOVED} lines in {@code lines}.
 * @param unchangedCount number of {@link DiffLineType#CONTEXT} lines in {@code lines}.
 */
public record TextDiffResponse(
        List<DiffLine> lines,
        int addedCount,
        int removedCount,
        int unchangedCount) {

    /**
     * Whether a line is present only in the "before" text ({@link #REMOVED}), only in the "after"
     * text ({@link #ADDED}), or in both, unchanged ({@link #CONTEXT}) — the classic 3-way split
     * every unified-diff format (including {@code git diff}'s own) renders as {@code -}/{@code +}/
     * a plain line respectively.
     */
    public enum DiffLineType {
        CONTEXT, ADDED, REMOVED
    }

    /**
     * @param type this line's classification.
     * @param text the line's own text, with no trailing newline and no {@code +}/{@code -}/
     *             leading-space prefix — a caller (the GUI's own diff panel, or this class' own
     *             plain-text rendering for Copy/Download) applies whatever presentation it needs
     *             on top of the raw text.
     */
    public record DiffLine(DiffLineType type, String text) {
    }
}
