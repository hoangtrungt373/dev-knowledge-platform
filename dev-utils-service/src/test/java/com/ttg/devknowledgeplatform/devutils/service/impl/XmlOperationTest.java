package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;

class XmlOperationTest {

    private final XmlOperation operation = new XmlOperation();

    @Test
    void indentsNestedElementsByDefault() {
        String result = operation.execute("<root><child>text</child></root>", false);

        assertThat(result).contains("\n").contains("  <child>text</child>");
    }

    @Test
    void producesCompactOutputWhenMinified() {
        String result = operation.execute("<root>\n  <child>text</child>\n</root>", true);

        assertThat(result).isEqualTo("<root><child>text</child></root>");
    }

    @Test
    void preservesMeaningfulTextContentWhitespace() {
        String result = operation.execute("<p>Hello world</p>", true);

        assertThat(result).contains("Hello world");
    }

    @Test
    void omitsTheXmlDeclarationWhenTheInputHadNone() {
        String result = operation.execute("<root/>", false);

        assertThat(result).doesNotContain("<?xml");
    }

    @Test
    void keepsTheXmlDeclarationWhenTheInputHadOne() {
        String result = operation.execute("<?xml version=\"1.0\"?><root/>", false);

        assertThat(result).contains("<?xml");
    }

    @Test
    void malformedXmlThrowsBusinessExceptionWithInvalidXmlErrorCode() {
        assertThatThrownBy(() -> operation.execute("<root><unclosed></root>", false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(DevUtilsErrorCode.INVALID_XML);
    }

    @Test
    void rejectsADoctypeDeclarationOutrightAsAnXxeDefense() {
        String withDoctype = "<?xml version=\"1.0\"?><!DOCTYPE root [<!ENTITY x \"y\">]><root>&x;</root>";

        assertThatThrownBy(() -> operation.execute(withDoctype, false))
                .isInstanceOf(BusinessException.class);
    }
}
