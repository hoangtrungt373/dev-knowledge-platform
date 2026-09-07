package com.ttg.devknowledgeplatform.devutils.dto;

/**
 * Response body shared by every dev-utils operation today — each one's output is a single
 * transformed text string. Not a rule this module forces going forward: a future operation whose
 * output is genuinely richer than one string (e.g. a Number Base Converter returning several
 * representations at once) should get its own response type rather than being packed into this
 * one — see {@code service.DevUtilOperation}'s own Javadoc.
 */
public record DevUtilResponse(String output) {
}
