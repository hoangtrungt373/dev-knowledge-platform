package com.ttg.devknowledgeplatform.common.entity;

import com.ttg.devknowledgeplatform.common.enums.ParamKey;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Persistent key-value store for system-managed parameters.
 *
 * <p>Each row holds one named parameter identified by a {@link ParamKey} constant.
 * Two serialization formats are used in {@link #value} depending on the key:
 * <ul>
 *   <li><strong>Vector parameters</strong> (CENTROID_* keys) — pgvector text notation:
 *       {@code [f1,f2,...,f1536]}</li>
 *   <li><strong>Numeric parameters</strong> (threshold keys) — plain decimal string:
 *       {@code "0.45"}</li>
 * </ul>
 *
 * <p>Rows are always written by the application scheduler, never by user actions.
 * The inherited audit fields ({@code usrCreation}, {@code usrLastModification}) will
 * therefore always contain {@code "system"} — the fallback value from
 * {@link com.ttg.devknowledgeplatform.common.util.UserUtils#getUserName()} when no
 * security context is present. {@link #computedAt} is the meaningful timestamp here,
 * recording when the underlying computation (e.g. the SQL {@code avg()} over embeddings)
 * actually ran.
 */
@Entity
@Table(name = "SYS_PARAM", schema = "product")
@AttributeOverride(name = "id", column = @Column(name = "SYS_PARAM_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SysParam extends AbstractEntity {

    /** The parameter identifier; maps to the {@code NAME} column and the {@link ParamKey} enum. */
    @Enumerated(EnumType.STRING)
    @Column(name = "NAME", nullable = false, unique = true, length = 100)
    private ParamKey name;

    /**
     * Serialized parameter value.
     * Vectors are stored as pgvector text notation {@code [f1,f2,...]};
     * numeric scalars are stored as plain decimal strings.
     */
    @Column(name = "VALUE", columnDefinition = "TEXT", nullable = false)
    private String value;

    /** Timestamp of the most recent computation that produced the current {@link #value}. */
    @Column(name = "COMPUTED_AT", nullable = false)
    private Instant computedAt;
}
