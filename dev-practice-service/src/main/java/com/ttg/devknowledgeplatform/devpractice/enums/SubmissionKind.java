package com.ttg.devknowledgeplatform.devpractice.enums;

/**
 * Why a submission exists. Both kinds go through the same judging pipeline; they differ in who can
 * make them and where they show up.
 *
 * <ul>
 *   <li>{@link #USER} — a user solving a published problem. Only these appear in a user's own
 *       submission history and count as "this problem has been attempted" (the problem-delete
 *       guard).</li>
 *   <li>{@link #REFERENCE} — an admin proving the problem is solvable. Allowed on a draft (the whole
 *       point is to verify before publishing), never listed to users, and the only kind that can
 *       verify a problem: publishing requires an {@code ACCEPTED} reference judged at the problem's
 *       current {@code contractVersion}.</li>
 * </ul>
 */
public enum SubmissionKind {
    USER,
    REFERENCE
}
