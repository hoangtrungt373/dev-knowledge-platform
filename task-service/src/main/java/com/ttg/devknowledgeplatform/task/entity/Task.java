package com.ttg.devknowledgeplatform.task.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;
import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.content.entity.ContentItem;
import com.ttg.devknowledgeplatform.task.enums.TaskPriority;
import com.ttg.devknowledgeplatform.task.enums.TaskStatus;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;

/**
 * A unit of work owned by a single {@link User}, optionally grouped under a {@link Project} and
 * optionally linked to a {@code content-service} {@link ContentItem} it tracks work against
 * (e.g. "write article X"). {@link #project} is nullable — standalone tasks (no project) are
 * allowed by design, for quick capture.
 */
@Entity
@Table(name = "TASK", schema = "product")
@AttributeOverride(name = "id", column = @Column(name = "TASK_ID"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"project", "owner", "contentItem"})
@ToString(exclude = {"project", "owner", "contentItem"})
public class Task extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PROJECT_ID", nullable = true)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "OWNER_ID", nullable = false)
    private User owner;

    @NotNull
    @Size(max = 255)
    @Column(name = "TITLE", length = 255, nullable = false)
    private String title;

    @Column(name = "DESCRIPTION")
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 50, nullable = false)
    @Builder.Default
    private TaskStatus status = TaskStatus.TODO;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "PRIORITY", length = 50, nullable = false)
    @Builder.Default
    private TaskPriority priority = TaskPriority.MEDIUM;

    @Column(name = "DUE_DATE")
    private Instant dueDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CONTENT_ITEM_ID", nullable = true)
    private ContentItem contentItem;
}
