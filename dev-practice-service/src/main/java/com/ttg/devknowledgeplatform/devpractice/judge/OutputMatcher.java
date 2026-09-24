package com.ttg.devknowledgeplatform.devpractice.judge;

import java.util.Iterator;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttg.devknowledgeplatform.devpractice.enums.ParamType;

/**
 * Decides whether a submission's stdout counts as the correct answer for one test case — a
 * structural, return-type-aware JSON comparison, never a raw string compare.
 *
 * <ul>
 *   <li><b>Structural:</b> {@code [0, 1]} and {@code [0,1]} are equal — each language's JSON
 *       writer formats differently (Python's {@code json.dumps} adds spaces).</li>
 *   <li><b>Floating-point return types</b> ({@code DOUBLE}, {@code DOUBLE_ARRAY}) compare every
 *       number within {@value #DOUBLE_TOLERANCE} (absolute, or relative for magnitudes above 1) —
 *       the same convention LeetCode states on its floating-point problems. This absorbs both
 *       rounding error ({@code 0.1 + 0.2}) and cross-language rendering: JavaScript's
 *       {@code JSON.stringify(2.0)} prints {@code 2} where Java/Python print {@code 2.0}.</li>
 *   <li><b>Every other return type</b> compares numbers by exact value ({@link java.math.BigDecimal}),
 *       so {@code 2} and {@code 2.0} match but {@code 2} and {@code 2.5} never do, and a
 *       {@code long} beyond 2<sup>53</sup> is compared without rounding.</li>
 * </ul>
 *
 * <p>This is also why Judge0's own {@code expected_output} comparison is never used — it's a raw
 * string compare (see {@link Judge0Status}).
 */
@Component
public class OutputMatcher {

    /** Absolute tolerance for magnitudes up to 1, relative tolerance above that. */
    static final double DOUBLE_TOLERANCE = 1e-5;

    private final ObjectMapper objectMapper;

    public OutputMatcher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param actualStdout   the program's stdout (may be {@code null}/blank if it printed nothing)
     * @param expectedOutput the test case's JSON-encoded expected return value
     * @param returnType     the problem's declared return type, selecting exact vs. tolerant
     *                       numeric comparison
     * @return {@code true} if the output is an acceptable answer; {@code false} if it differs or
     *         either side isn't valid JSON
     */
    public boolean matches(String actualStdout, String expectedOutput, ParamType returnType) {
        try {
            JsonNode actual = objectMapper.readTree(actualStdout == null ? "" : actualStdout.trim());
            JsonNode expected = objectMapper.readTree(expectedOutput);
            boolean floating = returnType == ParamType.DOUBLE || returnType == ParamType.DOUBLE_ARRAY;
            return actual != null && expected != null && nodesMatch(actual, expected, floating);
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    private static boolean nodesMatch(JsonNode actual, JsonNode expected, boolean floating) {
        if (actual.isNumber() && expected.isNumber()) {
            return floating
                    ? withinTolerance(actual.doubleValue(), expected.doubleValue())
                    : actual.decimalValue().compareTo(expected.decimalValue()) == 0;
        }
        if (actual.isArray() && expected.isArray()) {
            if (actual.size() != expected.size()) {
                return false;
            }
            Iterator<JsonNode> a = actual.elements();
            Iterator<JsonNode> e = expected.elements();
            while (a.hasNext()) {
                if (!nodesMatch(a.next(), e.next(), floating)) {
                    return false;
                }
            }
            return true;
        }
        return actual.equals(expected);
    }

    private static boolean withinTolerance(double actual, double expected) {
        double scale = Math.max(1.0, Math.max(Math.abs(actual), Math.abs(expected)));
        return Math.abs(actual - expected) <= DOUBLE_TOLERANCE * scale;
    }
}
