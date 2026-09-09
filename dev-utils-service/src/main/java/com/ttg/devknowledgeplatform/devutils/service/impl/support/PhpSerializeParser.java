package com.ttg.devknowledgeplatform.devutils.service.impl.support;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses PHP's own {@code serialize()} textual format back into a plain Java value tree Jackson
 * can serialize directly — the exact inverse of {@link PhpSerializeWriter}, and, like
 * {@link PhpArrayParser}, a real validating parser (not a lenient reformatter), since it has to
 * fully understand the value structure to convert it.
 *
 * <p>{@code s:L:"..."}'s own length {@code L} is read as UTF-8 <b>bytes</b>, not Java {@code char}s
 * — required for correctness on any string containing a multi-byte character (matching
 * {@link PhpSerializeWriter}'s own byte-counting on the way out). {@link #consumeStringBytes}
 * walks the input one Unicode code point at a time (not one {@code char}), since a code point
 * outside the Basic Multilingual Plane is one UTF-8 4-byte sequence but <i>two</i> Java
 * {@code char}s (a surrogate pair) — advancing by raw {@code char} count there would split a
 * surrogate pair and silently corrupt the decoded string.
 *
 * <p>An {@code a:N:{...}} array becomes a JSON array when its own keys are exactly the sequence
 * {@code 0, 1, 2, ..., N-1} in that order (PHP's own default auto-increment keys — what
 * {@code serialize()} itself always produces for a plain indexed array) — the same "sequential
 * zero-based integer keys only" rule {@link PhpArrayParser} already applies for its own array
 * literal syntax; any other array (a genuinely associative one, or a re-ordered/gapped integer-
 * keyed one) becomes a JSON object with every key stringified, since JSON object keys are always
 * strings even when the PHP key was an integer.
 */
public final class PhpSerializeParser {

    private final String input;
    private int pos;

    private PhpSerializeParser(String input) {
        this.input = input;
        this.pos = 0;
    }

    public static Object parse(String input) {
        PhpSerializeParser parser = new PhpSerializeParser(input);
        Object value = parser.parseValue();
        if (parser.pos < parser.input.length()) {
            throw parser.errorAt(parser.pos, "Unexpected trailing content after the value");
        }
        return value;
    }

    private Object parseValue() {
        if (pos >= input.length()) {
            throw errorAt(pos, "Unexpected end of input, expected a value");
        }
        char type = input.charAt(pos);
        return switch (type) {
            case 'N' -> parseNull();
            case 'b' -> parseBoolean();
            case 'i' -> parseInt();
            case 'd' -> parseDouble();
            case 's' -> parseString();
            case 'a' -> parseArray();
            default -> throw errorAt(pos, "Unexpected type marker '" + type + "', expected one of N/b/i/d/s/a");
        };
    }

    private Object parseNull() {
        expect("N;");
        return null;
    }

    private Object parseBoolean() {
        expect("b:");
        char c = nextChar("a boolean value ('0' or '1')");
        expect(";");
        if (c == '0') {
            return Boolean.FALSE;
        }
        if (c == '1') {
            return Boolean.TRUE;
        }
        throw errorAt(pos, "Invalid boolean value '" + c + "', expected '0' or '1'");
    }

    private Object parseInt() {
        expect("i:");
        String digits = readUntil(';', "an integer value");
        expect(";");
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            throw errorAt(pos, "Invalid integer literal '" + digits + "'");
        }
    }

    private Object parseDouble() {
        expect("d:");
        String digits = readUntil(';', "a double value");
        expect(";");
        try {
            return Double.parseDouble(digits);
        } catch (NumberFormatException e) {
            throw errorAt(pos, "Invalid double literal '" + digits + "'");
        }
    }

    private String parseString() {
        expect("s:");
        String lengthDigits = readUntil(':', "a string byte length");
        int byteLength;
        try {
            byteLength = Integer.parseInt(lengthDigits);
        } catch (NumberFormatException e) {
            throw errorAt(pos, "Invalid string length '" + lengthDigits + "'");
        }
        expect(":\"");
        String value = consumeStringBytes(byteLength);
        expect("\";");
        return value;
    }

    private String consumeStringBytes(int byteLength) {
        int start = pos;
        int consumedBytes = 0;
        while (consumedBytes < byteLength) {
            if (pos >= input.length()) {
                throw errorAt(start, "Unterminated string literal — expected " + byteLength + " bytes");
            }
            int codePoint = input.codePointAt(pos);
            int codePointByteLength = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            consumedBytes += codePointByteLength;
            pos += Character.charCount(codePoint);
        }
        if (consumedBytes != byteLength) {
            throw errorAt(start, "String content does not match its declared byte length");
        }
        return input.substring(start, pos);
    }

    private record Entry(Object key, Object value) {
    }

    private Object parseArray() {
        int start = pos;
        expect("a:");
        String countDigits = readUntil(':', "an array element count");
        int count;
        try {
            count = Integer.parseInt(countDigits);
        } catch (NumberFormatException e) {
            throw errorAt(pos, "Invalid array count '" + countDigits + "'");
        }
        expect(":{");
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Object key = parseValue();
            Object value = parseValue();
            entries.add(new Entry(key, value));
        }
        if (pos >= input.length() || input.charAt(pos) != '}') {
            throw errorAt(start, "Unterminated array — missing closing '}'");
        }
        pos++;
        return buildResult(entries);
    }

    private Object buildResult(List<Entry> entries) {
        if (isSequentialZeroBasedList(entries)) {
            List<Object> list = new ArrayList<>(entries.size());
            for (Entry entry : entries) {
                list.add(entry.value());
            }
            return list;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (Entry entry : entries) {
            map.put(String.valueOf(entry.key()), entry.value());
        }
        return map;
    }

    private boolean isSequentialZeroBasedList(List<Entry> entries) {
        long expected = 0;
        for (Entry entry : entries) {
            if (!(entry.key() instanceof Long keyLong) || keyLong != expected) {
                return false;
            }
            expected++;
        }
        return true;
    }

    private void expect(String literal) {
        if (!input.regionMatches(pos, literal, 0, literal.length())) {
            throw errorAt(pos, "Expected '" + literal + "'");
        }
        pos += literal.length();
    }

    private char nextChar(String expectedDescription) {
        if (pos >= input.length()) {
            throw errorAt(pos, "Unexpected end of input, expected " + expectedDescription);
        }
        return input.charAt(pos++);
    }

    private String readUntil(char terminator, String expectedDescription) {
        int start = pos;
        while (pos < input.length() && input.charAt(pos) != terminator) {
            pos++;
        }
        if (pos >= input.length()) {
            throw errorAt(start, "Unexpected end of input, expected " + expectedDescription);
        }
        return input.substring(start, pos);
    }

    private PhpSerializeParseException errorAt(int position, String message) {
        int line = 1;
        int column = 1;
        for (int i = 0; i < position && i < input.length(); i++) {
            if (input.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new PhpSerializeParseException(message + " (line " + line + ", column " + column + ")");
    }

    /** Thrown by {@link #parse} on malformed input — the message already carries a
     * {@code "(line N, column M)"} location, the same convention {@link PhpArrayParser}'s own
     * {@code PhpParseException} establishes. */
    public static final class PhpSerializeParseException extends RuntimeException {
        public PhpSerializeParseException(String message) {
            super(message);
        }
    }
}
