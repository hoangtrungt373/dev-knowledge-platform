package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class HtmlPreviewOperationTest {

    private final HtmlPreviewOperation operation = new HtmlPreviewOperation();

    @Test
    void stripsScriptTags() {
        String result = operation.execute("<p>Hello</p><script>alert('xss')</script>");

        assertThat(result).contains("Hello").doesNotContain("<script").doesNotContain("alert");
    }

    @Test
    void stripsEventHandlerAttributes() {
        String result = operation.execute("<img src=\"https://example.com/a.png\" onerror=\"alert(1)\">");

        assertThat(result).doesNotContain("onerror").doesNotContain("alert");
    }

    @Test
    void stripsJavascriptHref() {
        String result = operation.execute("<a href=\"javascript:alert(1)\">Click</a>");

        assertThat(result).doesNotContain("javascript:");
    }

    @Test
    void allowsStyleAttribute() {
        String result = operation.execute("<p style=\"color: red;\">Hello</p>");

        assertThat(result).contains("style=\"color: red;\"");
    }

    @Test
    void allowsClassAndIdAttributes() {
        String result = operation.execute("<div class=\"card\" id=\"main\">Hello</div>");

        assertThat(result).contains("class=\"card\"").contains("id=\"main\"");
    }

    @Test
    void allowsDataImageProtocol() {
        String result = operation.execute(
                "<img src=\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=\">");

        assertThat(result).contains("data:image/png;base64,");
    }

    @Test
    void stripsStyleBlock() {
        String result = operation.execute("<style>body { color: red; }</style><p>Hello</p>");

        assertThat(result).contains("Hello").doesNotContain("<style");
    }

    @Test
    void neverThrowsOnMalformedMarkup() {
        assertThatCode(() -> operation.execute("<div><p>unclosed<span>oops</div>")).doesNotThrowAnyException();
    }

    @Test
    void group() {
        assertThat(operation.group()).isEqualTo(com.ttg.devknowledgeplatform.devutils.service.OperationGroup.WEB);
    }
}
