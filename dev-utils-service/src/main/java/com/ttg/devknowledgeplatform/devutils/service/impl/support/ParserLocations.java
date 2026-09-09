package com.ttg.devknowledgeplatform.devutils.service.impl.support;

/**
 * Computes a 1-based {@code "(line N, column M)"} location suffix for a character offset into a
 * hand-rolled recursive-descent parser's own input string — the identical newline-counting loop
 * {@link PhpArrayParser} and {@link PhpSerializeParser} had each defined independently in their own
 * {@code errorAt} helper, extracted once the second copy turned up. Not shared with
 * {@code exception.ParsingExceptionMessages}/{@code XmlOperation}'s own location handling — those
 * read a *structured* location Jackson/JAXP already computed for you, so there's nothing to share
 * with "count newlines yourself" logic.
 */
final class ParserLocations {

    private ParserLocations() {
    }

    /** @return a 1-based {@code " (line N, column M)"} suffix (with its own leading space) for
     *          {@code position} within {@code input}, counting every {@code '\n'} before it as a
     *          line break */
    static String locationSuffix(String input, int position) {
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
        return " (line " + line + ", column " + column + ")";
    }
}
