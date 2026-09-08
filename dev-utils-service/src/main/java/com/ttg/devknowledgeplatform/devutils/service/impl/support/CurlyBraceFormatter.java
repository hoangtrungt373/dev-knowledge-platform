package com.ttg.devknowledgeplatform.devutils.service.impl.support;

/**
 * A lenient, brace/semicolon-driven pretty-printer and minifier shared by {@code CssOperation},
 * {@code LessOperation}, {@code ScssOperation}, and {@code JsOperation} — CSS, LESS, SCSS, and
 * JavaScript are all "curly-brace languages" whose block structure is delimited the same way
 * ({@code {}}/{@code ;}), so one shared textual reformatter covers all four rather than four
 * near-identical copies.
 *
 * <p><b>This is deliberately not a real grammar parser</b> — unlike {@code JsonFormatOperation}'s
 * Jackson-backed JSON parsing or {@code XmlOperation}'s JAXP-backed XML parsing, there is no single
 * shared grammar across CSS/LESS/SCSS/JS a Java library could parse uniformly (LESS/SCSS extend
 * CSS with variables/nesting/mixins that a strict CSS parser rejects; JS has its own grammar
 * entirely). Building four real per-language parsers is a fundamentally bigger undertaking (that's
 * what Prettier/Terser/UglifyJS actually do) — this instead reformats by tracking only three
 * structural signals every one of these languages shares: brace nesting depth, statement-ending
 * semicolons, and comment/string literals (kept atomic so their contents are never touched). Same
 * "lenient, best-effort, no invalid-input failure path" trade-off {@code HtmlBeautifyOperation}
 * already makes for HTML via jsoup — {@link #beautify}/{@link #minify} never throw.
 *
 * <p><b>Known limitation worth calling out explicitly (JS-specific): Automatic Semicolon
 * Insertion (ASI).</b> A textual reformatter with no real JS parser cannot know that {@code
 * return\nx;} means {@code return; x;} (the line break after a restricted-production keyword like
 * {@code return}/{@code break}/{@code continue}/{@code throw} triggers ASI) — collapsing that line
 * break into a space would silently change {@code return\nx} into {@code return x}, altering
 * runtime behavior. Both {@link #beautify} and {@link #minify} guard against this the same way:
 * whenever a run of original whitespace contains a real line break and sits between two ordinary
 * (non-punctuation) characters, that line break is preserved verbatim rather than collapsed to a
 * space or dropped — this doesn't require recognizing the specific ASI-restricted keywords by name,
 * it just never removes a line break where doing so could be semantically significant. The
 * practical cost: {@code minify} does not guarantee single-line output for JS the same way it
 * mostly does for CSS/LESS/SCSS (whose declarations are semicolon/brace-delimited at every
 * boundary, so most of their whitespace sits next to a safely-droppable punctuation character
 * anyway) — the same "not a true single-line guarantee" caveat {@code HtmlBeautifyOperation}'s own
 * Javadoc already documents for jsoup's minify mode.
 *
 * <p>Also known and deliberate: {@code beautify} never normalizes the spacing around a {@code :}
 * (e.g. {@code color:red} is left exactly as written, not turned into {@code color: red}) — a
 * blanket "always insert a space after {@code :}" rule would corrupt a CSS/LESS/SCSS pseudo-class
 * selector like {@code :hover}/{@code ::before}, which relies on *no* space between the colon and
 * what follows; telling a declaration's colon apart from a selector's would need real grammar
 * awareness this formatter deliberately doesn't have. {@code minify} does the opposite for the same
 * reason it's safe there: {@code :} is one of this class's own {@link #SAFE_BOUNDARY_CHARS}, so any
 * existing space around it is stripped, same as around {@code ;}/{@code {}/{@code }}.
 *
 * <p>Also known and deliberate: this formatter never invents structure the source didn't already
 * signal via whitespace — e.g. a comma-separated selector list already split across lines
 * ({@code h1,\nh2 {...}}) stays split (each original line break between ordinary characters is
 * preserved, per the ASI-safety rule above), but one written on a single line
 * ({@code h1,h2{...}}) is not proactively re-split. Blindly splitting on every comma would corrupt
 * a function call or argument list ({@code rgba(0, 0, 0, .5)}, a JS array literal {@code [1,2,3]})
 * that also uses commas but isn't a selector list — telling those apart needs real grammar
 * awareness this formatter deliberately doesn't have.
 *
 * <p><b>An unquoted {@code url(...)} argument is treated as an atomic span, the same way a quoted
 * string literal already is — a real bug, caught after the fact rather than by a test.</b> CSS
 * commonly writes {@code url(http://example.com/x.png)} with no quotes at all; without this, the
 * comment scanner's bare "any {@code //} starts a line comment" rule would misread the
 * {@code //} in {@code http://} as a comment start. In {@code beautify} that corrupts brace-depth
 * tracking for everything after it (a trailing {@code }} inside the "comment" span never
 * decrements {@code depth}); in {@code minify} it's worse — a single-line, semicolon-free
 * declaration has no {@code \n} to stop the scan, so everything from the {@code //} to the end of
 * the input is silently discarded. {@link #isUrlFunctionStart}/{@link #scanUrlFunctionArg} guard
 * against this by consuming the whole unquoted argument (up to its closing {@code )}) as one
 * span before the comment scanner ever gets a chance to look inside it. A *quoted* argument
 * ({@code url("...")}) is left to the ordinary string-literal handling instead — it's already
 * protected there.
 */
