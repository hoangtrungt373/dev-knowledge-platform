package com.ttg.devknowledgeplatform.devutils.service.impl.support;

import java.util.ArrayList;
import java.util.List;

/**
 * A lenient, keyword-driven pretty-printer/minifier for SQL — used by {@code SqlFormatOperation}.
 *
 * <p><b>This is deliberately not a real SQL-grammar parser</b>, the same "textual reformatter, not
 * a real grammar parser" trade-off {@link CurlyBraceFormatter} already makes for CSS/LESS/SCSS/JS,
 * for the same underlying reason: a real SQL parser would also need to pick a specific dialect
 * (MySQL/Postgres/SQL Server/Oracle all diverge on syntax details), which a general-purpose
 * formatting tool has no way to know in advance. Instead this fully re-tokenizes the input (string/
 * quoted-identifier literals and comments kept atomic, everything else split into words and single
 * punctuation/operator characters) and rebuilds the output from scratch — unlike
 * {@code CurlyBraceFormatter}, which has to preserve certain original line breaks verbatim for
 * JavaScript's ASI safety, SQL has no equivalent hazard, so nothing about the original whitespace
 * needs preserving at all.
 *
 * <p><b>Every recognized SQL keyword renders uppercase, and a comma-separated list splits one item
 * per line — reversing two previously deliberate design choices, per direct request, confirmed
 * against a full expected/actual example before implementing (the request's own size warranted
 * that: it reversed a previously-fixed, tested case-preservation bug and an explicitly-documented
 * "never splits a comma-separated list" rule, not an oversight in either case).</b> Three keyword
 * roles now exist, each classified in its own list below:
 * <ul>
 *   <li>{@link Role#TOP_LEVEL_CLAUSE} ({@code SELECT}/{@code FROM}/{@code WHERE}/{@code GROUP BY}/
 *       {@code ORDER BY}/{@code HAVING}/{@code LIMIT}/{@code OFFSET}/{@code INSERT INTO}/
 *       {@code VALUES}/{@code UPDATE}/{@code SET}/{@code DELETE FROM}/{@code UNION}/
 *       {@code UNION ALL}) — always starts its own fresh line, indented by live paren-nesting
 *       depth (same as before); its own body (everything until the next recognized keyword) then
 *       starts on a further-indented fresh line of its own.</li>
 *   <li>{@link Role#BODY_BREAK} (every {@code JOIN} variant, {@code AND}, {@code OR}) — starts a
 *       fresh line too, but *within* the current clause's own body indent (one level deeper than
 *       the clause keyword itself), not a brand-new top-level line — this is what keeps
 *       {@code LEFT JOIN posts p ON p.user_id = u.id} aligned with {@code FROM}'s other body lines
 *       rather than resetting back to column 0. {@code AND}/{@code OR} deliberately get *no* extra
 *       indent beyond that shared body level anymore (a previous version of this class gave them
 *       one extra level) — once a clause's first condition already lives on its own indented body
 *       line rather than staying inline with the clause keyword, giving {@code AND}/{@code OR}
 *       their own further indent stopped reading as "conditions under WHERE" and started reading
 *       as an arbitrary extra nesting level; aligning them with the rest of the body — the same
 *       treatment {@code JOIN} already gets — is simpler and reads as one list of conditions.</li>
 *   <li>{@link Role#INLINE} ({@code ON}, {@code AS}, {@code ASC}, {@code DESC}, {@code TRUE},
 *       {@code FALSE}, {@code NULL}, {@code NOT}, {@code IN}, {@code LIKE}, {@code IS},
 *       {@code BETWEEN}, {@code EXISTS}, {@code DISTINCT}) — rendered uppercase but never breaks a
 *       line on its own; this is what keeps {@code ON p.user_id = u.id} attached to its own
 *       {@code JOIN} line instead of wrapping onto a third line the way it used to. Deliberately a
 *       bounded, non-exhaustive list, not an attempt at full dialect-aware reserved-word coverage —
 *       extend it if a real gap is reported, the same "lenient heuristic, not a complete grammar"
 *       philosophy this whole class already follows. A function name that happens to also be a
 *       common SQL built-in ({@code COUNT}/{@code SUM}/{@code AVG}/...) is deliberately *not* in
 *       this list, or any of the three — it's an identifier, not a keyword, and there's no reliable
 *       parser-free way to tell "this word is being used as a function name" from "this word
 *       happens to also be a keyword" apart in general, so only words that are unambiguously always
 *       keywords (never legal identifiers/function names) get uppercased at all.</li>
 * </ul>
 *
 * <p><b>Comma-splitting is scoped to the current clause's own base paren depth, not blind.</b> A
 * comma triggers a line break (with a trailing comma on the line before it, matching the shape
 * every mainstream SQL formatter uses) only when the live paren depth has returned to exactly the
 * depth the *current* clause itself started at — a comma inside a function call's own argument list
 * ({@code count(a, b)}) or a subquery sits at a *deeper* paren depth than that, so it's correctly
 * left inline rather than corrupting the call/subquery. This is more tractable for SQL than the
 * identical-shaped problem {@code CurlyBraceFormatter} still declines to solve for CSS selector
 * lists — that class already has to track paren depth for other reasons, and a SQL list's own
 * commas are reliably at the clause's own base depth in a way a bare CSS selector list isn't.
 * {@link #matchKeywordPhrase} was tightened in the same pass to always prefer the *longest* matching
 * phrase at a position (e.g. {@code LEFT OUTER JOIN} over {@code LEFT JOIN}) regardless of each
 * list's own declaration order — the previous version relied on manually keeping longer phrases
 * listed before their own shorter prefixes, which happened to already be correct but wasn't
 * actually guaranteed by anything once phrases split across three separate lists.
 *
 * <p><b>Known, accepted imprecision, not chased further</b>: a scalar subquery appearing *inside* a
 * SELECT list item (e.g. {@code SELECT a, (SELECT COUNT(*) FROM y) AS cnt}) still has its own
 * nested {@code SELECT}/{@code FROM} broken onto their own fresh lines, the same as any other
 * subquery — which can read unusually for a single-value expression sitting inline in a column
 * list, but doesn't corrupt the SQL text itself, only its visual layout for this one nested shape.
 * Solving it generally would mean distinguishing "a clause keyword that's genuinely starting a new
 * top-level statement" from "a clause keyword sitting inside what's really just one scalar
 * expression" — real parsing, past what this lenient formatter is meant to be.
 *
 * <p>Line breaks are keyword- and comma-triggered now, not comma-triggered-never as before —
 * paren-nesting depth still drives indentation (2 spaces per level) exactly as it always has,
 * including a subquery's own {@code SELECT}/{@code FROM}/{@code WHERE} landing one level deeper
 * automatically purely from live paren-depth tracking, unchanged from before this pass.
 *
 * <p><b>Known, deliberate spacing trade-off: {@code (} never gets a leading space</b>, regardless
 * of what precedes it — correct and important for a function call ({@code COUNT(*)}, not
 * {@code COUNT (*)}), merely a stylistic (not incorrect) choice for a clause like
 * {@code VALUES(1, 2, 3)} (some style guides would write {@code VALUES (1, 2, 3)} instead) — since
 * there's no reliable, parser-free way to tell "this identifier is a function name" from "this
 * identifier is a keyword that conventionally gets a space before its parenthesis" apart, this
 * formatter picks the one rule that's never actually *wrong*, only occasionally a different
 * stylistic preference than some readers might expect.
 *
 * <p>Quote/identifier handling is dialect-agnostic and deliberately lenient: {@code '...'}
 * (standard SQL string literals), {@code "..."} (ANSI-standard quoted identifiers), and
 * {@code `...`} (MySQL-style quoted identifiers) are all treated as atomic tokens, copied
 * verbatim — each supports *either* a doubled quote ({@code ''}/{@code ""}/{@code ``}) *or* a
 * backslash escape to include a literal quote character, tolerating both rather than committing to
 * one dialect's actual rule. Comments — {@code -- line comment}, {@code # line comment} (MySQL),
 * {@code /* block comment *}{@code /} — are recognized and, in {@link #beautify}, preserved
 * verbatim; {@link #minify} strips them. {@code minify} also uppercases every recognized keyword
 * (for consistency with {@code beautify}) but never splits onto multiple lines — collapsing to one
 * line is the whole point of minifying. Never throws — an unterminated string/comment is copied to
 * the end of the input rather than treated as an error, the same lenient contract
 * {@code CurlyBraceFormatter} already follows.
 */
