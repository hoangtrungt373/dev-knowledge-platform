package com.ttg.devknowledgeplatform.content.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "ARTICLE", schema = "product")
@AttributeOverride(name = "id", column = @Column(name = "ARTICLE_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"contentItem"})
@ToString(exclude = {"contentItem"})
public class Article extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CONTENT_ITEM_ID", nullable = false, unique = true)
    private ContentItem contentItem;

    @Column(name = "BODY", columnDefinition = "TEXT")
    private String body;
}