package com.ttg.devknowledgeplatform.devutils.dto;

/**
 * Response body for the Hash Generator — genuinely richer than the single-string
 * {@link DevUtilResponse} every other operation shares, so it gets its own type, the same
 * scenario {@link StringCaseResponse} already establishes for a different operation.
 *
 * @param sha1   lowercase hex SHA-1 digest (40 characters)
 * @param sha256 lowercase hex SHA-256 digest (64 characters)
 * @param sha384 lowercase hex SHA-384 digest (96 characters)
 * @param sha512 lowercase hex SHA-512 digest (128 characters)
 */
public record HashResponse(String sha1, String sha256, String sha384, String sha512) {
}
