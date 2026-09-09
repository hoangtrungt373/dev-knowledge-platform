package com.ttg.devknowledgeplatform.devutils.service.impl.support;

import java.util.ArrayList;
import java.util.List;

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
 * <p><b>{@code beautify} now normalizes the spacing after a declaration's {@code :} (e.g.
 * {@code color:red} becomes {@code color: red}), while leaving a selector's own {@code :}
 * untouched (e.g. {@code .a:hover {}}/{@code &:hover {}} stay exactly as written) — a real bug,
 * reported directly against a real CSS example, fixed once it became clear the two really are
 * locally distinguishable without a full grammar parser after all.</b> The two contexts a bare
 * colon can appear in are told apart with two bounded, still-lenient checks rather than one
 * blanket rule:
 * <ul>
 *   <li>A running {@code parenDepth} counter (incremented/decremented alongside brace
 *       {@code depth}, but for {@code (}/{@code )}) — a colon already inside an open paren (e.g.
 *       a media feature, {@code @media (min-width: 768px)}, or an SCSS map key) is always treated
 *       as declaration-style and gets a space, regardless of what follows.</li>
 *   <li>Otherwise, {@link #selectorFollowsBeforeStatementEnd} looks forward from the colon (past
 *       any intervening string literal, comment, or unquoted {@code url(...)} argument, atomic the
 *       same way the main scan already treats them) for whichever of {@code {}/{@code ;}/{@code }}
 *       comes first: a {@code {} means this colon started a nested rule's own selector (e.g.
 *       {@code &:hover {}, no space); a {@code ;}/{@code }}/end of input means it ended a
 *       declaration's property name (e.g. {@code color:red;}, gets a space).</li>
 * </ul>
 * <p>Both checks only ever look forward from the colon's own position to the next natural
 * statement boundary — never across an entire rule or beyond — so this stays a bounded, local
 * heuristic, not a step toward a real CSS grammar. <b>Known remaining imprecision, accepted rather
 * than chased further:</b> a pseudo-class function argument that itself contained an unescaped
 * {@code {}/{@code ;}/{@code }} (not legal in real CSS) would still be misread the same way a
 * lookahead-based heuristic always could; this hasn't come up in practice. When a space is added,
 * any existing run of spaces/tabs right after the colon is first collapsed away so the source's
 * own spacing (already-spaced, doubly-spaced, or unspaced) always normalizes to exactly one space
 * — never a doubled space. {@code minify} needed no matching change: {@code :} was already one of
 * this class's own {@link #SAFE_BOUNDARY_CHARS}, so it already strips *all* surrounding whitespace
 * unconditionally, which is correct for both contexts in minified output.
 *
 * <p><b>A blank line now separates a nested rule from whatever content already precedes it in the
 * same block — first added for two top-level rules, then generalized after a second report showed
 * the same gap one level deeper.</b> {@code .a {...}} immediately followed by {@code .a:hover
 * {...}} used to render with no visual break between them (fixed first); then a LESS example —
 * {@code .button { background: @brand; &:hover {...} } } — showed the identical gap applies
 * *inside* a block too, between a plain declaration and a nested rule that follows it. Both are the
 * same underlying rule, not two separate fixes: whenever a new line is about to start (any of the
 * branches below that check {@code atLineStart}) and {@link #selectorFollowsBeforeStatementEnd}
 * says *this* line itself opens a nested rule, a blank line is inserted first if the block it
 * belongs to (tracked per-depth by {@code hasContentAtDepth}, pushed/popped alongside brace
 * {@code depth} on {@code {}/{@code }}) has already emitted a prior statement or nested rule of its
 * own — never before the very first thing in a block, regardless of what that first thing is. See
 * {@link #beginLine} for the actual mechanism, and its own Javadoc for a worked trace of the LESS
 * example above. A blank line is never inserted before a plain declaration, even one that follows
 * other content — only before a line that itself opens a nested rule.
 *
 * <p>Also known and deliberate: this formatter never invents *line-break* structure the source
 * didn't already signal via whitespace — e.g. a comma-separated selector list already split across
 * lines ({@code h1,\nh2 {...}}) stays split (each original line break between ordinary characters
 * is preserved, per the ASI-safety rule above), but one written on a single line
 * ({@code h1,h2{...}}) is not proactively re-split onto multiple lines. Blindly splitting on every
 * comma would corrupt a function call or argument list ({@code rgba(0, 0, 0, .5)}, a JS array
 * literal {@code [1,2,3]}) that also uses commas but isn't a selector list — telling those apart
 * needs real grammar awareness this formatter deliberately doesn't have.
 *
 * <p><b>{@code beautify} does normalize the *same-line* spacing after a {@code ,}</b> — a real bug,
 * part of the same LESS report {@link #beginLine} documents above: {@code darken(@brand,10%)}
 * needed a space after the comma too ({@code darken(@brand, 10%)}). This doesn't conflict with the
 * "never invents line-break structure" rule just above — inserting a same-line space (never a
 * newline) after a comma is safe unconditionally, unlike splitting one onto its own line, since a
 * function argument list, a selector list, and a JS array/object literal all uniformly want a
 * space after each comma with no ambiguity the way a colon's two contexts have. Skipped only when
 * a newline already follows (preserves an already-multiline list's own line breaks) or when a
 * closing {@code )}/{@code ]}/{@code }} immediately follows (a trailing comma before a closer
 * doesn't need a space padding it away from that closer).
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
        // Tracks nesting inside (...) — e.g. a media feature (min-width: 768px) or a pseudo-class
        // function :not(.foo) — independently of brace depth. Consulted by the `:` handling below
        // to decide whether a colon is declaration-style (inside parens, always spaced) before
        // even trying the selector-vs-declaration lookahead. See this class's own Javadoc.
        int parenDepth = 0;
        boolean atLineStart = true;
        // Distinguishes a genuine new statement/block boundary from a mid-statement resumption —
        // both set atLineStart = true (an already-multi-line selector list like `h1,\nh2 {...}`
        // resumes on its second line via the same atLineStart mechanism the ASI-safety rule above
        // uses), but only the former should ever trigger beginLine's blank-line decision. Only
        // `;`/`{`/`}` (a real statement/block ending) set this true; the whitespace branch's own
        // mid-statement newline handling deliberately leaves it untouched. See beginLine's own
        // Javadoc for why conflating the two was a real bug, caught by an existing test.
        boolean atStatementStart = true;
        int n = input.length();
        int i = 0;
        // Per-depth "has this block already emitted a statement/nested rule" flags, consulted and
        // updated by beginLine — see this class's own Javadoc and beginLine's own doc comment for
        // the full mechanism. Index 0 (the whole document's own top level) is pushed once, up
        // front; every `{`/`}` below pushes/pops one more, keeping size() - 1 always equal to the
        // current depth.
        List<Boolean> hasContentAtDepth = new ArrayList<>();
        hasContentAtDepth.add(false);

        while (i < n) {
            char c = input.charAt(i);

            if ((c == 'u' || c == 'U') && isUrlFunctionStart(input, i) && !isQuoteAt(input, i + 4)) {
                int end = scanUrlFunctionArg(input, i);
                if (atLineStart) {
                    beginLine(out, input, i, depth, hasContentAtDepth, atStatementStart);
                    atLineStart = false;
                    atStatementStart = false;
                }
                out.append(input, i, end);
                i = end;
                continue;
            }
            if (c == '/' && i + 1 < n && input.charAt(i + 1) == '*') {
                int end = TextScanning.indexOfOrEnd(input, "*/", i + 2);
                if (atLineStart) {
                    beginLine(out, input, i, depth, hasContentAtDepth, atStatementStart);
                    atLineStart = false;
                    atStatementStart = false;
                }
                out.append(input, i, end);
                i = end;
                continue;
            }
            if (c == '/' && i + 1 < n && input.charAt(i + 1) == '/') {
                int end = TextScanning.indexOfOrEnd(input, '\n', i + 2);
                if (atLineStart) {
                    beginLine(out, input, i, depth, hasContentAtDepth, atStatementStart);
                    atStatementStart = false;
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
                    beginLine(out, input, i, depth, hasContentAtDepth, atStatementStart);
                    atLineStart = false;
                    atStatementStart = false;
                }
                out.append(input, i, end);
                i = end;
                continue;
            }
            if (c == '(') {
                if (atLineStart) {
                    beginLine(out, input, i, depth, hasContentAtDepth, atStatementStart);
                    atLineStart = false;
                    atStatementStart = false;
                }
                out.append(c);
                parenDepth++;
                i++;
                continue;
            }
            if (c == ')') {
                if (atLineStart) {
                    beginLine(out, input, i, depth, hasContentAtDepth, atStatementStart);
                    atLineStart = false;
                    atStatementStart = false;
                }
                out.append(c);
                parenDepth = Math.max(0, parenDepth - 1);
                i++;
                continue;
            }
            if (c == ',') {
                if (atLineStart) {
                    beginLine(out, input, i, depth, hasContentAtDepth, atStatementStart);
                    atLineStart = false;
                    atStatementStart = false;
                }
                out.append(c);
                i++;
                // Same existing-whitespace-collapse-then-single-space normalization the
                // declaration colon already gets — unlike that one, a comma needs no context
                // check at all: a space after `,` is always correct in CSS/LESS/SCSS/JS, whether
                // it's a function argument list (darken(@brand,10%) -> darken(@brand, 10%)) or a
                // selector list (h1,h2 -> h1, h2) — this only adds a same-line space, it never
                // splits onto a new line, so it doesn't touch the "never invents structure the
                // source didn't signal via whitespace" rule this class's own Javadoc documents for
                // comma-separated selector lists. Skipped when real content doesn't actually
                // follow — a trailing comma right before `)`/`]`/`}` shouldn't gain a stray space,
                // and a comma already followed by a newline (an already-multiline list) keeps that
                // newline rather than being collapsed onto one line.
                int j = i;
                while (j < n && (input.charAt(j) == ' ' || input.charAt(j) == '\t')) {
                    j++;
                }
                if (j < n) {
                    char next = input.charAt(j);
                    if (next != ')' && next != ']' && next != '}' && next != '\n' && next != '\r') {
                        i = j;
                        out.append(' ');
                    }
                }
                continue;
            }
            if (c == ':') {
                if (atLineStart) {
                    beginLine(out, input, i, depth, hasContentAtDepth, atStatementStart);
                    atLineStart = false;
                    atStatementStart = false;
                }
                out.append(c);
                i++;
                // Declaration-style whenever already inside parens (a media feature, an SCSS map
                // key, etc.) — otherwise, look forward for whichever of {/;/} comes first. See
                // this class's own Javadoc for the full reasoning and known remaining imprecision.
                if (parenDepth > 0 || !selectorFollowsBeforeStatementEnd(input, i)) {
                    // Collapse any existing run of spaces/tabs right after the colon before
                    // re-inserting our own single space, so "color:red", "color: red", and
                    // "color:   red" all normalize to exactly one space rather than doubling up
                    // on whatever the source already had. A newline is deliberately left alone —
                    // it falls through to the ordinary whitespace handling below, which already
                    // preserves it (and trims any trailing space this branch just added) via the
                    // same ASI-safety-driven logic this class's Javadoc documents elsewhere.
                    int j = i;
                    while (j < n && (input.charAt(j) == ' ' || input.charAt(j) == '\t')) {
                        j++;
                    }
                    i = j;
                    out.append(' ');
                }
                continue;
            }
            if (c == '{') {
                if (atLineStart) {
                    beginLine(out, input, i, depth, hasContentAtDepth, atStatementStart);
                } else {
                    trimTrailingSpaces(out);
                    out.append(' ');
                }
                out.append("{\n");
                depth++;
                hasContentAtDepth.add(false);
                atLineStart = true;
                atStatementStart = true;
                i++;
                continue;
            }
            if (c == '}') {
                depth = Math.max(0, depth - 1);
                if (hasContentAtDepth.size() > 1) {
                    hasContentAtDepth.remove(hasContentAtDepth.size() - 1);
                }
                if (!atLineStart) {
                    trimTrailingSpaces(out);
                    out.append('\n');
                }
                appendIndent(out, depth);
                out.append("}\n");
                atLineStart = true;
                atStatementStart = true;
                i++;
                continue;
            }
            if (c == ';') {
                trimTrailingSpaces(out);
                out.append(";\n");
                atLineStart = true;
                atStatementStart = true;
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
                beginLine(out, input, i, depth, hasContentAtDepth, atStatementStart);
                atLineStart = false;
                atStatementStart = false;
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
                i = TextScanning.indexOfOrEnd(input, "*/", i + 2);
                continue;
            }
            if (c == '/' && i + 1 < n && input.charAt(i + 1) == '/') {
                i = TextScanning.indexOfOrEnd(input, '\n', i + 2);
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

    /** Called from every {@code if (atLineStart) {...}} site above, exactly once per new line —
     * decides whether this line needs a blank line inserted before it, appends its indent, and
     * updates {@code hasContentAtDepth} for whatever line comes next. A blank line goes in when
     * both are true: this line itself opens a nested rule (per
     * {@link #selectorFollowsBeforeStatementEnd}), and the block at {@code depth} has already
     * emitted a prior statement or nested rule of its own (checked via
     * {@code hasContentAtDepth}'s own top-of-stack entry, corresponding to the current depth,
     * pushed/popped alongside {@code {}/{@code }} — see this class's own Javadoc). Either way,
     * that entry is then set {@code true}, since a new line is now definitely starting — this is
     * what lets the check-then-set happen in one pass with no separate bookkeeping needed at
     * {@code ;}/{@code }} time.
     *
     * <p><b>Worked trace, the LESS example this method exists for</b> — {@code .button { background:
     * @brand; &:hover { color: blue; } }}: when {@code background: @brand;}'s own line begins
     * (depth 1, freshly pushed {@code false}), it doesn't open a nested rule, so no blank line goes
     * in regardless — but its entry is still set {@code true}. When {@code &:hover {}'s own line
     * begins next (still depth 1), it *does* open a nested rule, and depth 1's entry is now
     * {@code true} from the previous line — blank line inserted. Inside {@code &:hover}'s own block
     * (depth 2, fresh {@code false}), {@code color: blue;} is the *first* line, so no blank line
     * goes in there regardless of what it is. */
    private static void beginLine(
            StringBuilder out, String input, int i, int depth, List<Boolean> hasContentAtDepth, boolean atStatementStart) {
        // Only a genuine new statement/block boundary (atStatementStart) is eligible for the
        // blank-line decision — a mid-statement resumption (e.g. `h1,\nh2 {...}`'s second line)
        // also reaches this method with atLineStart true, but must never be mistaken for a new
        // statement in its own right: it's still part of the SAME statement that already made
        // this decision (or explicitly chose not to) when that statement first began. A real bug,
        // caught by an existing test (preservesAlreadyMultilineSelectorLists) failing once this
        // method's blank-line logic first landed — without this guard, h1's own line would mark
        // its enclosing block "has content" the instant it began, and h2's line (the *same*
        // selector list's second line) would then wrongly see that as "prior content" and gain an
        // unwanted blank line between them.
        if (atStatementStart) {
            int top = hasContentAtDepth.size() - 1;
            boolean opensNestedBlock = selectorFollowsBeforeStatementEnd(input, i);
            if (opensNestedBlock && hasContentAtDepth.get(top)) {
                out.append('\n');
            }
            hasContentAtDepth.set(top, true);
        }
        appendIndent(out, depth);
    }

    /** Bounded forward lookahead from position {@code from}, used by two different callers for the
     * same underlying question — "does a {@code {} come before the next {@code ;}/{@code }}
     * starting here?" — {@link #beginLine} (from a new line's very first character, deciding
     * whether that whole line opens a nested rule) and the {@code :} handling in {@link #beautify}
     * (from just after a colon not already known to be inside parens, deciding whether *that*
     * colon opened a nested rule's own selector or ended a declaration's property name — see this
     * class's own Javadoc for the full reasoning there). Returns {@code true} when a {@code {} is
     * the first of {@code {}/{@code ;}/{@code }} reached (e.g. {@code &:hover {}), {@code false}
     * otherwise (e.g. {@code color:red;}, or end of input reached with no clear terminator — the
     * safer default in both callers, since it only ever skips adding a blank line/space rather than
     * adding one that wasn't warranted). Skips over string literals, comments, and an unquoted
     * {@code url(...)} argument the same way the main scan does, so a {@code {}/{@code ;}/{@code }}
     * inside any of those (e.g. {@code content: "a;b"}) never confuses the scan. */
    private static boolean selectorFollowsBeforeStatementEnd(String s, int from) {
        int n = s.length();
        int i = from;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '{') {
                return true;
            }
            if (c == ';' || c == '}') {
                return false;
            }
            if (c == '\'' || c == '"' || c == '`') {
                i = scanStringLiteral(s, i, c);
                continue;
            }
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                i = TextScanning.indexOfOrEnd(s, "*/", i + 2);
                continue;
            }
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                i = TextScanning.indexOfOrEnd(s, '\n', i + 2);
                continue;
            }
            if ((c == 'u' || c == 'U') && isUrlFunctionStart(s, i) && !isQuoteAt(s, i + 4)) {
                i = scanUrlFunctionArg(s, i);
                continue;
            }
            i++;
        }
        return false;
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
