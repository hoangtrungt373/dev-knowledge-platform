package com.ttg.devknowledgeplatform.devutils.service.impl.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class CurlyBraceFormatterTest {

    @Test
    void indentsNestedBlocksByDefault() {
        String result = CurlyBraceFormatter.beautify(".a{color:red;.b{color:blue;}}");

        // Note: no space after the colon in "color:red" — this formatter preserves whatever
        // spacing the source had around a `:` rather than normalizing it, deliberately, since a
        // blanket "always insert a space after `:`" rule would corrupt a pseudo-class selector
        // like `:hover`/`::before`, which relies on *no* space between the colon and what follows.
        assertThat(result).isEqualTo(
                ".a {\n"
                        + "  color:red;\n"
                        + "  .b {\n"
                        + "    color:blue;\n"
                        + "  }\n"
                        + "}"
        );
    }

    @Test
    void preservesAlreadyMultilineSelectorLists() {
        String result = CurlyBraceFormatter.beautify("h1,\nh2 {\n  color: red;\n}");

        assertThat(result).isEqualTo("h1,\nh2 {\n  color: red;\n}");
    }

    @Test
    void keepsBlockCommentsVerbatim() {
        String result = CurlyBraceFormatter.beautify(".a { /* keep me */ color: red; }");

        assertThat(result).contains("/* keep me */");
    }

    @Test
    void keepsLineCommentsVerbatimAndEndsTheirLine() {
        String result = CurlyBraceFormatter.beautify(".a {\n  color: red; // note\n  top: 0;\n}");

        assertThat(result).contains("// note").contains("top: 0;");
    }

    @Test
    void neverThrowsOnUnterminatedStringsOrComments() {
        assertThatCode(() -> CurlyBraceFormatter.beautify(".a { content: \"unterminated"))
                .doesNotThrowAnyException();
        assertThatCode(() -> CurlyBraceFormatter.beautify(".a { /* unterminated"))
                .doesNotThrowAnyException();
    }

    @Test
    void doesNotReformatContentInsideStringLiterals() {
        String result = CurlyBraceFormatter.beautify(".a { content: \"a{b;c}d\"; }");

        assertThat(result).contains("\"a{b;c}d\"");
    }

    @Test
    void minifyStripsCommentsAndCollapsesSafeBoundaryWhitespace() {
        String result = CurlyBraceFormatter.minify(".a {\n  /* drop me */\n  color: red;\n  top: 0;\n}");

        // The space after each `:` is dropped too — `:` is a safe-to-tighten boundary character
        // (both sides of a CSS declaration's colon, or a JS/JSON-like object literal's, tolerate
        // no surrounding whitespace at all), same reasoning `;`/`{`/`}` already get tightened.
        assertThat(result).doesNotContain("drop me").isEqualTo(".a{color:red;top:0;}");
    }

    @Test
    void minifyPreservesStringLiteralsVerbatim() {
        String result = CurlyBraceFormatter.minify(".a { content: \"  spaced  \"; }");

        assertThat(result).contains("\"  spaced  \"");
    }

    @Test
    void minifyNeverMergesTwoOrdinaryLinesSeparatedByANewline() {
        // The ASI-safety guarantee this class's own Javadoc documents: a real line break between
        // two ordinary (non-punctuation) tokens must never be collapsed into "no separator" or a
        // plain space, since that could silently change what `return\nx` means in JavaScript.
        String result = CurlyBraceFormatter.minify("return\nx");

        assertThat(result).isEqualTo("return\nx");
    }

    @Test
    void minifyNeverThrowsOnUnterminatedInput() {
        assertThatCode(() -> CurlyBraceFormatter.minify(".a { content: \"unterminated"))
                .doesNotThrowAnyException();
    }

    @Test
    void beautifyDoesNotTreatDoubleSlashInsideUnquotedUrlAsALineComment() {
        // A real bug: the `//` in an unquoted url(http://...) argument used to be misread as a
        // line-comment start, which swallowed the closing `}` that follows into the "comment" and
        // broke brace-depth tracking for everything after it.
        String result = CurlyBraceFormatter.beautify("a{background:url(http://example.com/x.png);color:red;}");

        assertThat(result).isEqualTo(
                "a {\n"
                        + "  background:url(http://example.com/x.png);\n"
                        + "  color:red;\n"
                        + "}"
        );
    }

    @Test
    void minifyDoesNotDiscardEverythingAfterAnUnquotedUrlsDoubleSlash() {
        // Same bug as above, worse in minify: a single-line declaration has no `\n` to stop the
        // comment scan, so everything from the `//` in `http://` to the end of the input used to
        // be silently discarded rather than just mis-formatted.
        String result = CurlyBraceFormatter.minify("a{background:url(http://example.com/x.png);color:red;}");

        assertThat(result).isEqualTo("a{background:url(http://example.com/x.png);color:red;}");
    }

    @Test
    void stillTreatsAQuotedUrlArgumentAsAnOrdinaryStringLiteral() {
        String result = CurlyBraceFormatter.beautify("a{background:url(\"http://example.com/x.png\");}");

        assertThat(result).contains("\"http://example.com/x.png\"");
    }
}
