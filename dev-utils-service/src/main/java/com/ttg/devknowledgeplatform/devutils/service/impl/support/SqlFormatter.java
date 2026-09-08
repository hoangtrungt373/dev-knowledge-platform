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
 * <p><b>Line breaks are keyword-triggered, not comma-triggered.</b> A recognized clause keyword —
 * {@code SELECT}/{@code FROM}/{@code WHERE}/{@code GROUP BY}/{@code ORDER BY}/{@code HAVING}/
 * {@code LIMIT}/{@code OFFSET}/{@code INSERT INTO}/{@code VALUES}/{@code UPDATE}/{@code SET}/
 * {@code DELETE FROM}/{@code UNION}/{@code UNION ALL}/every {@code JOIN} variant/{@code ON}/
 * {@code AND}/{@code OR} — starts a new line, indented by the current parenthesis-nesting depth
 * (2 spaces per level; {@code AND}/{@code OR} get one extra level, the common convention for
 * indenting a {@code WHERE} clause's own conditions). A parenthesized subquery's own
 * {@code SELECT}/{@code FROM}/{@code WHERE} get indented one level deeper automatically, since
 * indentation is driven by live paren-depth tracking, not by which clause "owns" them.
 * <b>Deliberately does not split a comma-separated column/value list onto one item per line</b> —
 * unlike the clause keywords above, a bare comma has no context-free way to tell a {@code SELECT}
 * column list apart from a function call's argument list ({@code COUNT(a, b)}) or a value tuple
 * ({@code VALUES (1, 2, 3)}) without real parsing; splitting blindly would corrupt those. This
 * mirrors {@code CurlyBraceFormatter}'s own identical reasoning for never splitting CSS selector
 * lists on a bare comma either.
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
 * verbatim; {@link #minify} strips them. Never throws — an unterminated string/comment is copied
 * to the end of the input rather than treated as an error, the same lenient contract
 * {@code CurlyBraceFormatter} already follows.
 */
public final class SqlFormatter {

    private static final int INDENT_WIDTH = 2;

    // An exact, full-length match against consecutive tokens is required regardless of this list's
    // own ordering — a shorter phrase can never accidentally "steal" tokens that actually belong to
    // a longer one (e.g. matching {"LEFT","JOIN"} at a "LEFT OUTER JOIN" position would require
    // token[i+1] to literally equal "JOIN", which it doesn't — it's "OUTER") — so list order here
    // is purely for readability, not correctness.
    private static final List<String[]> LINE_BREAK_KEYWORDS = List.of(
            new String[] {"LEFT", "OUTER", "JOIN"},
            new String[] {"RIGHT", "OUTER", "JOIN"},
            new String[] {"GROUP", "BY"},
            new String[] {"ORDER", "BY"},
            new String[] {"INSERT", "INTO"},
            new String[] {"DELETE", "FROM"},
            new String[] {"UNION", "ALL"},
            new String[] {"LEFT", "JOIN"},
            new String[] {"RIGHT", "JOIN"},
            new String[] {"INNER", "JOIN"},
            new String[] {"FULL", "JOIN"},
            new String[] {"CROSS", "JOIN"},
            new String[] {"SELECT"},
            new String[] {"FROM"},
            new String[] {"WHERE"},
            new String[] {"HAVING"},
            new String[] {"LIMIT"},
            new String[] {"OFFSET"},
            new String[] {"VALUES"},
            new String[] {"SET"},
            new String[] {"UPDATE"},
            new String[] {"UNION"},
            new String[] {"JOIN"},
            new String[] {"ON"},
            new String[] {"AND"},
            new String[] {"OR"}
    );

    private SqlFormatter() {
    }

    /** Reformats {@code input} with one clause per line, indented by paren-nesting depth. Never
     * throws. */
    public static String beautify(String input) {
        return render(tokenize(input), false);
    }

    /** Strips comments and collapses the query onto (in practice, almost always) one line. Never
     * throws. */
    public static String minify(String input) {
        return render(tokenize(input), true);
    }

    private static String render(List<String> tokens, boolean singleLine) {
        StringBuilder out = new StringBuilder();
        int parenDepth = 0;
        int i = 0;

        while (i < tokens.size()) {
            String token = tokens.get(i);

            if (singleLine && isComment(token)) {
                i++;
                continue;
            }

            String[] phrase = isPlainWord(token) ? matchKeywordPhrase(tokens, i) : null;

            if (phrase != null) {
                boolean isAndOr = phrase.length == 1
                        && (phrase[0].equalsIgnoreCase("AND") || phrase[0].equalsIgnoreCase("OR"));
                // Append the *original* tokens (whatever case the input actually used), not
                // `phrase`'s own canonical uppercase words — `phrase` exists only to identify
                // *which* keyword matched, not to dictate output casing. A real bug, caught by a
                // failing test: this used to emit `phrase[k]` here, silently upper-casing every
                // recognized keyword regardless of how the input had written it.
                String firstWord = tokens.get(i);
                if (singleLine) {
                    appendToken(out, firstWord);
                } else {
                    if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') {
                        out.append('\n');
                    }
                    out.append(" ".repeat(Math.max(0, parenDepth * INDENT_WIDTH + (isAndOr ? INDENT_WIDTH : 0))));
                    out.append(firstWord);
                }
                for (int k = 1; k < phrase.length; k++) {
                    out.append(' ').append(tokens.get(i + k));
                }
                i += phrase.length;
                continue;
            }

            if (token.equals("(")) {
                appendToken(out, token);
                parenDepth++;
            } else if (token.equals(")")) {
                parenDepth = Math.max(0, parenDepth - 1);
                appendToken(out, token);
            } else if (token.equals(";")) {
                appendToken(out, token);
                if (!singleLine && i + 1 < tokens.size()) {
                    out.append('\n');
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

    private static String[] matchKeywordPhrase(List<String> tokens, int start) {
        for (String[] phrase : LINE_BREAK_KEYWORDS) {
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
            if (matches) {
                return phrase;
            }
        }
        return null;
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
                int end = indexOfOrEnd(input, '\n', i + 2);
                tokens.add(input.substring(i, end));
                i = end;
                continue;
            }
            if (c == '#') {
                int end = indexOfOrEnd(input, '\n', i + 1);
                tokens.add(input.substring(i, end));
                i = end;
                continue;
            }
            if (c == '/' && i + 1 < n && input.charAt(i + 1) == '*') {
                int end = indexOfOrEnd(input, "*/", i + 2);
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

    private static int indexOfOrEnd(String s, String needle, int from) {
        int idx = s.indexOf(needle, from);
        return idx < 0 ? s.length() : idx + needle.length();
    }

    private static int indexOfOrEnd(String s, char needle, int from) {
        int idx = s.indexOf(needle, from);
        return idx < 0 ? s.length() : idx;
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
