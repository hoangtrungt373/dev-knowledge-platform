package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HtmlEntityDecodeOperationTest {

    private final HtmlEntityDecodeOperation operation = new HtmlEntityDecodeOperation();

    @Test
    void decodesReservedMarkupCharacterReferencesInAnHtmlSnippet() {
        assertThat(operation.execute("&lt;main class=&quot;hero&quot;&gt;Dev Knowledge Platform © 2026&lt;/main&gt;"))
                .isEqualTo("<main class=\"hero\">Dev Knowledge Platform © 2026</main>");
    }

    @Test
    void decodesBothApostropheSpellingsAndTheAmpersandReference() {
        assertThat(operation.execute("Ben &amp; Jerry&#39;s")).isEqualTo("Ben & Jerry's");
        assertThat(operation.execute("Ben &amp; Jerry&apos;s")).isEqualTo("Ben & Jerry's");
        assertThat(operation.execute("Ben &amp; Jerry&#x27;s")).isEqualTo("Ben & Jerry's");
    }

    // A single left-to-right scan decodes exactly one level — the outer '&amp;' becomes '&', but
    // the 'lt;' that follows it is not re-scanned as part of that same match.
    @Test
    void decodesOnlyOneLevelForADoublyEscapedEntity() {
        assertThat(operation.execute("&amp;lt;")).isEqualTo("&lt;");
    }

    // An unrecognized entity (not one of the fixed set HtmlEntityEncodeOperation emits) passes
    // through untouched, the same way a browser leaves an unknown reference alone.
    @Test
    void leavesAnUnrecognizedEntityUntouched() {
        assertThat(operation.execute("&copy; &nbsp;")).isEqualTo("&copy; &nbsp;");
    }

    @Test
    void emptyStringDecodesToEmptyString() {
        assertThat(operation.execute("")).isEqualTo("");
    }
}
