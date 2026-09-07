package com.ttg.devknowledgeplatform.devutils.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for an operation with no minify concept — today, only {@code JsonToYamlOperation}
 * (output is always block-style YAML; see that class's own Javadoc for why there's no compact
 * form to toggle). Kept separate from {@link MinifiableTextRequest} deliberately, rather than
 * reusing that record with an ignored {@code minify} field — see
 * {@code service.DevUtilOperation}'s own Javadoc for why this module stopped forcing every
 * operation through one shared request shape.
 */
public record TextRequest(@NotBlank String input) {
}
