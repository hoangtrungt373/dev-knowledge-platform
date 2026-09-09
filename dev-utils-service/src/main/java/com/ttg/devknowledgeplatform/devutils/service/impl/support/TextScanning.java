package com.ttg.devknowledgeplatform.devutils.service.impl.support;

/**
 * A single tiny scanning helper — "find {@code needle} starting from {@code from}, or the end of
 * the string if it's never found" — used by every one of this module's hand-rolled lenient
 * reformatters/parsers to consume an unterminated comment/string/literal leniently instead of
 * treating a missing closer as an error. Extracted once a third copy (identical down to the
 * variable names) turned up: {@link CurlyBraceFormatter}, {@link SqlFormatter}, and
 * {@link PhpArrayParser} had each defined this same pair of overloads independently.
 */
final class TextScanning {

    private TextScanning() {
    }

    /** @return the index right after the first occurrence of {@code needle} at or after
     *          {@code from}, or {@code s.length()} if {@code needle} never occurs */
    static int indexOfOrEnd(String s, String needle, int from) {
        int idx = s.indexOf(needle, from);
        return idx < 0 ? s.length() : idx + needle.length();
    }

    /** @return the index of the first occurrence of {@code needle} at or after {@code from}, or
     *          {@code s.length()} if {@code needle} never occurs */
    static int indexOfOrEnd(String s, char needle, int from) {
        int idx = s.indexOf(needle, from);
        return idx < 0 ? s.length() : idx;
    }
}
