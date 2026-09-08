package com.ttg.devknowledgeplatform.devutils.service.impl.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.ttg.devknowledgeplatform.devutils.dto.StringCaseResponse;

/**
 * Splits arbitrary text into words and re-joins them into every {@link StringCaseResponse} case
 * variant — used by {@code StringCaseOperation}. A pure text transform with no notion of "invalid"
 * input (see {@code DevUtilsErrorCode}'s own Javadoc) — {@link #convert} never throws.
 *
 * <p><b>Word splitting</b> handles both delimiter-separated input (spaces, underscores, hyphens,
 * or any other run of non-alphanumeric characters — all normalized to a single boundary) and
 * already-cased input (camelCase/PascalCase boundaries, including a trailing-acronym boundary,
 * e.g. {@code XMLParser} → {@code XML}, {@code Parser}) — the standard two-part heuristic most
 * case-conversion tools use: insert a boundary between a lowercase-or-digit and a following
 * uppercase letter, and between the last letter of an uppercase run and a following
 * capitalized word. This means feeding the output of one case variant back in as input to convert
 * to a *different* variant works correctly (e.g. {@code snake_case} input still splits into the
 * same words a plain sentence would).
 */
public final class StringCaseConverter {

    private StringCaseConverter() {
    }

    public static StringCaseResponse convert(String input) {
        List<String> words = splitIntoWords(input);
        return new StringCaseResponse(
                joinCamel(words, false),
                joinCamel(words, true),
                joinWithSeparator(words, "_", StringCaseConverter::lower),
                joinWithSeparator(words, "-", StringCaseConverter::lower),
                joinWithSeparator(words, "_", StringCaseConverter::upper),
                joinWithSeparator(words, " ", StringCaseConverter::capitalize),
                joinSentenceCase(words)
        );
    }

    static List<String> splitIntoWords(String input) {
        String normalized = input.replaceAll("[^A-Za-z0-9]+", " ");
        // lowercase/digit -> uppercase boundary (e.g. "shipAnd" -> "ship And")
        normalized = normalized.replaceAll("([a-z0-9])([A-Z])", "$1 $2");
        // uppercase-run -> capitalized-word boundary (e.g. "XMLParser" -> "XML Parser")
        normalized = normalized.replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");

        List<String> words = new ArrayList<>();
        for (String word : normalized.trim().split("\\s+")) {
            if (!word.isEmpty()) {
                words.add(word.toLowerCase(Locale.ROOT));
            }
        }
        return words;
    }

    private static String joinCamel(List<String> words, boolean capitalizeFirst) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < words.size(); i++) {
            out.append(i == 0 && !capitalizeFirst ? lower(words.get(i)) : capitalize(words.get(i)));
        }
        return out.toString();
    }

    private static String joinSentenceCase(List<String> words) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < words.size(); i++) {
            if (i > 0) {
                out.append(' ');
            }
            out.append(i == 0 ? capitalize(words.get(i)) : lower(words.get(i)));
        }
        return out.toString();
    }

    private static String joinWithSeparator(List<String> words, String separator, java.util.function.UnaryOperator<String> transform) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < words.size(); i++) {
            if (i > 0) {
                out.append(separator);
            }
            out.append(transform.apply(words.get(i)));
        }
        return out.toString();
    }

    private static String lower(String word) {
        return word.toLowerCase(Locale.ROOT);
    }

    private static String upper(String word) {
        return word.toUpperCase(Locale.ROOT);
    }

    private static String capitalize(String word) {
        if (word.isEmpty()) {
            return word;
        }
        return Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase(Locale.ROOT);
    }
}
