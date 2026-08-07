package com.ttg.devknowledgeplatform.task.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;
import com.ttg.devknowledgeplatform.task.enums.ProjectStatus;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * A named grouping of {@link Task}s owned by a single Keycloak identity. MVP is single-user —
 * there is no shared membership yet, only an {@link #ownerUuid}.
 *
 * <p>{@link #ownerUuid} is a plain column (the Keycloak JWT's {@code sub} claim), not a foreign
 * key to a local {@code User} row — this module never needs to display another user's profile,
 * only "is this project's owner the caller," which a direct string comparison against the
 * authenticated principal's UUID already answers with no join. See
 * {@code security.KeycloakJwtAuthenticationConverter}'s Javadoc for the "no persisted User copy"
 * reasoning this mirrors from {@code ecommerce-service}.
 */
@Entity
@Table(name = "PROJECT", schema = "task")
@AttributeOverride(name = "id", column = @Column(name = "PROJECT_ID"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Project extends AbstractEntity {

    @NotNull
    @Size(max = 255)
    @Column(name = "NAME", length = 255, nullable = false)
    private String name;

    @Column(name = "DESCRIPTION")
    private String description;

    @NotNull
    @Size(max = 36)
    @Column(name = "OWNER_UUID", length = 36, nullable = false)
    private String ownerUuid;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 50, nullable = false)
    private ProjectStatus status;
}
