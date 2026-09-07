package com.ttg.devknowledgeplatform.devutils.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for an operation whose output can be either pretty-printed or compact/single-line —
 * today: JSON format/validate, YAML→JSON, HTML beautify. Shared across exactly those three because
 * they genuinely share this shape (raw text in, a minify flag, transformed text out); an operation
 * whose input/output doesn't fit this shape (e.g. JSON→YAML, which has no minify concept at all —
 * see {@code JsonToYamlOperation}'s own Javadoc — or a future Unix Time Converter/Number Base
 * Converter, neither of which is a single string) gets its own dedicated request type instead of
 * being forced through this one. See {@code service.DevUtilOperation}'s own Javadoc for the full
 * reasoning behind not sharing one request/response pair across every operation.
 *
 * @param minify {@code true} for compact/single-line output, {@code false} (the default, when
 *               omitted) for pretty-printed.
 */
public record MinifiableTextRequest(@NotBlank String input, boolean minify) {
}
