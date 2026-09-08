package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.ttg.devknowledgeplatform.devutils.dto.StringCaseResponse;

class StringCaseOperationTest {

    private final StringCaseOperation operation = new StringCaseOperation();

    @Test
    void returnsEveryCaseVariant() {
        StringCaseResponse result = operation.execute("Build ship and share with Vui Coding");

        assertThat(result.camelCase()).isEqualTo("buildShipAndShareWithVuiCoding");
        assertThat(result.constantCase()).isEqualTo("BUILD_SHIP_AND_SHARE_WITH_VUI_CODING");
    }
}
