package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AsciiToHexOperationTest {

    private final AsciiToHexOperation operation = new AsciiToHexOperation();

    @Test
    void encodesTheExactReportedExample() {
        assertThat(operation.execute("Serialize JSON")).isEqualTo("53 65 72 69 61 6c 69 7a 65 20 4a 53 4f 4e");
    }

    // Operates on UTF-8 bytes, not raw Java chars — verified against a real standalone Java
    // harness first, not assumed, the same discipline Base64EncodeOperationTest's own multi-byte
    // case already establishes. 'é' is a 2-byte UTF-8 sequence.
    @Test
    void encodesAMultiByteCharacterAsItsUtf8Bytes() {
        assertThat(operation.execute("café")).isEqualTo("63 61 66 c3 a9");
    }

    @Test
    void emptyStringEncodesToEmptyString() {
        assertThat(operation.execute("")).isEqualTo("");
    }
}
