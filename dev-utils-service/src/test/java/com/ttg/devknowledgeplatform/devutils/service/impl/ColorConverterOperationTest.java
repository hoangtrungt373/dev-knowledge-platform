package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.dto.ColorConversionResponse;
import com.ttg.devknowledgeplatform.devutils.service.OperationGroup;

class ColorConverterOperationTest {

    private final ColorConverterOperation operation = new ColorConverterOperation();

    @Test
    void delegatesToColorConverter() {
        ColorConversionResponse result = operation.execute("#14B8A6");

        assertThat(result.hex()).isEqualTo("#14B8A6");
        assertThat(result.rgb()).isEqualTo("rgb(20, 184, 166)");
    }

    @Test
    void throwsBusinessExceptionOnInvalidInput() {
        assertThatThrownBy(() -> operation.execute("not-a-color")).isInstanceOf(BusinessException.class);
    }

    @Test
    void group() {
        assertThat(operation.group()).isEqualTo(OperationGroup.WEB);
    }
}