public final class SqlFormatter {

    private static final int INDENT_WIDTH = 2;

    /** Which of the three ways a recognized SQL keyword phrase affects line breaks — see this
     * class's own Javadoc for the full reasoning behind each. */
    private enum Role {
        TOP_LEVEL_CLAUSE,
        BODY_BREAK,
        INLINE
    }

    private record KeywordEntry(String[] words, Role role) {
    }

    private record KeywordMatch(String[] phrase, Role role) {
    }

    // Starts a brand-new top-level clause: its own fresh line at the current paren depth, with its
    // own body starting on a further-indented fresh line of its own. Multi-word phrases are listed
    // here purely for readability — matchKeywordPhrase() itself always prefers the longest match
    // regardless of any list's own ordering, so a shorter phrase can never accidentally "steal"
    // tokens that actually belong to a longer one.
    private static final List<KeywordEntry> TOP_LEVEL_CLAUSE_KEYWORDS = List.of(
            new KeywordEntry(new String[] {"GROUP", "BY"}, Role.TOP_LEVEL_CLAUSE),
            new KeywordEntry(new String[] {"ORDER", "BY"}, Role.TOP_LEVEL_CLAUSE),
            new KeywordEntry(new String[] {"INSERT", "INTO"}, Role.TOP_LEVEL_CLAUSE),
            new KeywordEntry(new String[] {"DELETE", "FROM"}, Role.TOP_LEVEL_CLAUSE),
            new KeywordEntry(new String[] {"UNION", "ALL"}, Role.TOP_LEVEL_CLAUSE),
            new KeywordEntry(new String[] {"SELECT"}, Role.TOP_LEVEL_CLAUSE),
            new KeywordEntry(new String[] {"FROM"}, Role.TOP_LEVEL_CLAUSE),
            new KeywordEntry(new String[] {"WHERE"}, Role.TOP_LEVEL_CLAUSE),
            new KeywordEntry(new String[] {"HAVING"}, Role.TOP_LEVEL_CLAUSE),
            new KeywordEntry(new String[] {"LIMIT"}, Role.TOP_LEVEL_CLAUSE),
            new KeywordEntry(new String[] {"OFFSET"}, Role.TOP_LEVEL_CLAUSE),
            new KeywordEntry(new String[] {"VALUES"}, Role.TOP_LEVEL_CLAUSE),
            new KeywordEntry(new String[] {"SET"}, Role.TOP_LEVEL_CLAUSE),
            new KeywordEntry(new String[] {"UPDATE"}, Role.TOP_LEVEL_CLAUSE),
            new KeywordEntry(new String[] {"UNION"}, Role.TOP_LEVEL_CLAUSE)
    );

