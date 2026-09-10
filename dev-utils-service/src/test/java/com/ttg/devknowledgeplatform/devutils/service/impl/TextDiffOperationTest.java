package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.dto.TextDiffResponse;
import com.ttg.devknowledgeplatform.devutils.dto.TextDiffResponse.DiffLine;
import com.ttg.devknowledgeplatform.devutils.dto.TextDiffResponse.DiffLineType;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;

class TextDiffOperationTest {

    private final TextDiffOperation operation = new TextDiffOperation();

    private static final String ORIGINAL = "const ship = () => 'today';\nconsole.log(ship());";
    private static final String UPDATED =
            "const ship = () => 'production';\nconsole.log(ship());\nconsole.log('Done 🚀');";

    // A real LCS diff, unlike the reported example's own hand-typed output, correctly recognizes
    // that "console.log(ship());" is unchanged — see TextDiffOperation's own Javadoc for why this
    // is the intended, correct behavior, not a deviation to "fix."
    @Test
    void diffsTheExactReportedExampleRecognizingTheUnchangedLine() {
        TextDiffResponse result = operation.execute(ORIGINAL, UPDATED);

        assertThat(result.lines()).containsExactly(
                new DiffLine(DiffLineType.REMOVED, "const ship = () => 'today';"),
                new DiffLine(DiffLineType.ADDED, "const ship = () => 'production';"),
                new DiffLine(DiffLineType.CONTEXT, "console.log(ship());"),
                new DiffLine(DiffLineType.ADDED, "console.log('Done 🚀');"));
        assertThat(result.addedCount()).isEqualTo(2);
        assertThat(result.removedCount()).isEqualTo(1);
        assertThat(result.unchangedCount()).isEqualTo(1);
    }

    @Test
    void identicalTextProducesOnlyContextLines() {
        TextDiffResponse result = operation.execute("a\nb\nc", "a\nb\nc");

        assertThat(result.lines()).hasSize(3);
        assertThat(result.lines()).allMatch(line -> line.type() == DiffLineType.CONTEXT);
        assertThat(result.addedCount()).isZero();
        assertThat(result.removedCount()).isZero();
    }

    @Test
    void blankOriginalMeansEveryLineOfUpdatedIsAdded() {
        TextDiffResponse result = operation.execute("", "a\nb");

        assertThat(result.lines()).containsExactly(
                new DiffLine(DiffLineType.ADDED, "a"),
                new DiffLine(DiffLineType.ADDED, "b"));
        assertThat(result.removedCount()).isZero();
        assertThat(result.addedCount()).isEqualTo(2);
    }

    @Test
    void blankUpdatedMeansEveryLineOfOriginalIsRemoved() {
        TextDiffResponse result = operation.execute("a\nb", "");

        assertThat(result.lines()).containsExactly(
                new DiffLine(DiffLineType.REMOVED, "a"),
                new DiffLine(DiffLineType.REMOVED, "b"));
        assertThat(result.addedCount()).isZero();
        assertThat(result.removedCount()).isEqualTo(2);
    }

    @Test
    void bothBlankProducesAnEmptyDiff() {
        TextDiffResponse result = operation.execute("", "");

        assertThat(result.lines()).isEmpty();
        assertThat(result.addedCount()).isZero();
        assertThat(result.removedCount()).isZero();
        assertThat(result.unchangedCount()).isZero();
    }

    @Test
    void nullFieldsAreTreatedTheSameAsBlank() {
        TextDiffResponse result = operation.execute(null, "a");

        assertThat(result.lines()).containsExactly(new DiffLine(DiffLineType.ADDED, "a"));
    }

    // A single trailing newline is insignificant, matching every mainstream diff tool's default —
    // "a\nb\n" and "a\nb" must diff as identical, not show a spurious trailing blank-line change.
    @Test
    void aSingleTrailingNewlineIsInsignificant() {
        TextDiffResponse result = operation.execute("a\nb\n", "a\nb");

        assertThat(result.lines()).hasSize(2);
        assertThat(result.lines()).allMatch(line -> line.type() == DiffLineType.CONTEXT);
    }

    @Test
    void aGenuineBlankLineInTheMiddleIsPreserved() {
        TextDiffResponse result = operation.execute("a\n\nb", "a\n\nb");

        assertThat(result.lines()).containsExactly(
                new DiffLine(DiffLineType.CONTEXT, "a"),
                new DiffLine(DiffLineType.CONTEXT, ""),
                new DiffLine(DiffLineType.CONTEXT, "b"));
    }

    @Test
    void rejectsInputWithMoreLinesThanTheCap() {
        String tooManyLines = "x\n".repeat(TextDiffOperation.MAX_LINES + 1);

        assertThatThrownBy(() -> operation.execute(tooManyLines, "a"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(DevUtilsErrorCode.DIFF_INPUT_TOO_LARGE));
    }

    @Test
    void acceptsInputAtExactlyTheLineCap() {
        String exactlyAtCap = String.join("\n", java.util.Collections.nCopies(TextDiffOperation.MAX_LINES, "x"));

        TextDiffResponse result = operation.execute(exactlyAtCap, exactlyAtCap);

        assertThat(result.lines()).hasSize(TextDiffOperation.MAX_LINES);
        assertThat(result.unchangedCount()).isEqualTo(TextDiffOperation.MAX_LINES);
    }
}
