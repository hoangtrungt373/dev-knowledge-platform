package com.ttg.devknowledgeplatform.devutils.service.impl.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class HtmlToTsxConverterTest {

    @Test
    void convertsTheReportedExampleExactly() {
        String input = "<label class=\"field\" for=\"email\" style=\"color: teal; font-weight: 700\">"
                + "<input tabindex=\"0\" autocomplete=\"email\" />Email</label>";

        String result = HtmlToTsxConverter.convert(input, true);

        assertThat(result).isEqualTo("<label className=\"field\" htmlFor=\"email\" "
                + "style={{ color: \"teal\", fontWeight: \"700\" }}><input tabIndex=\"0\" autoComplete=\"email\" />Email</label>");
    }

    @Test
    void convertsClassToClassName() {
        assertThat(HtmlToTsxConverter.convert("<div class=\"card\">Hi</div>", true)).contains("className=\"card\"");
    }

    @Test
    void convertsForToHtmlFor() {
        assertThat(HtmlToTsxConverter.convert("<label for=\"email\">Email</label>", true)).contains("htmlFor=\"email\"");
    }

    @Test
    void convertsStyleStringToObjectLiteral() {
        String result = HtmlToTsxConverter.convert("<div style=\"background-color: red; z-index: 2\">x</div>", true);

        assertThat(result).contains("style={{ backgroundColor: \"red\", zIndex: \"2\" }}");
    }

    @Test
    void preservesCssCustomPropertyNameVerbatim() {
        String result = HtmlToTsxConverter.convert("<div style=\"--main-color: blue\">x</div>", true);

        assertThat(result).contains("style={{ --main-color: \"blue\" }}");
    }

    @Test
    void selfClosesVoidElements() {
        assertThat(HtmlToTsxConverter.convert("<br>", true)).isEqualTo("<br />");
        assertThat(HtmlToTsxConverter.convert("<img src=\"a.png\">", true)).isEqualTo("<img src=\"a.png\" />");
    }

    @Test
    void keepsBareBooleanAttributeShorthand() {
        assertThat(HtmlToTsxConverter.convert("<button disabled>Go</button>", true)).contains("<button disabled>");
    }

    @Test
    void convertsKnownEventHandlerAttribute() {
        assertThat(HtmlToTsxConverter.convert("<button onclick=\"go()\">Go</button>", true)).contains("onClick=\"go()\"");
        assertThat(HtmlToTsxConverter.convert("<div onmouseover=\"hover()\">x</div>", true)).contains("onMouseOver=\"hover()\"");
    }

    @Test
    void leavesDataAndAriaAttributesUntouched() {
        String result = HtmlToTsxConverter.convert("<div data-id=\"1\" aria-hidden=\"true\">x</div>", true);

        assertThat(result).contains("data-id=\"1\"").contains("aria-hidden=\"true\"");
    }

    @Test
    void convertsHtmlCommentsToJsxComments() {
        // A bare top-level comment with no other content is a degenerate case jsoup's own HTML
        // tree builder attaches outside <body> entirely (before <html>, never reaching
        // body.html()'s own output) — real-world input always has the comment nested inside actual
        // markup, which is what this asserts instead.
        assertThat(HtmlToTsxConverter.convert("<div><!-- note --></div>", true)).contains("{/* note */}");
    }

    @Test
    void indentsNestedElementsWhenNotMinified() {
        String result = HtmlToTsxConverter.convert("<div><p>Hello</p></div>", false);

        assertThat(result).contains("\n");
    }

    @Test
    void formatsInlineHtmlTagsOntoSeparateLinesWhenNotMinified() {
        // Real bug, reported directly ("the result is inline even though we do not choose
        // Minify"): jsoup's own pretty-printer only breaks a *block*-level tag onto its own line
        // by default — <label>/<input> are both HTML5 inline tags, so plain prettyPrint(true)
        // alone still rendered them on one single line regardless of `minify`. Fixed via
        // OutputSettings.outline(true) — see this class's own Javadoc.
        String input = "<label class=\"field\" for=\"email\" style=\"color: teal; font-weight: 700\">"
                + "<input tabindex=\"0\" autocomplete=\"email\" />Email</label>";

        String result = HtmlToTsxConverter.convert(input, false);

        assertThat(result)
                .contains("<label className=\"field\" htmlFor=\"email\" style={{ color: \"teal\", fontWeight: \"700\" }}>")
                .contains("\n  <input tabIndex=\"0\" autoComplete=\"email\" />")
                .contains("\n  Email")
                .contains("\n</label>");
    }

    @Test
    void neverThrowsOnMalformedMarkup() {
        assertThatCode(() -> HtmlToTsxConverter.convert("<div><p>unclosed<span>oops</div>", false))
                .doesNotThrowAnyException();
    }
}
