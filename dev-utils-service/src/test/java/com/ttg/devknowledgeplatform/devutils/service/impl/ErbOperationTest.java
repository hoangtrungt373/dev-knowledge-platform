package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class ErbOperationTest {

    private final ErbOperation operation = new ErbOperation();

    @Test
    void indentsHtmlAroundErbTagsByDefault() {
        String result = operation.execute("<div><% if true %><p>Hi</p><% end %></div>", false);

        assertThat(result).contains("\n").contains("<% if true %>").contains("<% end %>");
    }

    @Test
    void preservesErbOutputTagsVerbatim() {
        String result = operation.execute("<p><%= user.name %></p>", false);

        assertThat(result).contains("<%= user.name %>");
    }

    @Test
    void doesNotHtmlEscapeComparisonOperatorsInsideAnErbTag() {
        // Confirms the placeholder-protection step: the Ruby code's own `<`/`>` never reach
        // jsoup's tokenizer, so they can never come back HTML-entity-escaped (e.g. `&lt;`).
        String result = operation.execute("<% if x < y %>ok<% end %>", false);

        assertThat(result).contains("<% if x < y %>").doesNotContain("&lt;");
    }

    @Test
    void producesUnformattedOutputWhenMinified() {
        String result = operation.execute("<div><% if true %><p>Hi</p><% end %></div>", true);

        assertThat(result).doesNotContain("\n").contains("<% if true %>");
    }

    @Test
    void neverThrowsOnMalformedMarkupOrUnterminatedErbTags() {
        assertThatCode(() -> operation.execute("<div><%unclosed", false)).doesNotThrowAnyException();
    }
}
