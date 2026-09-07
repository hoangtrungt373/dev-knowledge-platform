package com.ttg.devknowledgeplatform.devutils.dto;

/**
 * Shared input-size limit for every dev-utils request DTO. A first-version, deliberately generous
 * but finite bound — this is the one fully public, unauthenticated endpoint in the reactor (see
 * root {@code CLAUDE.md}'s Security section), so an unbounded {@code input} would be a real
 * resource-exhaustion vector (a large body fully buffered/parsed before any other check runs).
 * One shared constant, not a per-operation limit, since nothing about JSON vs. YAML vs. HTML makes
 * a different bound obviously correct yet — split it per operation later if a real use case needs
 * a different number for one of them.
 */
public final class DevUtilsLimits {

    /** Max characters accepted for {@code input}, across every operation. */
    public static final int MAX_INPUT_LENGTH = 100_000;

    private DevUtilsLimits() {
    }
}
