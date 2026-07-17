package com.ttg.devknowledgeplatform.task.service;

/**
 * Plain input records for {@link ProjectService}, without any REST/validation concerns — those
 * belong to the REST layer, which translates a request DTO into one of these before calling the
 * service. Mirrors {@code content-service}'s {@code ArticleCommands}.
 */
public final class ProjectCommands {

    private ProjectCommands() {}

    public record Create(String name, String description) {
    }

    public record Update(String name, String description) {
    }
}
