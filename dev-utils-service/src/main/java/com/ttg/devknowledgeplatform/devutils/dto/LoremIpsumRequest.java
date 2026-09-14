package com.ttg.devknowledgeplatform.devutils.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Request for {@code LoremIpsumGeneratorOperation} — this operation takes no text input at all
 * (it *generates* content rather than transforming an existing one), so it gets its own
 * single-field request instead of reusing {@link TextRequest}/{@link MinifiableTextRequest},
 * neither of which has a paragraph-count concept. Bounded 1-20 inclusive, per direct request —
 * generous enough for any real placeholder-text need, finite enough that a single call can't be
 * used to generate an unbounded response body on this fully public, unauthenticated endpoint (the
 * same resource-exhaustion reasoning {@link DevUtilsLimits}'s own Javadoc applies to text length
 * instead of a paragraph count). A plain {@code int}, not {@code Integer} — there's no legitimate
 * "count not supplied" case worth distinguishing from an out-of-range one the way
 * {@code DateTimeToTimestampRequest#zoneId}'s blank-means-UTC case is; a missing field just
 * deserializes to {@code 0}, which {@code @Min(1)} already rejects with the same 400 a genuinely
 * out-of-range value gets.
 *
 * @param paragraphs how many paragraphs to generate, 1-20 inclusive.
 */
public record LoremIpsumRequest(@Min(1) @Max(20) int paragraphs) {
}
