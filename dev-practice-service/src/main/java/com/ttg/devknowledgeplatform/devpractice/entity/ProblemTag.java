package com.ttg.devknowledgeplatform.devpractice.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * A topic a problem can be tagged with (Array, Math, Stack, ...). Flat — just {@code name}/
 * {@code slug}, no hierarchy or lifecycle — mirroring {@code ecommerce-service}'s {@code ProductTag}.
 * A problem may have any number of tags and a tag any number of problems, via
 * {@link ProblemTagAssignment}.
 *
 * <p>No back-reference collection to its assignments: nothing navigates tag → problems in Java (the
 * in-use check and the tag filter both go through queries), so it would only be a lazy collection
 * waiting to be loaded by accident.
 */
@Entity
@Table(name = "PROBLEM_TAG", schema = "dev_practice")
@AttributeOverride(name = "id", column = @Column(name = "PROBLEM_TAG_ID"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString
public class ProblemTag extends AbstractEntity {

    // Case-insensitive uniqueness is a functional index on LOWER(NAME) (see DKP-0055), not
    // expressible as `unique = true` here, which would mean a case-sensitive constraint.
    @NotNull
    @Size(max = 100)
    @Column(name = "NAME", length = 100, nullable = false)
    private String name;

    @NotNull
    @Size(max = 100)
    @Column(name = "SLUG", length = 100, nullable = false, unique = true)
    private String slug;
}
