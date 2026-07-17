package com.ttg.devknowledgeplatform.task.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;
import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.task.enums.ProjectStatus;

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

/**
 * A named grouping of {@link Task}s owned by a single {@link User}. MVP is single-user — there
 * is no shared membership yet, only an {@link #owner}.
 */
@Entity
@Table(name = "PROJECT", schema = "product")
@AttributeOverride(name = "id", column = @Column(name = "PROJECT_ID"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = "owner")
@ToString(exclude = "owner")
public class Project extends AbstractEntity {

    @NotNull
    @Size(max = 255)
    @Column(name = "NAME", length = 255, nullable = false)
    private String name;

    @Column(name = "DESCRIPTION")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "OWNER_ID", nullable = false)
    private User owner;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 50, nullable = false)
    private ProjectStatus status;
}
