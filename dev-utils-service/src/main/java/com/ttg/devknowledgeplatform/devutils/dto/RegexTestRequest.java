package com.ttg.devknowledgeplatform.devutils.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for RegExp Tester — the first operation whose input is genuinely 3 separate,
 * differently-shaped fields rather than "text in, a minify flag" (see
 * {@code service.DevUtilOperation}'s own Javadoc for why this gets its own dedicated type instead
 * of being bent into {@link MinifiableTextRequest}/{@link TextRequest}, the same reasoning that
 * Javadoc already anticipates for a future Unix Time Converter/Number Base Converter).
 *
 * @param pattern  the regular expression itself, capped at {@link DevUtilsLimits#MAX_INPUT_LENGTH}
 *                 like every other text field in this module, even though a pattern this long
 *                 would be impractical in practice — the cap exists as a resource-exhaustion
 *                 guard on a fully public endpoint, not a realistic usage ceiling.
 * @param flags    JS-style regex flags (e.g. {@code "gi"}) — {@code g} (global — collect every
 *                 match instead of just the first), {@code i} (case-insensitive), {@code m}
 *                 (multiline, {@code ^}/{@code $} match at line boundaries), {@code s} (dot
 *                 matches newline too). Optional (blank/omitted means no flags at all); an
 *                 unrecognized character is ignored rather than rejected — see
 *                 {@code RegexTesterOperation}'s own Javadoc.
 * @param testText the text to test the pattern against, capped the same way {@code pattern} is.
 */
public record RegexTestRequest(
        @NotBlank @Size(max = DevUtilsLimits.MAX_INPUT_LENGTH) String pattern,
        @Size(max = 16) String flags,
        @NotBlank @Size(max = DevUtilsLimits.MAX_INPUT_LENGTH) String testText) {
}
