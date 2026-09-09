package com.ttg.devknowledgeplatform.devutils.service.impl.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a PHP array literal — bracket syntax {@code [...]} or legacy {@code array(...)} — into a
 * plain Java value tree Jackson can serialize directly: {@link Map}{@code <String, Object>} for an
 * associative array, {@link List}{@code <Object>} for a purely sequential one, and boxed
 * String/Long/Double/Boolean/{@code null} for scalars. Used by {@code PhpToJsonOperation}.
 *
 * <p><b>Unlike {@code CurlyBraceFormatter}/{@code SqlFormatter}, this is a real, validating
 * parser</b> — it has to fully understand the value structure to convert it, not just track
 * brace/keyword boundaries, so {@link #parse} throws {@link PhpParseException} (carrying a
 * {@code "<message> (line N, column M)"} location, the same convention
 * {@code ParsingExceptionMessages}/{@code XmlOperation} already establish) on malformed input.
 *
 * <p>Tolerates being handed a full PHP snippet, not just the bare array literal — an optional
 * leading {@code <?php} tag, an optional {@code return} keyword before the array, and an optional
 * trailing {@code ;}/{@code ?>} are all skipped if present, so {@code JsonToPhpOperation}'s own
 * output can be fed straight back into this parser unmodified.
 *
 * <p>An array with no explicit {@code =>} keys at all — or whose explicit keys happen to form the
 * exact sequence {@code 0, 1, 2, ...} in order (PHP's own default auto-increment keys, e.g. from a
 * {@code var_export()} dump) — becomes a JSON array; any other array becomes a JSON object, with
 * every key stringified (JSON object keys are always strings, even when the PHP key was an
 * integer). A later un-keyed element after an explicit integer key resumes numbering from that
 * key plus one, approximating (not exactly replicating) PHP's own "highest integer key ever used"
 * auto-increment rule — a reasonable simplification for a rare edge case (mixing explicit and
 * implicit keys in one array) most real-world PHP snippets don't exercise.
 *
 * <p>String literals: {@code '...'} (PHP single-quoted — only {@code \'}/{@code \\} are real
 * escapes; everything else is a literal backslash followed by that character) and {@code "..."}
 * (PHP double-quoted — the common escape sequences {@code \n}/{@code \t}/{@code \r}/{@code \"}/
 * {@code \\}/{@code \$} are honored; variable interpolation like {@code "$name"} is deliberately
 * *not* evaluated — this is a literal text converter, not a PHP interpreter, so an interpolated
 * variable is left exactly as written). {@code //}/{@code #} line comments and block comments
 * (slash-star ... star-slash) are skipped anywhere between tokens, the same leniency
 * {@code SqlFormatter} affords SQL comments.
 */
public final class PhpArrayParser {

    private final String input;
    private int pos;

    private PhpArrayParser(String input) {
        this.input = input;
        this.pos = 0;
    }

    public static Object parse(String input) {
        PhpArrayParser parser = new PhpArrayParser(input);
        parser.skipWhitespaceAndComments();
        parser.skipOptionalPhpOpenTag();
        parser.skipWhitespaceAndComments();
        parser.skipOptionalReturnKeyword();
        parser.skipWhitespaceAndComments();
        Object value = parser.parseValue();
        parser.skipWhitespaceAndComments();
        parser.skipOptionalChar(';');
        parser.skipWhitespaceAndComments();
        parser.skipOptionalPhpCloseTag();
        parser.skipWhitespaceAndComments();
        if (parser.pos < parser.input.length()) {
            throw parser.errorAt(parser.pos, "Unexpected trailing content after the array");
        }
        return value;
    }

    private void skipOptionalPhpOpenTag() {
        if (input.regionMatches(true, pos, "<?php", 0, 5)) {
            pos += 5;
        }
    }

    private void skipOptionalPhpCloseTag() {
        if (input.regionMatches(pos, "?>", 0, 2)) {
            pos += 2;
        }
    }

    private void skipOptionalReturnKeyword() {
        if (matchesKeywordAt(pos, "return")) {
            pos += "return".length();
        }
    }

    private void skipOptionalChar(char c) {
        if (pos < input.length() && input.charAt(pos) == c) {
            pos++;
        }
    }

    private Object parseValue() {
        skipWhitespaceAndComments();
        if (pos >= input.length()) {
            throw errorAt(pos, "Unexpected end of input, expected a value");
        }
        char c = input.charAt(pos);
        if (c == '[' || matchesKeywordAt(pos, "array")) {
            return parseArray();
        }
        if (c == '\'' || c == '"') {
            return parseString();
        }
        if (c == '-' || Character.isDigit(c)) {
            return parseNumber();
        }
        if (matchesKeywordAt(pos, "true")) {
            pos += 4;
            return Boolean.TRUE;
        }
        if (matchesKeywordAt(pos, "false")) {
            pos += 5;
            return Boolean.FALSE;
        }
        if (matchesKeywordAt(pos, "null")) {
            pos += 4;
            return null;
        }
        throw errorAt(pos, "Unexpected character '" + c + "', expected a value");
    }

    /** Whether {@code keyword} (case-insensitive) starts at {@code at}, and isn't just the prefix
     * of a longer identifier (e.g. matching "array" inside "arrayValue"). */
    private boolean matchesKeywordAt(int at, String keyword) {
        if (at + keyword.length() > input.length()) {
            return false;
        }
        if (!input.regionMatches(true, at, keyword, 0, keyword.length())) {
            return false;
        }
        int after = at + keyword.length();
        return after >= input.length() || !isIdentifierChar(input.charAt(after));
    }

    private boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private record Entry(Object key, Object value, boolean hasKey) {
    }

    private Object parseArray() {
        int start = pos;
        char closing;
        if (input.charAt(pos) == '[') {
            pos++;
            closing = ']';
        } else {
            pos += "array".length();
            skipWhitespaceAndComments();
            if (pos >= input.length() || input.charAt(pos) != '(') {
                throw errorAt(pos, "Expected '(' after 'array'");
            }
            pos++;
            closing = ')';
        }

        List<Entry> entries = new ArrayList<>();
        skipWhitespaceAndComments();
        while (true) {
            skipWhitespaceAndComments();
            if (pos >= input.length()) {
                throw errorAt(start, "Unterminated array — missing closing '" + closing + "'");
            }
            if (input.charAt(pos) == closing) {
                pos++;
                break;
            }
            Object first = parseValue();
            skipWhitespaceAndComments();
            if (pos + 1 < input.length() && input.charAt(pos) == '=' && input.charAt(pos + 1) == '>') {
                pos += 2;
                entries.add(new Entry(first, parseValue(), true));
            } else {
                entries.add(new Entry(null, first, false));
            }
            skipWhitespaceAndComments();
            if (pos < input.length() && input.charAt(pos) == ',') {
                pos++;
                continue;
            }
            skipWhitespaceAndComments();
            if (pos < input.length() && input.charAt(pos) == closing) {
                pos++;
                break;
            }
            throw errorAt(pos, "Expected ',' or '" + closing + "'");
        }
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
        long autoIndex = 0;
        for (Entry entry : entries) {
            String key;
            if (entry.hasKey()) {
                key = stringifyKey(entry.key());
                if (entry.key() instanceof Number number && isWholeNumber(number)) {
                    autoIndex = number.longValue() + 1;
                }
            } else {
                key = String.valueOf(autoIndex);
                autoIndex++;
            }
            map.put(key, entry.value());
        }
        return map;
    }

    private boolean isSequentialZeroBasedList(List<Entry> entries) {
        long expected = 0;
        for (Entry entry : entries) {
            if (entry.hasKey()) {
                if (!(entry.key() instanceof Number number) || !isWholeNumber(number) || number.longValue() != expected) {
                    return false;
                }
            }
            expected++;
        }
        return true;
    }

    private boolean isWholeNumber(Number number) {
        return number.doubleValue() == Math.floor(number.doubleValue()) && !Double.isInfinite(number.doubleValue());
    }

    private String stringifyKey(Object key) {
        if (key instanceof Number number && isWholeNumber(number)) {
            return String.valueOf(number.longValue());
        }
        return String.valueOf(key);
    }

    private String parseString() {
        char quote = input.charAt(pos);
        int start = pos;
        pos++;
        StringBuilder out = new StringBuilder();
        while (true) {
            if (pos >= input.length()) {
                throw errorAt(start, "Unterminated string literal");
            }
            char c = input.charAt(pos);
            if (c == '\\' && pos + 1 < input.length()) {
                char next = input.charAt(pos + 1);
                if (quote == '\'') {
                    if (next == '\'' || next == '\\') {
                        out.append(next);
                        pos += 2;
                    } else {
                        out.append(c);
                        pos++;
                    }
                } else {
                    switch (next) {
                        case 'n' -> { out.append('\n'); pos += 2; }
                        case 't' -> { out.append('\t'); pos += 2; }
                        case 'r' -> { out.append('\r'); pos += 2; }
                        case '"' -> { out.append('"'); pos += 2; }
                        case '\\' -> { out.append('\\'); pos += 2; }
                        case '$' -> { out.append('$'); pos += 2; }
                        default -> { out.append(c); pos++; }
                    }
                }
                continue;
            }
            if (c == quote) {
                pos++;
                return out.toString();
            }
            out.append(c);
            pos++;
        }
    }

    private Object parseNumber() {
        int start = pos;
        if (input.charAt(pos) == '-') {
            pos++;
        }
        boolean isFloat = false;
        consumeDigits();
        if (pos < input.length() && input.charAt(pos) == '.') {
            isFloat = true;
            pos++;
            consumeDigits();
        }
        if (pos < input.length() && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
            isFloat = true;
            pos++;
            if (pos < input.length() && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) {
                pos++;
            }
            consumeDigits();
        }
        String raw = input.substring(start, pos).replace("_", "");
        if (raw.isEmpty() || raw.equals("-")) {
            throw errorAt(start, "Invalid number literal");
        }
        // Long.parseLong/Double.parseDouble both throw the unchecked NumberFormatException for a
        // syntactically-plausible-looking literal they still can't actually parse — an integer
        // wider than a long (e.g. 22+ digits), or an exponent with no digits after 'e' (consumeDigits()
        // above is a no-op there, so raw ends up as e.g. "1e"). Uncaught, that exception isn't a
        // PhpParseException, so it would skip PhpToJsonOperation's own catch clause entirely and
        // surface as a generic 500 instead of the clean 400/INVALID_PHP this parser exists to
        // produce for exactly this class of malformed input.
        try {
            return isFloat ? (Object) Double.parseDouble(raw) : (Object) Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw errorAt(start, "Invalid number literal");
        }
    }

    private void consumeDigits() {
        while (pos < input.length() && (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '_')) {
            pos++;
        }
    }

    private void skipWhitespaceAndComments() {
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (Character.isWhitespace(c)) {
                pos++;
                continue;
            }
            if (c == '/' && pos + 1 < input.length() && input.charAt(pos + 1) == '/') {
                pos = TextScanning.indexOfOrEnd(input, '\n', pos + 2);
                continue;
            }
            if (c == '#') {
                pos = TextScanning.indexOfOrEnd(input, '\n', pos + 1);
                continue;
            }
            if (c == '/' && pos + 1 < input.length() && input.charAt(pos + 1) == '*') {
                pos = TextScanning.indexOfOrEnd(input, "*/", pos + 2);
                continue;
            }
            break;
        }
    }

    private PhpParseException errorAt(int position, String message) {
        return new PhpParseException(message + ParserLocations.locationSuffix(input, position));
    }

    /** Thrown by {@link #parse} on malformed input — the message already carries a
     * {@code "(line N, column M)"} location, so callers can pass {@link #getMessage()} straight
     * through to {@code BusinessException} without any further formatting. */
    public static final class PhpParseException extends RuntimeException {
        public PhpParseException(String message) {
            super(message);
        }
    }
}
