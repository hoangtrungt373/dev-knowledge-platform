package com.ttg.devknowledgeplatform.devpractice.dto;

import java.time.Instant;

/** A problem tag as the admin tag-management screens and the public tag list see it. */
public record ProblemTagResponse(Integer id, String name, String slug, Instant createdAt) {
}
