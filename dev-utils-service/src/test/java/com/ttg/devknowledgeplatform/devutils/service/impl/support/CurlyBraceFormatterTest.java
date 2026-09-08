package com.ttg.devknowledgeplatform.devutils.service.impl.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class CurlyBraceFormatterTest {

    @Test
    void indentsNestedBlocksByDefault() {
        String result = CurlyBraceFormatter.beautify(".a{color:red;.b{color:blue;}}");

        // A declaration's own colon gets a space inserted ("color: red") — see
        // declarationColonGetsASpaceButSelectorColonDoesNot below for the selector-colon side of
        // this same fix.
        assertThat(result).isEqualTo(
                ".a {\n"
                        + "  color: red;\n"
                        + "  .b {\n"
                        + "    color: blue;\n"
                        + "  }\n"
                        + "}"
        );
    }

    @Test
    void declarationColonGetsASpaceButSelectorColonDoesNot() {
        // The exact reported bug: a declaration's colon (`display:grid`) needed a space; a
        // pseudo-class selector's colon (`.card:hover`) must stay untouched — no space would ever
        // be legal there. Also covers the blank line now inserted between two top-level rules.
        String result = CurlyBraceFormatter.beautify(
                ".card{display:grid;gap:1rem;padding:1.5rem;background:#fff;border-radius:1rem}"
                        + ".card:hover{transform:translateY(-2px)}"
        );

        assertThat(result).isEqualTo(
                ".card {\n"
                        + "  display: grid;\n"
                        + "  gap: 1rem;\n"
                        + "  padding: 1.5rem;\n"
                        + "  background: #fff;\n"
                        + "  border-radius: 1rem\n"
                        + "}\n"
                        + "\n"
                        + ".card:hover {\n"
                        + "  transform: translateY(-2px)\n"
                        + "}"
        );
    }

    @Test
    void doesNotSpaceANestedRulesOwnPseudoClassSelectorColon() {
        // LESS/SCSS-style nesting: "&:hover" is a selector, not a declaration, even though it
        // sits at a deeper brace depth than a top-level rule's own selector would.
        String result = CurlyBraceFormatter.beautify(".card { &:hover { color: blue; } }");

        assertThat(result).isEqualTo(
                ".card {\n"
                        + "  &:hover {\n"
                        + "    color: blue;\n"
                        + "  }\n"
                        + "}"
        );
    }

    @Test
    void spacesAColonInsideParensEvenWhenFollowedByABrace() {
        // A media feature's own colon (min-width: 768px) sits inside parens that are themselves
        // followed by the media rule's own opening brace — the parenDepth check has to win over
        // the plain "does a { come next" lookahead here, or this would be misread as a selector.
        String result = CurlyBraceFormatter.beautify("@media (min-width:768px) { .a { color: red; } }");

        assertThat(result).isEqualTo(
                "@media (min-width: 768px) {\n"
                        + "  .a {\n"
                        + "    color: red;\n"
                        + "  }\n"
                        + "}"
        );
    }

    @Test
    void collapsesExistingSpacingAroundADeclarationColonToExactlyOne() {
        String result = CurlyBraceFormatter.beautify(".a { one:red; two:   blue; three: green; }");

        assertThat(result).isEqualTo(
                ".a {\n"
                        + "  one: red;\n"
                        + "  two: blue;\n"
                        + "  three: green;\n"
                        + "}"
        );
    }

    @Test
    void insertsABlankLineBetweenTwoTopLevelRulesButNotBetweenNestedOnes() {
        String result = CurlyBraceFormatter.beautify(".a{color:red}.b{color:blue}");

        assertThat(result).isEqualTo(".a {\n  color: red\n}\n\n.b {\n  color: blue\n}");
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
                        + "  background: url(http://example.com/x.png);\n"
                        + "  color: red;\n"
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
