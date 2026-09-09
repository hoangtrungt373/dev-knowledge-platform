package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Base64EncodeOperationTest {

    private final Base64EncodeOperation operation = new Base64EncodeOperation();

    @Test
    void encodesPlainAsciiText() {
        assertThat(operation.execute("Vui Coding")).isEqualTo("VnVpIENvZGluZw==");
    }

    // English text plus an emoji — the emoji alone is enough to exercise a genuinely multi-byte
    // UTF-8 sequence (U+1F44B, 4 bytes) without reaching for non-English script — verified against
    // a real standalone Java harness (compiled/run with explicit -encoding UTF-8, and the source
    // file's own code points double-checked via String#codePoints(), since a platform-default-
    // charset mismatch is a real pitfall this same harness pattern already caught once on Windows)
    // before writing this assertion, not assumed.
    @Test
    void encodesMultiByteUtf8TextIncludingAnEmoji() {
        assertThat(operation.execute("Hello from Vui Coding 👋"))
                .isEqualTo("SGVsbG8gZnJvbSBWdWkgQ29kaW5nIPCfkYs=");
    }

    @Test
    void emptyStringEncodesToEmptyString() {
        assertThat(operation.execute("")).isEqualTo("");
    }
}