public final class CurlyBraceFormatter {

    private static final int INDENT_WIDTH = 2;
    // Whitespace touching one of these characters never needs a separating space — collapsing it
    // to nothing is always safe, since none of these can combine with adjacent whitespace to form
    // a different, longer token the way two adjacent word/operator characters could.
    private static final String SAFE_BOUNDARY_CHARS = "{};:,()[]";

    private CurlyBraceFormatter() {
    }

    /** Reformats {@code input} with 2-space indentation per brace-nesting level. Never throws. */
    public static String beautify(String input) {
        StringBuilder out = new StringBuilder();
        int depth = 0;
        boolean atLineStart = true;
        int n = input.length();
        int i = 0;

        while (i < n) {
            char c = input.charAt(i);

            if ((c == 'u' || c == 'U') && isUrlFunctionStart(input, i) && !isQuoteAt(input, i + 4)) {
                int end = scanUrlFunctionArg(input, i);
                if (atLineStart) {
                    appendIndent(out, depth);
                    atLineStart = false;
                }
                out.append(input, i, end);
                i = end;
                continue;
            }
            if (c == '/' && i + 1 < n && input.charAt(i + 1) == '*') {
                int end = indexOfOrEnd(input, "*/", i + 2);
                if (atLineStart) {
                    appendIndent(out, depth);
                    atLineStart = false;
                }
                out.append(input, i, end);
                i = end;
                continue;
            }
            if (c == '/' && i + 1 < n && input.charAt(i + 1) == '/') {
                int end = indexOfOrEnd(input, '\n', i + 2);
                if (atLineStart) {
                    appendIndent(out, depth);
                }
                out.append(input, i, end);
                out.append('\n');
                atLineStart = true;
                i = end;
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                int end = scanStringLiteral(input, i, c);
                if (atLineStart) {
                    appendIndent(out, depth);
                    atLineStart = false;
                }
                out.append(input, i, end);
                i = end;
                continue;
            }
            if (c == '{') {
                if (atLineStart) {
                    appendIndent(out, depth);
                } else {
                    trimTrailingSpaces(out);
                    out.append(' ');
                }
                out.append("{\n");
                depth++;
                atLineStart = true;
                i++;
                continue;
            }
            if (c == '}') {
                depth = Math.max(0, depth - 1);
                if (!atLineStart) {
                    trimTrailingSpaces(out);
                    out.append('\n');
                }
                appendIndent(out, depth);
                out.append("}\n");
                atLineStart = true;
                i++;
                continue;
            }
            if (c == ';') {
                trimTrailingSpaces(out);
                out.append(";\n");
                atLineStart = true;
                i++;
                continue;
            }
            if (Character.isWhitespace(c)) {
                int j = i;
                boolean hasNewline = false;
                while (j < n && Character.isWhitespace(input.charAt(j))) {
                    if (input.charAt(j) == '\n') {
                        hasNewline = true;
                    }
                    j++;
                }
                if (!atLineStart) {
                    if (hasNewline) {
                        // See this class's own Javadoc — preserving the line break (not collapsing
                        // to a space) is what keeps JS's Automatic Semicolon Insertion behavior
                        // intact, and it's also what lets an already-multi-line selector list stay
                        // multi-line.
                        trimTrailingSpaces(out);
                        out.append('\n');
                        atLineStart = true;
                    } else {
                        out.append(' ');
                    }
                }
                i = j;
                continue;
            }
            if (atLineStart) {
                appendIndent(out, depth);
                atLineStart = false;
            }
            out.append(c);
            i++;
        }

        return out.toString().strip();
    }

