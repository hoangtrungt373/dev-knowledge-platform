package com.ttg.devknowledgeplatform.devutils.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body for Text Diff Checker — the second operation (after {@link RegexTestRequest})
 * whose input is genuinely 2 separate fields rather than "text in, a minify flag."
 *
 * <p>Deliberately **not** {@code @NotBlank} on either field, unlike almost every other request
 * DTO in this module — comparing against a blank/absent value is a legitimate, common diff (e.g.
 * "what changed when this file was first created," where {@code original} is blank and every
 * line of {@code updated} shows as added).
 *
 * @param original the "before" text, capped at {@link DevUtilsLimits#MAX_INPUT_LENGTH} — see that
 *                 class's own Javadoc for why.
 * @param updated  the "after" text, capped the same way.
 */
public record TextDiffRequest(
        @Size(max = DevUtilsLimits.MAX_INPUT_LENGTH) String original,
        @Size(max = DevUtilsLimits.MAX_INPUT_LENGTH) String updated) {
}
