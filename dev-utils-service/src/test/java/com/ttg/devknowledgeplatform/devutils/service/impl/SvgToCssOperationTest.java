package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

import com.ttg.devknowledgeplatform.devutils.service.OperationGroup;

class SvgToCssOperationTest {

    private final SvgToCssOperation operation = new SvgToCssOperation();

    private static final String SAMPLE_SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"32\" height=\"32\" "
            + "viewBox=\"0 0 32 32\"><rect width=\"32\" height=\"32\" rx=\"8\" fill=\"#14b8a6\"/>"
            + "<path d=\"M9 16l5 5 9-10\" fill=\"none\" stroke=\"white\" stroke-width=\"3\"/></svg>";

    private static final String EXPECTED_DATA_URI =
            "data:image/svg+xml,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20width%3D%2232%22%20"
                    + "height%3D%2232%22%20viewBox%3D%220%200%2032%2032%22%3E%3Crect%20width%3D%2232%22%20height%3D%2232"
                    + "%22%20rx%3D%228%22%20fill%3D%22%2314b8a6%22%2F%3E%3Cpath%20d%3D%22M9%2016l5%205%209-10%22%20fill"
                    + "%3D%22none%22%20stroke%3D%22white%22%20stroke-width%3D%223%22%2F%3E%3C%2Fsvg%3E";

    @Test
    void convertsTheReportedExampleExactly() {
        String result = operation.execute(SAMPLE_SVG, false);

        assertThat(result).isEqualTo("""
                .icon {
                  background-image: url("%s");
                  background-repeat: no-repeat;
                  background-position: center;
                  background-size: contain;
                }""".formatted(EXPECTED_DATA_URI));
    }

    @Test
    void minifiesToASingleLine() {
        String result = operation.execute(SAMPLE_SVG, true);

        assertThat(result).isEqualTo(".icon{background-image:url(\"" + EXPECTED_DATA_URI
                + "\");background-repeat:no-repeat;background-position:center;background-size:contain;}");
        assertThat(result).doesNotContain("\n");
    }

    @Test
    void trimsSurroundingWhitespace() {
        String result = operation.execute("\n  " + SAMPLE_SVG + "  \n", true);

        assertThat(result).contains(EXPECTED_DATA_URI);
    }

    @Test
    void encodesSpaceAsPercentTwentyNotPlus() {
        String result = operation.execute("<svg>a b</svg>", true);

        assertThat(result).contains("a%20b").doesNotContain("a+b");
    }

    @Test
    void alwaysIncludesTheThreeCompanionDeclarations() {
        String result = operation.execute("<svg></svg>", false);

        assertThat(result)
                .contains("background-repeat: no-repeat;")
                .contains("background-position: center;")
                .contains("background-size: contain;");
    }

    @Test
    void neverThrowsOnAnyInput() {
        assertThatCode(() -> operation.execute("not even svg markup", false)).doesNotThrowAnyException();
        assertThatCode(() -> operation.execute("", false)).doesNotThrowAnyException();
    }

    @Test
    void group() {
        assertThat(operation.group()).isEqualTo(OperationGroup.WEB);
    }
}
