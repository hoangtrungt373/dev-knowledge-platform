package com.ttg.devknowledgeplatform.social.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * A text channel within a {@link Group}. Every group member can see every channel in this MVP —
 * there is no private/restricted channel concept yet. Channel names are unique within their group
 * ({@code UK_CHANNEL_GROUP_NAME}), enforced across all rows regardless of group via a composite
 * key.
 */
@Entity
@Table(
        name = "CHANNEL",
        schema = "product",
        uniqueConstraints = @UniqueConstraint(name = "UK_CHANNEL_GROUP_NAME", columnNames = {"GROUP_ID", "NAME"})
)
@AttributeOverride(name = "id", column = @Column(name = "CHANNEL_ID"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = "group")
@ToString(exclude = "group")
public class Channel extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "GROUP_ID", nullable = false)
    private Group group;

    @Column(name = "NAME", length = 100, nullable = false)
    private String name;
}
