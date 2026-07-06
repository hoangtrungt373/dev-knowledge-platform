package com.ttg.devknowledgeplatform.content.service;

import java.util.Set;

import com.ttg.devknowledgeplatform.content.enums.ContentStatus;
import com.ttg.devknowledgeplatform.content.enums.ContentType;

/**
 * Plain input records for {@link ArticleService}, mirroring {@code api}'s
 * {@code CreateArticleRequest}/{@code UpdateArticleRequest} field-for-field but without any
 * REST/validation concerns — those stay in {@code api}, which does the request-DTO-to-command
 * translation before calling the service.
 */
public final class ArticleCommands {

    private ArticleCommands() {}

    public record Create(
            String title,
            ContentType type,
            String body,
            ContentStatus status,
            Integer categoryId,
            Set<Integer> tagIds) {
    }

    /** {@code tagIds}: {@code null} leaves tags unchanged; empty clears them; otherwise replaces them. */
    public record Update(
            String title,
            ContentType type,
            String body,
            ContentStatus status,
            Integer categoryId,
            Set<Integer> tagIds) {
    }
}
