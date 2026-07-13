package com.ttg.devknowledgeplatform.social.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * A multi-user chat group containing one or more {@link Channel}s. Membership and role
 * (owner/admin/member) are tracked by {@link GroupMember}, not on this entity — the owner is
 * whichever member row holds {@code role = OWNER}, so there is exactly one source of truth for it
 * rather than a duplicated {@code ownerId} that could drift out of sync.
 *
 * <p>Maps to table {@code MESSAGE_GROUP} rather than {@code GROUP} — {@code GROUP} is a reserved
 * word in PostgreSQL (used by {@code GROUP BY}) and would need quoting in every raw query.
 */
@Entity
@Table(name = "MESSAGE_GROUP", schema = "product")
@AttributeOverride(name = "id", column = @Column(name = "GROUP_ID"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString
public class Group extends AbstractEntity {

    @Column(name = "NAME", length = 255, nullable = false)
    private String name;
}
