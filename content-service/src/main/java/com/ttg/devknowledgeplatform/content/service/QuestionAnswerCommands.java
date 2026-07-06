package com.ttg.devknowledgeplatform.content.service;

import java.util.Set;

import com.ttg.devknowledgeplatform.content.enums.ContentStatus;
import com.ttg.devknowledgeplatform.content.enums.QuestionDifficulty;

/**
 * Plain input records for {@link QuestionAnswerService}, mirroring {@code api}'s
 * {@code CreateQuestionAnswerRequest}/{@code UpdateQuestionAnswerRequest} field-for-field but
 * without any REST/validation concerns — those stay in {@code api}, which does the
 * request-DTO-to-command translation before calling the service.
 */
public final class QuestionAnswerCommands {

    private QuestionAnswerCommands() {}

    public record Create(
            String title,
            QuestionDifficulty difficulty,
            String questionBody,
            String shortAnswer,
            String detailedAnswer,
            Boolean isCommon,
            ContentStatus status,
            Integer categoryId,
            Set<Integer> tagIds) {
    }

    /** {@code tagIds}: {@code null} leaves tags unchanged; empty clears them; otherwise replaces them. */
    public record Update(
            String title,
            QuestionDifficulty difficulty,
            String questionBody,
            String shortAnswer,
            String detailedAnswer,
            Boolean isCommon,
            ContentStatus status,
            Integer categoryId,
            Set<Integer> tagIds) {
    }
}
