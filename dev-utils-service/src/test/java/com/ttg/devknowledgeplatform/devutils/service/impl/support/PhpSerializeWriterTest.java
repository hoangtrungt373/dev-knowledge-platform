package com.ttg.devknowledgeplatform.devutils.service.impl.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class PhpSerializeWriterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode parse(String json) throws Exception {
        return mapper.readTree(json);
    }

    @Test
    void serializesTheExactReportedExample() throws Exception {
        JsonNode node = parse("{\"name\":\"DevKnowledge\",\"active\":true,\"count\":47}");
        assertThat(PhpSerializeWriter.write(node))
                .isEqualTo("a:3:{s:4:\"name\";s:12:\"DevKnowledge\";s:6:\"active\";b:1;s:5:\"count\";i:47;}");
    }

    @Test
    void serializesAJsonArrayAsASequentiallyIndexedPhpArray() throws Exception {
        JsonNode node = parse("[\"a\",\"b\",\"c\"]");
        assertThat(PhpSerializeWriter.write(node)).isEqualTo("a:3:{i:0;s:1:\"a\";i:1;s:1:\"b\";i:2;s:1:\"c\";}");
    }

    @Test
    void serializesNestedObjectsAndArrays() throws Exception {
        JsonNode node = parse("{\"tags\":[\"json\",\"php\"],\"meta\":{\"ok\":true}}");
        assertThat(PhpSerializeWriter.write(node)).isEqualTo(
                "a:2:{s:4:\"tags\";a:2:{i:0;s:4:\"json\";i:1;s:3:\"php\";}s:4:\"meta\";a:1:{s:2:\"ok\";b:1;}}");
    }

    @Test
    void serializesNullAndFalse() throws Exception {
        JsonNode node = parse("{\"a\":null,\"b\":false}");
        assertThat(PhpSerializeWriter.write(node)).isEqualTo("a:2:{s:1:\"a\";N;s:1:\"b\";b:0;}");
    }

    // A string's own length is counted in UTF-8 bytes, not Java chars — verified against a real
    // standalone Java harness first, not assumed, the same discipline Base64EncodeOperationTest's
    // own multi-byte case already establishes. "café" has 4 Java chars but 5 UTF-8 bytes (é is a
    // 2-byte sequence); "Hi👋" has 4 Java chars (the emoji is a surrogate pair) but 6 UTF-8 bytes.
    @Test
    void countsAMultiByteStringsLengthInUtf8BytesNotJavaChars() throws Exception {
        assertThat(PhpSerializeWriter.write(parse("\"café\""))).isEqualTo("s:5:\"café\";");
        assertThat(PhpSerializeWriter.write(parse("\"Hi\\uD83D\\uDC4B\""))).isEqualTo("s:6:\"Hi👋\";");
    }

    @Test
    void serializesABareScalarValue() throws Exception {
        assertThat(PhpSerializeWriter.write(parse("47"))).isEqualTo("i:47;");
        assertThat(PhpSerializeWriter.write(parse("\"hi\""))).isEqualTo("s:2:\"hi\";");
    }
}
