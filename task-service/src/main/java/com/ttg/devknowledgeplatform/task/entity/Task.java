package com.ttg.devknowledgeplatform.task.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;
import com.ttg.devknowledgeplatform.task.enums.TaskPriority;
import com.ttg.devknowledgeplatform.task.enums.TaskStatus;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.hibernate.annotations.BatchSize;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A unit of work owned by a single Keycloak identity, optionally grouped under a {@link Project}.
 * {@link #project} is nullable — standalone tasks (no project) are allowed by design, for quick
 * capture. {@link #parentTask} is nullable and capped at one level deep — a subtask cannot itself
 * have subtasks (see {@code TaskServiceImpl.validateParentAssignment}); mirrors {@code
 * content-service}'s {@code Category} self-referential parent/child shape, just depth-limited
 * instead of an arbitrary tree.
 *
 * <p>{@link #ownerUuid} is a plain column (the Keycloak JWT's {@code sub} claim), not a foreign
 * key to a local {@code User} row — see {@link Project}'s Javadoc for why.
 */
@Entity
@Table(name = "TASK", schema = "task")
@AttributeOverride(name = "id", column = @Column(name = "TASK_ID"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"project", "parentTask", "subtasks"})
@ToString(exclude = {"project", "parentTask", "subtasks"})
public class Task extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PROJECT_ID", nullable = true)
    private Project project;

    @NotNull
    @Size(max = 36)
    @Column(name = "OWNER_UUID", length = 36, nullable = false)
    private String ownerUuid;

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
    @JoinColumn(name = "PARENT_TASK_ID")
    private Task parentTask;

    @OrderBy("id ASC")
    @BatchSize(size = 32)
    @OneToMany(mappedBy = "parentTask", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Task> subtasks = new ArrayList<>();
}
