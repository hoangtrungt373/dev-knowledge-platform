package com.ttg.devknowledgeplatform.social.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;
import com.ttg.devknowledgeplatform.common.entity.User;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * A directional block: {@link #blocker} does not want to see or be found by {@link #blocked}.
 * Unlike {@link Friendship}, this is never symmetric — the reverse direction has no implied row.
 */
@Entity
@Table(name = "USER_BLOCK", schema = "product")
@AttributeOverride(name = "id", column = @Column(name = "USER_BLOCK_ID"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"blocker", "blocked"})
@ToString(exclude = {"blocker", "blocked"})
public class UserBlock extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BLOCKER_ID", nullable = false)
    private User blocker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BLOCKED_ID", nullable = false)
    private User blocked;
}
