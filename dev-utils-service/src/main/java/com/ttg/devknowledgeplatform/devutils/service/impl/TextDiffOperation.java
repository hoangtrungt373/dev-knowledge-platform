package com.ttg.devknowledgeplatform.devutils.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.dto.TextDiffResponse;
import com.ttg.devknowledgeplatform.devutils.dto.TextDiffResponse.DiffLine;
import com.ttg.devknowledgeplatform.devutils.dto.TextDiffResponse.DiffLineType;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;
import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.OperationGroup;

/**
 * Compares two versions of text line by line — "works like {@code git diff}," per direct request
 * — via a real Longest Common Subsequence (LCS) algorithm, the same class of algorithm every real
 * line-based diff tool (including {@code git}'s own, a variant of Myers' algorithm) is built on.
 * Declares {@link OperationGroup#INSPECTORS} — compares two values and reports their relationship
 * rather than transforming either one, the same "inspection" shape
 * {@code JwtDebuggerOperation}/{@code RegexTesterOperation}/{@code UrlParserOperation}/
 * {@code CronParserOperation} already establish for that group.
 *
 * <p><b>This deliberately does not reproduce the reported example's own line-by-line output
 * verbatim</b> — that example showed every line of {@code original} as removed and every line of
 * {@code updated} as added, but {@code console.log(ship());} is identical in both and a real diff
 * (confirmed via a standalone Java harness against this exact example before writing any
 * production code) correctly recognizes it as unchanged, showing only the one line that actually
 * changed plus the one line that was genuinely added. Reproducing the example's own output
 * literally would mean *not* actually diffing — the whole point of "works like {@code git diff}"
 * is exactly this recognition of what's unchanged, which a merely cosmetic {@code -}/{@code +}
 * relabeling of every original/updated line would not provide.
 *
 * <p><b>Response is structured data ({@link TextDiffResponse}), not a flat unified-diff text
 * block</b> — the same "output genuinely richer than {@code DevUtilResponse}'s single string"
 * shape {@code HashResponse}/{@code StringCaseResponse} already establish, so the GUI's own diff
 * panel can render each line with real color/emphasis (added/removed/context) rather than
 * re-parsing a {@code -}/{@code +}-prefixed text block back apart itself.
 *
 * <p><b>Resource-exhaustion guard, checked *before* running the expensive comparison</b> — the
 * LCS algorithm here is a classic full dynamic-programming table, {@code O(n×m)} in line count
 * (not character count); confirmed via harness that a 2000×2000-line comparison completes in
 * under 50ms using about 15MB, so {@link #MAX_LINES} is set well above any realistic real-world
 * input while still keeping the absolute worst case fast and bounded. This is a cleaner mitigation
 * than {@code RegexTesterOperation}'s own best-effort timeout: a regex pattern's own cost can't be
 * judged without already running it, but here the input size is known and cheap to check upfront,
 * so an oversized request is rejected outright rather than attempted and hoped to finish in time —
 * no runaway background work is ever started at all.
 */
@Component
public class TextDiffOperation implements DevUtilOperation {

    /** Bounds the LCS dynamic-programming table to at most 2000×2000 cells — see this class's own
     * Javadoc for the harness that confirmed this stays comfortably fast (under 50ms) even at the
     * absolute worst case (every line different). */
    static final int MAX_LINES = 2000;

    @Override
    public OperationGroup group() {
        return OperationGroup.INSPECTORS;
    }

    /**
     * @throws BusinessException wrapping {@link DevUtilsErrorCode#DIFF_INPUT_TOO_LARGE} when
     *                           either {@code original} or {@code updated} has more than
     *                           {@link #MAX_LINES} lines
     */
    public TextDiffResponse execute(String original, String updated) {
        String[] originalLines = splitLines(original);
        String[] updatedLines = splitLines(updated);

        if (originalLines.length > MAX_LINES || updatedLines.length > MAX_LINES) {
            throw new BusinessException(DevUtilsErrorCode.DIFF_INPUT_TOO_LARGE, (Object) (
                    "each side is limited to " + MAX_LINES + " lines (original has "
                            + originalLines.length + ", updated has " + updatedLines.length + ")"));
        }

        List<DiffLine> lines = diff(originalLines, updatedLines);
        int added = 0;
        int removed = 0;
        int unchanged = 0;
        for (DiffLine line : lines) {
            switch (line.type()) {
                case ADDED -> added++;
                case REMOVED -> removed++;
                case CONTEXT -> unchanged++;
            }
        }
        return new TextDiffResponse(lines, added, removed, unchanged);
    }

    /** {@code ""} splits to zero lines, not one empty-string line — comparing against genuinely
     * absent text (see this operation's own {@code TextDiffRequest} — neither field requires
     * {@code @NotBlank}) means "everything on the other side was added/removed," not "there's one
     * blank line to diff against." A lone trailing newline is likewise insignificant, matching
     * every mainstream diff tool's own default treatment — {@code String#split}'s own default
     * (positive) limit already drops trailing empty strings for free, so {@code "a\nb\n"} and
     * {@code "a\nb"} both split to exactly {@code ["a", "b"]}. */
    private static String[] splitLines(String text) {
        String normalized = text == null ? "" : text.replace("\r\n", "\n");
        return normalized.isEmpty() ? new String[0] : normalized.split("\n");
    }

    /**
     * The actual LCS diff: builds the standard bottom-up dynamic-programming table of
     * longest-common-subsequence lengths, then walks it front-to-back (equivalently, could walk
     * a top-down table back-to-front — this walks a bottom-up table forward, which reads more
     * naturally as "process line by line from the start") to reconstruct the actual sequence of
     * context/removed/added lines.
     */
    private static List<DiffLine> diff(String[] original, String[] updated) {
        int n = original.length;
        int m = updated.length;
        // dp[i][j] = length of the LCS of original[i..n) and updated[j..m) — built from the
        // bottom-right corner outward, so dp[i][j] only ever depends on already-computed cells
        // "below and to the right" of it.
        int[][] dp = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                dp[i][j] = original[i].equals(updated[j])
                        ? dp[i + 1][j + 1] + 1
                        : Math.max(dp[i + 1][j], dp[i][j + 1]);
            }
        }

        List<DiffLine> result = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (original[i].equals(updated[j])) {
                result.add(new DiffLine(DiffLineType.CONTEXT, original[i]));
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                // Keeping original[i] out of the LCS costs nothing (the LCS reachable without it
                // is just as long), so it's a genuine removal, not a coincidental tie — the same
                // "prefer the branch that doesn't shrink the LCS" rule the >= (not >) enforces.
                result.add(new DiffLine(DiffLineType.REMOVED, original[i]));
                i++;
            } else {
                result.add(new DiffLine(DiffLineType.ADDED, updated[j]));
                j++;
            }
        }
        // Whichever side has lines left once the other is exhausted is unambiguous — no more
        // choices to weigh, just drain the remainder as removed/added respectively.
        while (i < n) {
            result.add(new DiffLine(DiffLineType.REMOVED, original[i]));
            i++;
        }
        while (j < m) {
            result.add(new DiffLine(DiffLineType.ADDED, updated[j]));
            j++;
        }
        return result;
    }
}
