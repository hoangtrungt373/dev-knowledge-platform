package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class MarkdownPreviewOperationTest {

    private final MarkdownPreviewOperation operation = new MarkdownPreviewOperation();

    @Test
    void rendersBasicMarkdown() {
        String result = operation.execute("# Hello\n\nThis is **bold** and *italic*.");

        assertThat(result).contains("<h1>Hello</h1>").contains("<strong>bold</strong>").contains("<em>italic</em>");
    }

    @Test
    void rendersGfmTables() {
        String result = operation.execute("| A | B |\n| --- | --- |\n| 1 | 2 |\n");

        assertThat(result).contains("<table>").contains("<td>1</td>").contains("<td>2</td>");
    }

    @Test
    void rendersGfmStrikethrough() {
        String result = operation.execute("~~gone~~");

        assertThat(result).contains("<del>gone</del>");
    }

    @Test
    void rendersGfmTaskListItems() {
        String result = operation.execute("- [x] done\n- [ ] todo\n");

        assertThat(result).contains("checked").contains("done").contains("todo");
    }

    @Test
    void stripsEmbeddedScriptTags() {
        String result = operation.execute("Hello\n\n<script>alert('xss')</script>");

        assertThat(result).contains("Hello").doesNotContain("<script").doesNotContain("alert");
    }

    @Test
    void stripsEmbeddedEventHandlerAttributes() {
        String result = operation.execute("<img src=\"https://example.com/a.png\" onerror=\"alert(1)\">");

        assertThat(result).doesNotContain("onerror").doesNotContain("alert");
    }

    @Test
    void neverThrowsOnAnyInput() {
        assertThatCode(() -> operation.execute("# unclosed *emphasis\n\n[link](")).doesNotThrowAnyException();
    }

    @Test
    void group() {
        assertThat(operation.group()).isEqualTo(com.ttg.devknowledgeplatform.devutils.service.OperationGroup.WEB);
    }
}
