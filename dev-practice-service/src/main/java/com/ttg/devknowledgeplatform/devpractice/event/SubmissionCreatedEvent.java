package com.ttg.devknowledgeplatform.devpractice.event;

/**
 * Published by {@code SubmissionServiceImpl.create} right after a new {@code PENDING}
 * {@code Submission} is persisted. Carries only the id (not the entity itself) — a record is
 * published/handled across a transaction and thread boundary (see
 * {@code SubmissionJudgeEventListener}'s Javadoc), so the listener must re-load fresh state rather
 * than reuse anything from the publishing thread's own (by-then-closed) persistence context.
 *
 * @param submissionId primary key of the newly created {@code Submission}
 */
public record SubmissionCreatedEvent(Integer submissionId) {
}
