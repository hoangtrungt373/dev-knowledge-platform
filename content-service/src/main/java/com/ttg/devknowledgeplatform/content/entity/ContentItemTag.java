package com.ttg.devknowledgeplatform.content.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(
        name = "CONTENT_ITEM_TAG",
        schema = "product",
        uniqueConstraints = @UniqueConstraint(name = "UK_CONTENT_ITEM_TAG_PAIR", columnNames = {"CONTENT_ITEM_ID", "TAG_ID"})
)
@AttributeOverride(name = "id", column = @Column(name = "CONTENT_ITEM_TAG_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"contentItem", "tag"})
public class ContentItemTag extends AbstractEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CONTENT_ITEM_ID", nullable = false)
    private ContentItem contentItem;

    @NotNull
    @BatchSize(size = 32)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TAG_ID", nullable = false)
    private Tag tag;
}
