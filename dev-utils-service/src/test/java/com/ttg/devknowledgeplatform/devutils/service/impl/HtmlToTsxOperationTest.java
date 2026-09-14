package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.ttg.devknowledgeplatform.devutils.service.OperationGroup;

class HtmlToTsxOperationTest {

    private final HtmlToTsxOperation operation = new HtmlToTsxOperation();

    @Test
    void delegatesToHtmlToTsxConverter() {
        String result = operation.execute("<div class=\"card\">Hi</div>", true);

        assertThat(result).isEqualTo("<div className=\"card\">Hi</div>");
    }

    @Test
    void group() {
        assertThat(operation.group()).isEqualTo(OperationGroup.WEB);
    }
}
