package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;

class JwtDebuggerOperationTest {

    // The exact reported example — header {"alg":"HS256","typ":"JWT"}, payload
    // {"sub":"1234567890","name":"Vui Coding","iat":1516239022}, signature "demo-signature" (a
    // dummy value, deliberately never decoded — see the operation's own Javadoc for why).
    private static final String EXAMPLE_JWT =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
                    + ".eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IlZ1aSBDb2RpbmciLCJpYXQiOjE1MTYyMzkwMjJ9"
                    + ".demo-signature";

    private ObjectMapper objectMapper;
    private JwtDebuggerOperation operation;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        operation = new JwtDebuggerOperation(objectMapper);
    }

    @Test
    void decodesTheExactReportedExample() {
        String result = operation.execute(EXAMPLE_JWT, false);

        assertThat(result).isEqualTo(
                "{\n"
                        + "  \"header\": {\n"
                        + "    \"alg\": \"HS256\",\n"
                        + "    \"typ\": \"JWT\"\n"
                        + "  },\n"
                        + "  \"payload\": {\n"
                        + "    \"sub\": \"1234567890\",\n"
                        + "    \"name\": \"Vui Coding\",\n"
                        + "    \"iat\": 1516239022\n"
                        + "  },\n"
                        + "  \"signature\": \"demo-signature\"\n"
                        + "}"
        );
    }

    @Test
    void producesCompactSingleLineOutputWhenMinified() throws Exception {
        String result = operation.execute(EXAMPLE_JWT, true);

        assertThat(result).doesNotContain("\n");
        assertThat(objectMapper.readTree(result).get("signature").asText()).isEqualTo("demo-signature");
        assertThat(objectMapper.readTree(result).get("header").get("alg").asText()).isEqualTo("HS256");
    }

    @Test
    void toleratesLeadingAndTrailingWhitespace() {
        String result = operation.execute("  " + EXAMPLE_JWT + "\n", false);

        assertThat(result).contains("\"alg\": \"HS256\"");
    }

    @Test
    void rejectsInputWithTheWrongSegmentCount() {
        assertThatThrownBy(() -> operation.execute("only.two", false))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(DevUtilsErrorCode.INVALID_JWT))
                .hasMessageContaining("found 2");
    }

    @Test
    void rejectsAHeaderSegmentThatIsNotValidBase64Url() {
        assertThatThrownBy(() -> operation.execute("not!valid.eyJhIjoxfQ.sig", false))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(DevUtilsErrorCode.INVALID_JWT))
                .hasMessageContaining("header");
    }

    @Test
    void rejectsAPayloadSegmentThatDecodesToInvalidJson() {
        // "bm90IGpzb24" is the Base64URL encoding of the literal text "not json" — valid
        // Base64URL, invalid JSON once decoded.
        assertThatThrownBy(() -> operation.execute("eyJhIjoxfQ.bm90IGpzb24.sig", false))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(DevUtilsErrorCode.INVALID_JWT))
                .hasMessageContaining("payload");
    }
}
