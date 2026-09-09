package com.ttg.devknowledgeplatform.devutils.service.impl.support;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Renders a Jackson {@link JsonNode} tree as PHP's own {@code serialize()} textual format —
 * {@code a:N:{...}} (array, keyed by string or by sequential integer index), {@code s:L:"..."}
 * (string, {@code L} counted in UTF-8 <b>bytes</b>, matching PHP's own byte-oriented string
 * length — not Java {@code char} count, which would produce a wrong length for any non-ASCII
 * text), {@code i:N;} (integer), {@code d:N;} (float/double), {@code b:0;}/{@code b:1;} (boolean),
 * and {@code N;} (null). Genuinely different from this module's own PHP <i>array-literal</i>
 * syntax ({@code ['key' => 'value']}, see {@link PhpArrayWriter}/{@link PhpArrayParser}) — this is
 * the wire format PHP's {@code serialize()}/{@code unserialize()} functions themselves produce/
 * consume (the shape WordPress options, PHP session data, etc. are stored in), not PHP source
 * code.
 *
 * <p>A JSON object and a JSON array both become PHP's one array type ({@code a:N:{...}}), keyed
 * either by an object's own string field names or by an array's own sequential integer index —
 * matching how {@code json_decode($json, true)} (PHP's own "decode as associative array" mode)
 * represents both, since PHP has no separate list type.
 *
 * <p>Double formatting uses Java's own {@link Double#toString(double)}, not a byte-for-byte
 * replica of PHP's {@code serialize_precision} rules — close enough to round-trip through
 * {@link PhpSerializeParser} but not guaranteed to match PHP's own output character-for-character
 * on every float value, the same "reasonable effort, not exhaustive" trade-off
 * {@code SqlFormatter}/{@code CurlyBraceFormatter} already make elsewhere in this module for a
 * format with no single Java-library equivalent to delegate to.
 */
public final class PhpSerializeWriter {

    private PhpSerializeWriter() {
    }

    public static String write(JsonNode node) {
        StringBuilder out = new StringBuilder();
        writeValue(node, out);
        return out.toString();
    }

    private static void writeValue(JsonNode node, StringBuilder out) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            out.append("N;");
        } else if (node.isBoolean()) {
            out.append("b:").append(node.booleanValue() ? 1 : 0).append(';');
        } else if (node.isIntegralNumber()) {
            out.append("i:").append(node.asLong()).append(';');
        } else if (node.isFloatingPointNumber()) {
            out.append("d:").append(node.asDouble()).append(';');
        } else if (node.isTextual()) {
            writeString(node.textValue(), out);
        } else if (node.isArray()) {
            out.append("a:").append(node.size()).append(":{");
            for (int i = 0; i < node.size(); i++) {
                out.append("i:").append(i).append(';');
                writeValue(node.get(i), out);
            }
            out.append('}');
        } else if (node.isObject()) {
            out.append("a:").append(node.size()).append(":{");
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                writeString(entry.getKey(), out);
                writeValue(entry.getValue(), out);
            }
            out.append('}');
        } else {
            // Not reachable from a plain JSON parse tree (binary/POJO nodes aren't produced by
            // ObjectMapper#readTree) — fall back to PHP's own null representation rather than
            // throwing on a node type this format has no real equivalent for.
            out.append("N;");
        }
    }

    private static void writeString(String value, StringBuilder out) {
        int byteLength = value.getBytes(StandardCharsets.UTF_8).length;
        out.append("s:").append(byteLength).append(":\"").append(value).append("\";");
    }
}
