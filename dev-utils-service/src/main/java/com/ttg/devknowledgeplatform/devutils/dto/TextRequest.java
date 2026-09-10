package com.ttg.devknowledgeplatform.devutils.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for an operation with no minify concept at all — today: {@code JsonToYamlOperation}
 * (output is always block-style YAML), {@code JsonToCsvOperation} (CSV has no distinct "compact"
 * form either), {@code StringCaseOperation}/{@code HashGeneratorOperation} (no "compact form" of
 * a case conversion or a hash digest), every {@code Encoders/Decoders}-group encode/decode pair
 * (Base64, URL, HTML entity, PHP serialize/unserialize, ASCII↔Hex — an encoded/decoded form has no
 * distinct compact representation to toggle either), and {@code CronParserOperation} (a plain-
 * English sentence has no "compact form"). Kept separate from {@link MinifiableTextRequest}
 * deliberately, rather than reusing that record with an ignored {@code minify} field — see
 * {@code service.DevUtilOperation}'s own Javadoc for why this module stopped forcing every
 * operation through one shared request shape.
 *
 * @param input raw text to transform, capped at {@link DevUtilsLimits#MAX_INPUT_LENGTH} — see
 *              that class's own Javadoc for why.
 */
public record TextRequest(@NotBlank @Size(max = DevUtilsLimits.MAX_INPUT_LENGTH) String input) {
}