    /**
     * Strips comments and non-essential whitespace. Not a true single-line guarantee for every
     * input — see this class's own Javadoc for the JS/ASI-safety reasoning behind that. Never
     * throws.
     */
    public static String minify(String input) {
        StringBuilder out = new StringBuilder();
        int n = input.length();
        int i = 0;

        while (i < n) {
            char c = input.charAt(i);

            if ((c == 'u' || c == 'U') && isUrlFunctionStart(input, i) && !isQuoteAt(input, i + 4)) {
                int end = scanUrlFunctionArg(input, i);
                out.append(input, i, end);
                i = end;
                continue;
            }
            if (c == '/' && i + 1 < n && input.charAt(i + 1) == '*') {
                i = indexOfOrEnd(input, "*/", i + 2);
                continue;
            }
            if (c == '/' && i + 1 < n && input.charAt(i + 1) == '/') {
                i = indexOfOrEnd(input, '\n', i + 2);
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                int end = scanStringLiteral(input, i, c);
                out.append(input, i, end);
                i = end;
                continue;
            }
            if (Character.isWhitespace(c)) {
                int j = i;
                boolean hasNewline = false;
                while (j < n && Character.isWhitespace(input.charAt(j))) {
                    if (input.charAt(j) == '\n') {
                        hasNewline = true;
                    }
                    j++;
                }
                char prev = out.length() > 0 ? out.charAt(out.length() - 1) : '\0';
                char next = j < n ? input.charAt(j) : '\0';
                if (isSafeBoundary(prev) || isSafeBoundary(next)) {
                    // Drop entirely — neither side needs a separator (e.g. around `{`, `}`, `;`,
                    // `:`, `,`, parens/brackets).
                } else if (hasNewline) {
                    // See this class's own Javadoc's ASI-safety note — never silently turn a real
                    // line break between two ordinary tokens into "no separator at all" or even a
                    // plain space; only an actual newline preserves the original meaning exactly.
                    out.append('\n');
                } else {
                    out.append(' ');
                }
                i = j;
                continue;
            }
            out.append(c);
            i++;
        }

        return out.toString().strip();
    }

    /** True when {@code s.charAt(i)} starts a {@code url(} function token (case-insensitive, not
     * itself the tail of a longer identifier like {@code myurl(}). Doesn't check what follows —
     * see {@link #scanUrlFunctionArg}'s own caller for the quoted-vs-unquoted split. */
    private static boolean isUrlFunctionStart(String s, int i) {
        int n = s.length();
        if (i + 4 > n) {
            return false;
        }
        if (Character.toLowerCase(s.charAt(i)) != 'u'
                || Character.toLowerCase(s.charAt(i + 1)) != 'r'
                || Character.toLowerCase(s.charAt(i + 2)) != 'l'
                || s.charAt(i + 3) != '(') {
            return false;
        }
        if (i > 0) {
            char prev = s.charAt(i - 1);
            if (Character.isLetterOrDigit(prev) || prev == '_' || prev == '-') {
                return false;
            }
        }
        return true;
    }

    private static boolean isQuoteAt(String s, int i) {
        return i < s.length() && (s.charAt(i) == '\'' || s.charAt(i) == '"' || s.charAt(i) == '`');
    }

    /** Scans an unquoted {@code url(...)} argument starting at the {@code u} of {@code "url("}
     * (already confirmed present by {@link #isUrlFunctionStart}), through and including its
     * closing {@code )}. A real unquoted CSS {@code url()} value can't itself contain a right
     * paren, so a plain scan to the next {@code )} is sufficient — this exists purely so the
     * comment/string scanners never look inside an unquoted {@code url()} argument, where a bare
     * {@code //} (e.g. {@code url(http://...)}) is not a comment. See this class's own Javadoc
     * for the bug this fixes. */
    private static int scanUrlFunctionArg(String s, int start) {
        int n = s.length();
        int i = start + 4;
        while (i < n && s.charAt(i) != ')') {
            i++;
        }
        return i < n ? i + 1 : n;
    }

    private static boolean isSafeBoundary(char c) {
        return c == '\0' || SAFE_BOUNDARY_CHARS.indexOf(c) >= 0;
    }

    private static int indexOfOrEnd(String s, String needle, int from) {
        int idx = s.indexOf(needle, from);
        return idx < 0 ? s.length() : idx + needle.length();
    }

    private static int indexOfOrEnd(String s, char needle, int from) {
        int idx = s.indexOf(needle, from);
        return idx < 0 ? s.length() : idx;
    }

    /** Scans a quoted string literal starting at {@code start} (the opening quote), honoring
     * backslash escapes. An unterminated literal is treated leniently — copied to the end of the
     * input rather than treated as an error, the same "never throws" contract this whole class
     * follows. */
    private static int scanStringLiteral(String s, int start, char quote) {
        int n = s.length();
        int i = start + 1;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < n) {
                i += 2;
                continue;
            }
            if (c == quote) {
                return i + 1;
            }
            i++;
        }
        return n;
    }

    private static void appendIndent(StringBuilder out, int depth) {
        out.append(" ".repeat(Math.max(0, depth) * INDENT_WIDTH));
    }

    private static void trimTrailingSpaces(StringBuilder out) {
        int end = out.length();
        while (end > 0 && out.charAt(end - 1) == ' ') {
            end--;
        }
        out.setLength(end);
    }
}
