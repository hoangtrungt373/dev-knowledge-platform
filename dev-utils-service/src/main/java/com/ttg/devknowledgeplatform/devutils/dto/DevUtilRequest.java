package com.ttg.devknowledgeplatform.devutils.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for every dev-utils operation — a single raw text input to format, validate, or
 * convert. Deliberately the same shape across every operation (input string in, output string
 * out), so one record serves all four endpoints instead of a near-duplicate per operation.
 */
public record DevUtilRequest(@NotBlank String input) {
}