    // Starts a fresh line within the CURRENT clause's own body indent, not a brand-new top-level
    // line — see this class's own Javadoc for why AND/OR no longer get an extra indent level
    // beyond that shared body level.
    private static final List<KeywordEntry> BODY_BREAK_KEYWORDS = List.of(
            new KeywordEntry(new String[] {"LEFT", "OUTER", "JOIN"}, Role.BODY_BREAK),
            new KeywordEntry(new String[] {"RIGHT", "OUTER", "JOIN"}, Role.BODY_BREAK),
            new KeywordEntry(new String[] {"LEFT", "JOIN"}, Role.BODY_BREAK),
            new KeywordEntry(new String[] {"RIGHT", "JOIN"}, Role.BODY_BREAK),
            new KeywordEntry(new String[] {"INNER", "JOIN"}, Role.BODY_BREAK),
            new KeywordEntry(new String[] {"FULL", "JOIN"}, Role.BODY_BREAK),
            new KeywordEntry(new String[] {"CROSS", "JOIN"}, Role.BODY_BREAK),
            new KeywordEntry(new String[] {"JOIN"}, Role.BODY_BREAK),
            new KeywordEntry(new String[] {"AND"}, Role.BODY_BREAK),
            new KeywordEntry(new String[] {"OR"}, Role.BODY_BREAK)
    );

    // Rendered uppercase, never breaks a line — a deliberately bounded, non-exhaustive list; see
    // this class's own Javadoc for why COUNT/SUM/AVG/etc. are deliberately NOT here (or in either
    // list above).
    private static final List<KeywordEntry> INLINE_KEYWORDS = List.of(
            new KeywordEntry(new String[] {"ON"}, Role.INLINE),
            new KeywordEntry(new String[] {"AS"}, Role.INLINE),
            new KeywordEntry(new String[] {"ASC"}, Role.INLINE),
            new KeywordEntry(new String[] {"DESC"}, Role.INLINE),
            new KeywordEntry(new String[] {"TRUE"}, Role.INLINE),
            new KeywordEntry(new String[] {"FALSE"}, Role.INLINE),
            new KeywordEntry(new String[] {"NULL"}, Role.INLINE),
            new KeywordEntry(new String[] {"NOT"}, Role.INLINE),
            new KeywordEntry(new String[] {"IN"}, Role.INLINE),
            new KeywordEntry(new String[] {"LIKE"}, Role.INLINE),
            new KeywordEntry(new String[] {"IS"}, Role.INLINE),
            new KeywordEntry(new String[] {"BETWEEN"}, Role.INLINE),
            new KeywordEntry(new String[] {"EXISTS"}, Role.INLINE),
            new KeywordEntry(new String[] {"DISTINCT"}, Role.INLINE)
    );

