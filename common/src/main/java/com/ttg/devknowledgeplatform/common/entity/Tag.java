package com.ttg.devknowledgeplatform.common.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "TAG", schema = "product")
@AttributeOverride(name = "id", column = @Column(name = "TAG_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Tag extends AbstractEntity {

    @Column(name = "NAME", length = 100, nullable = false, unique = true)
    private String name;

    @Column(name = "SLUG", length = 100, nullable = false, unique = true)
    private String slug;
}
