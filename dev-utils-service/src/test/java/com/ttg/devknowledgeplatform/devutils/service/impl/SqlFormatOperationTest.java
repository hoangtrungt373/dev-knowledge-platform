package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class SqlFormatOperationTest {

    private final SqlFormatOperation operation = new SqlFormatOperation();

    @Test
    void putsMajorClausesOnTheirOwnLineByDefault() {
        String result = operation.execute("select id from users where active = 1", false);

        assertThat(result).contains("\n").contains("WHERE").contains("active = 1");
    }

    @Test
    void producesCompactOutputWhenMinified() {
        String result = operation.execute("select id\nfrom users\nwhere active = 1", true);

        assertThat(result).doesNotContain("\n");
    }

    @Test
    void neverThrowsOnMalformedSql() {
        assertThatCode(() -> operation.execute("select * from users where name = 'unterminated", false))
                .doesNotThrowAnyException();
    }
}