    private static final List<KeywordEntry> ALL_KEYWORDS = new ArrayList<>();

    static {
        ALL_KEYWORDS.addAll(TOP_LEVEL_CLAUSE_KEYWORDS);
        ALL_KEYWORDS.addAll(BODY_BREAK_KEYWORDS);
        ALL_KEYWORDS.addAll(INLINE_KEYWORDS);
    }

    private SqlFormatter() {
    }

    /** Reformats {@code input} with one clause per line (each clause keyword's own body further
     * indented, one list item per line) — see this class's own Javadoc for the full layout rules.
     * Never throws. */
    public static String beautify(String input) {
        return render(tokenize(input), false);
    }

    /** Strips comments and collapses the query onto (in practice, almost always) one line, keywords
     * still uppercased. Never throws. */
    public static String minify(String input) {
        return render(tokenize(input), true);
    }

    private static String render(List<String> tokens, boolean singleLine) {
        StringBuilder out = new StringBuilder();
        int parenDepth = 0;
        // The paren depth the CURRENT top-level clause's own body began at — a comma is a list-item
        // separator only when parenDepth has returned to exactly this value (see this class's own
        // Javadoc). Reset to the current parenDepth every time a new TOP_LEVEL_CLAUSE keyword is
        // matched, including inside a parenthesized subquery. -1 (impossible) until the first
        // clause is seen, so a comma before any recognized clause never tries to split.
        int clauseBaseDepth = -1;
        // True when the next appended content must start a fresh line at indentLevel — mirrors
        // CurlyBraceFormatter's own atLineStart pattern, adapted for SQL's flatter structure: unlike
        // that class, a single boolean-plus-pending-level pair is enough here, since SQL has no
        // ASI-style hazard requiring original line breaks to be preserved verbatim.
        boolean atLineStart = false;
        int indentLevel = 0;
        int i = 0;

        while (i < tokens.size()) {
            String token = tokens.get(i);

            if (singleLine && isComment(token)) {
                i++;
                continue;
            }

            KeywordMatch match = isPlainWord(token) ? matchKeywordPhrase(tokens, i) : null;

            if (!singleLine && match != null
                    && (match.role() == Role.TOP_LEVEL_CLAUSE || match.role() == Role.BODY_BREAK)) {
                // This keyword always starts its own fresh line, regardless of whatever atLineStart
                // already held (a comma or a previous clause may already have requested one at a
                // different level — this keyword's own requirement always wins for itself).
                atLineStart = true;
                indentLevel = match.role() == Role.TOP_LEVEL_CLAUSE ? parenDepth : clauseBaseDepth + 1;
            }

            if (!singleLine && atLineStart) {
                if (out.length() > 0) {
                    out.append('\n');
                }
                out.append(" ".repeat(Math.max(0, indentLevel * INDENT_WIDTH)));
                atLineStart = false;
            }

            if (match != null) {
                String upper = String.join(" ", match.phrase());
                appendToken(out, upper);
                i += match.phrase().length;
                if (!singleLine && match.role() == Role.TOP_LEVEL_CLAUSE) {
                    clauseBaseDepth = parenDepth;
                    atLineStart = true;
                    indentLevel = parenDepth + 1;
                }
                continue;
            }

            if (token.equals("(")) {
                appendToken(out, token);
                parenDepth++;
            } else if (token.equals(")")) {
                parenDepth = Math.max(0, parenDepth - 1);
                appendToken(out, token);
            } else if (token.equals(",")) {
                appendToken(out, token);
                if (!singleLine && clauseBaseDepth >= 0 && parenDepth == clauseBaseDepth) {
                    atLineStart = true;
                    indentLevel = clauseBaseDepth + 1;
                }
            } else if (token.equals(";")) {
                appendToken(out, token);
                clauseBaseDepth = -1;
                if (!singleLine && i + 1 < tokens.size()) {
                    atLineStart = true;
                    indentLevel = 0;
                }
            } else {
                appendToken(out, token);
            }
            i++;
        }
        return out.toString().strip();
    }

