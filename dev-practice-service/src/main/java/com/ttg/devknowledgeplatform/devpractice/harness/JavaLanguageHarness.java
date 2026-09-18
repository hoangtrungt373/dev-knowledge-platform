package com.ttg.devknowledgeplatform.devpractice.harness;

import com.ttg.devknowledgeplatform.devpractice.entity.MethodParameter;
import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.enums.ParamType;
import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * The one harness that needs a hand-rolled JSON helper ({@link #JSON_MINI}) — Judge0's Java
 * runtime has no application classpath, so this module's own Jackson dependency is never available
 * inside a submission's sandboxed run, unlike Python's {@code json}/JavaScript's {@code JSON}
 * (both stdlib/native). {@code JsonMini} only supports the closed {@code ParamType} vocabulary
 * (numbers, strings, booleans, arrays, up to one level of nesting for a matrix) — it is
 * deliberately not a general-purpose JSON parser.
 *
 * <p>Judge0's Java language expects a single {@code public class Main} per submission with every
 * other top-level type left package-private in the same file — the user's own {@code class
 * Solution} (no {@code public} modifier, matching this module's own example) already fits that,
 * so the generated program is exactly: {@code JsonMini} + the user's {@code Solution} + a generated
 * {@code public class Main}.
 */
@Component
public class JavaLanguageHarness extends LanguageHarness {

    @Override
    public ProgrammingLanguage language() {
        return ProgrammingLanguage.JAVA;
    }

    @Override
    public String renderStarterCode(Problem problem) {
        String params = problem.getParameters().stream()
                .map(p -> javaType(p.getType()) + " " + p.getName())
                .collect(Collectors.joining(", "));
        return """
                class Solution {
                    public %s %s(%s) {

                    }
                }
                """.formatted(javaType(problem.getReturnType()), problem.getMethodName(), params);
    }

    @Override
    protected String renderPrelude() {
        return "import java.util.*;\n\n" + JSON_MINI + "\n\n";
    }

    @Override
    protected String renderMain(Problem problem) {
        String args = new ArrayList<>(problem.getParameters()).stream()
                .sorted(Comparator.comparing(MethodParameter::getPosition))
                .map(p -> "JsonMini.%s(_parsed.get(%d))".formatted(toMethod(p.getType()), p.getPosition()))
                .collect(Collectors.joining(", "));

        return """


                public class Main {
                    @SuppressWarnings("unchecked")
                    public static void main(String[] args) throws Exception {
                        java.util.Scanner _scanner = new java.util.Scanner(System.in).useDelimiter("\\\\A");
                        String _input = _scanner.hasNext() ? _scanner.next() : "[]";
                        List<Object> _parsed = (List<Object>) JsonMini.parse(_input);
                        Solution _sol = new Solution();
                        %s _result = _sol.%s(%s);
                        System.out.println(JsonMini.write(_result));
                    }
                }
                """.formatted(javaType(problem.getReturnType()), problem.getMethodName(), args);
    }

    private static String javaType(ParamType type) {
        return switch (type) {
            case INT -> "int";
            case LONG -> "long";
            case DOUBLE -> "double";
            case BOOLEAN -> "boolean";
            case STRING -> "String";
            case INT_ARRAY -> "int[]";
            case DOUBLE_ARRAY -> "double[]";
            case BOOLEAN_ARRAY -> "boolean[]";
            case STRING_ARRAY -> "String[]";
            case INT_MATRIX -> "int[][]";
        };
    }

    /** {@code JsonMini}'s parse-side method name for a given {@link ParamType}. */
    private static String toMethod(ParamType type) {
        return switch (type) {
            case INT -> "toInt";
            case LONG -> "toLong";
            case DOUBLE -> "toDouble";
            case BOOLEAN -> "toBool";
            case STRING -> "toStr";
            case INT_ARRAY -> "toIntArray";
            case DOUBLE_ARRAY -> "toDoubleArray";
            case BOOLEAN_ARRAY -> "toBooleanArray";
            case STRING_ARRAY -> "toStringArray";
            case INT_MATRIX -> "toIntMatrix";
        };
    }

    /**
     * A minimal JSON value parser/writer for exactly this module's {@link ParamType} vocabulary —
     * numbers, strings (basic {@code \" \\ \n \t} escapes only, no {@code \\uXXXX}), booleans, and
     * arrays (nested one level for a matrix). Embedded verbatim into every generated Java program.
     */
    private static final String JSON_MINI = """
            class JsonMini {
                private final String s;
                private int i;

                private JsonMini(String s) { this.s = s; this.i = 0; }

                static Object parse(String text) {
                    return new JsonMini(text.trim()).parseValue();
                }

                private Object parseValue() {
                    skipWs();
                    char c = s.charAt(i);
                    if (c == '[') return parseArray();
                    if (c == '"') return parseString();
                    if (c == 't' || c == 'f') return parseBool();
                    return parseNumber();
                }

                private java.util.List<Object> parseArray() {
                    java.util.List<Object> list = new java.util.ArrayList<>();
                    i++;
                    skipWs();
                    if (s.charAt(i) == ']') { i++; return list; }
                    while (true) {
                        list.add(parseValue());
                        skipWs();
                        char c = s.charAt(i++);
                        if (c == ']') break;
                    }
                    return list;
                }

                private String parseString() {
                    StringBuilder sb = new StringBuilder();
                    i++;
                    while (s.charAt(i) != '"') {
                        char c = s.charAt(i++);
                        if (c == '\\\\') {
                            char e = s.charAt(i++);
                            switch (e) {
                                case 'n': sb.append('\\n'); break;
                                case 't': sb.append('\\t'); break;
                                case '"': sb.append('"'); break;
                                case '\\\\': sb.append('\\\\'); break;
                                default: sb.append(e);
                            }
                        } else {
                            sb.append(c);
                        }
                    }
                    i++;
                    return sb.toString();
                }

                private Boolean parseBool() {
                    if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
                    i += 5;
                    return Boolean.FALSE;
                }

                private Double parseNumber() {
                    int start = i;
                    while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
                    return Double.parseDouble(s.substring(start, i));
                }

                private void skipWs() {
                    while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
                }

                static int toInt(Object o) { return ((Number) o).intValue(); }
                static long toLong(Object o) { return ((Number) o).longValue(); }
                static double toDouble(Object o) { return ((Number) o).doubleValue(); }
                static boolean toBool(Object o) { return (Boolean) o; }
                static String toStr(Object o) { return (String) o; }

                @SuppressWarnings("unchecked")
                static int[] toIntArray(Object o) {
                    java.util.List<Object> l = (java.util.List<Object>) o;
                    int[] r = new int[l.size()];
                    for (int k = 0; k < r.length; k++) r[k] = toInt(l.get(k));
                    return r;
                }

                @SuppressWarnings("unchecked")
                static double[] toDoubleArray(Object o) {
                    java.util.List<Object> l = (java.util.List<Object>) o;
                    double[] r = new double[l.size()];
                    for (int k = 0; k < r.length; k++) r[k] = toDouble(l.get(k));
                    return r;
                }

                @SuppressWarnings("unchecked")
                static boolean[] toBooleanArray(Object o) {
                    java.util.List<Object> l = (java.util.List<Object>) o;
                    boolean[] r = new boolean[l.size()];
                    for (int k = 0; k < r.length; k++) r[k] = toBool(l.get(k));
                    return r;
                }

                @SuppressWarnings("unchecked")
                static String[] toStringArray(Object o) {
                    java.util.List<Object> l = (java.util.List<Object>) o;
                    String[] r = new String[l.size()];
                    for (int k = 0; k < r.length; k++) r[k] = toStr(l.get(k));
                    return r;
                }

                @SuppressWarnings("unchecked")
                static int[][] toIntMatrix(Object o) {
                    java.util.List<Object> l = (java.util.List<Object>) o;
                    int[][] r = new int[l.size()][];
                    for (int k = 0; k < r.length; k++) r[k] = toIntArray(l.get(k));
                    return r;
                }

                static String write(int v) { return String.valueOf(v); }
                static String write(long v) { return String.valueOf(v); }
                static String write(double v) { return String.valueOf(v); }
                static String write(boolean v) { return String.valueOf(v); }

                static String write(String v) {
                    StringBuilder sb = new StringBuilder();
                    sb.append('"');
                    for (int k = 0; k < v.length(); k++) {
                        char c = v.charAt(k);
                        if (c == '"' || c == '\\\\') { sb.append('\\\\'); }
                        sb.append(c);
                    }
                    sb.append('"');
                    return sb.toString();
                }

                static String write(int[] v) {
                    StringBuilder sb = new StringBuilder("[");
                    for (int k = 0; k < v.length; k++) { if (k > 0) sb.append(','); sb.append(v[k]); }
                    return sb.append(']').toString();
                }

                static String write(double[] v) {
                    StringBuilder sb = new StringBuilder("[");
                    for (int k = 0; k < v.length; k++) { if (k > 0) sb.append(','); sb.append(v[k]); }
                    return sb.append(']').toString();
                }

                static String write(boolean[] v) {
                    StringBuilder sb = new StringBuilder("[");
                    for (int k = 0; k < v.length; k++) { if (k > 0) sb.append(','); sb.append(v[k]); }
                    return sb.append(']').toString();
                }

                static String write(String[] v) {
                    StringBuilder sb = new StringBuilder("[");
                    for (int k = 0; k < v.length; k++) { if (k > 0) sb.append(','); sb.append(write(v[k])); }
                    return sb.append(']').toString();
                }

                static String write(int[][] v) {
                    StringBuilder sb = new StringBuilder("[");
                    for (int k = 0; k < v.length; k++) { if (k > 0) sb.append(','); sb.append(write(v[k])); }
                    return sb.append(']').toString();
                }
            }""";
}
