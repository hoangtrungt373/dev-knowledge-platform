package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HtmlEntityEncodeOperationTest {

    private final HtmlEntityEncodeOperation operation = new HtmlEntityEncodeOperation();

    @Test
    void escapesReservedMarkupCharactersInAnHtmlSnippet() {
        assertThat(operation.execute("<main class=\"hero\">Dev Knowledge Platform © 2026</main>"))
                .isEqualTo("&lt;main class=&quot;hero&quot;&gt;Dev Knowledge Platform © 2026&lt;/main&gt;");
    }

    // Non-ASCII text is left completely untouched — this operation escapes only the five
    // structurally-significant markup characters, not a full ISO-8859-1/HTML4 named-entity table
    // (which would rewrite '©' to '&copy;' too — see this class's own Javadoc for why that's
    // deliberately out of scope).
    @Test
    void leavesNonAsciiCharactersUnescaped() {
        assertThat(operation.execute("café © 2026")).isEqualTo("café © 2026");
    }

    @Test
    void escapesALiteralAmpersandExactlyOnceRatherThanReEscapingItsOwnOutput() {
        assertThat(operation.execute("Ben & Jerry's")).isEqualTo("Ben &amp; Jerry&#39;s");
    }

    @Test
    void emptyStringEncodesToEmptyString() {
        assertThat(operation.execute("")).isEqualTo("");
    }
}