    private static void appendToken(StringBuilder out, String token) {
        if (needsLeadingSpace(out, token)) {
            out.append(' ');
        }
        out.append(token);
    }

    private static boolean needsLeadingSpace(StringBuilder out, String next) {
        if (out.length() == 0) {
            return false;
        }
        char last = out.charAt(out.length() - 1);
        if (last == '(' || last == '\n' || last == '.' || last == ' ') {
            return false;
        }
        // These always hug whatever precedes them — see this class's own Javadoc for the `(`
        // trade-off specifically.
        return !(next.equals(")") || next.equals(",") || next.equals(";") || next.equals(".") || next.equals("("));
    }

    /** Whether {@code token} is one of {@link #tokenize}'s own comment tokens (stored verbatim,
     * markers included) — used only by {@link #minify} to strip them; {@link #beautify} keeps them
     * unconditionally, so this is never consulted there. */
    private static boolean isComment(String token) {
        return token.startsWith("--") || token.startsWith("#") || token.startsWith("/*");
    }

    private static boolean isPlainWord(String token) {
        if (token.isEmpty()) {
            return false;
        }
        char c = token.charAt(0);
        return Character.isLetter(c) || c == '_';
    }

    /** Finds the recognized keyword phrase starting at {@code start}, if any — always the
     * *longest* matching phrase across all three keyword lists (e.g. {@code LEFT OUTER JOIN} over
     * {@code LEFT JOIN}), regardless of any list's own declaration order (see this class's own
     * Javadoc for why this is now an explicit guarantee rather than a manually-maintained one). */
    private static KeywordMatch matchKeywordPhrase(List<String> tokens, int start) {
        KeywordMatch best = null;
        for (KeywordEntry entry : ALL_KEYWORDS) {
            String[] phrase = entry.words();
            if (start + phrase.length > tokens.size()) {
                continue;
            }
            boolean matches = true;
            for (int k = 0; k < phrase.length; k++) {
                if (!phrase[k].equalsIgnoreCase(tokens.get(start + k))) {
                    matches = false;
                    break;
                }
            }
            if (matches && (best == null || phrase.length > best.phrase().length)) {
                best = new KeywordMatch(phrase, entry.role());
            }
        }
        return best;
    }

    private static List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        int n = input.length();
        int i = 0;

        while (i < n) {
            char c = input.charAt(i);

            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '-' && i + 1 < n && input.charAt(i + 1) == '-') {
                int end = TextScanning.indexOfOrEnd(input, '\n', i + 2);
                tokens.add(input.substring(i, end));
                i = end;
                continue;
            }
            if (c == '#') {
                int end = TextScanning.indexOfOrEnd(input, '\n', i + 1);
                tokens.add(input.substring(i, end));
                i = end;
                continue;
            }
            if (c == '/' && i + 1 < n && input.charAt(i + 1) == '*') {
                int end = TextScanning.indexOfOrEnd(input, "*/", i + 2);
                tokens.add(input.substring(i, end));
                i = end;
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                int end = scanQuoted(input, i, c);
                tokens.add(input.substring(i, end));
                i = end;
                continue;
            }
            if ((c == '<' || c == '>' || c == '!') && i + 1 < n && input.charAt(i + 1) == '=') {
                tokens.add(input.substring(i, i + 2));
                i += 2;
                continue;
            }
            if (c == '<' && i + 1 < n && input.charAt(i + 1) == '>') {
                tokens.add("<>");
                i += 2;
                continue;
            }
            if (c == '|' && i + 1 < n && input.charAt(i + 1) == '|') {
                tokens.add("||");
                i += 2;
                continue;
            }
            if (Character.isLetterOrDigit(c) || c == '_') {
                int j = i;
                while (j < n && (Character.isLetterOrDigit(input.charAt(j)) || input.charAt(j) == '_')) {
                    j++;
                }
                tokens.add(input.substring(i, j));
                i = j;
                continue;
            }
            tokens.add(String.valueOf(c));
            i++;
        }
        return tokens;
    }

    /** Scans a quoted literal/identifier starting at {@code start} (the opening quote), tolerating
     * *either* a doubled quote or a backslash escape to include a literal quote character — see
     * this class's own Javadoc for why both are accepted rather than committing to one dialect's
     * actual rule. An unterminated literal is copied to the end of the input rather than treated as
     * an error. */
    private static int scanQuoted(String s, int start, char quote) {
        int n = s.length();
        int i = start + 1;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < n) {
                i += 2;
                continue;
            }
            if (c == quote) {
                if (i + 1 < n && s.charAt(i + 1) == quote) {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return n;
    }
}
