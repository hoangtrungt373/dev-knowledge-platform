package com.ttg.devknowledgeplatform.devutils.dto;

/**
 * Response body shared by every dev-utils operation whose output is a single transformed text
 * string — every operation today except {@code StringCaseOperation}, whose 7 named case variants
 * genuinely don't fit one string and get their own {@link StringCaseResponse} instead. Not a rule
 * this module forces going forward: a future operation whose output is likewise richer than one
 * string (e.g. a Number Base Converter returning several representations at once) should get its
 * own response type too, rather than being packed into this one — see
 * {@code service.DevUtilOperation}'s own Javadoc.
 */
public record DevUtilResponse(String output) {
}
