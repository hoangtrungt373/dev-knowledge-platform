package com.ttg.devknowledgeplatform.common.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "CONTENT_ITEM_TAG", schema = "product")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"contentItem", "tag"})
@ToString(exclude = {"contentItem", "tag"})
public class ContentItemTag {

    @EmbeddedId
    private ContentItemTagId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("contentItemId")
    @JoinColumn(name = "CONTENT_ITEM_ID")
    private ContentItem contentItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("tagId")
    @JoinColumn(name = "TAG_ID")
    private Tag tag;

    public ContentItemTag(ContentItem contentItem, Tag tag) {
        this.contentItem = contentItem;
        this.tag = tag;
        this.id = new ContentItemTagId(contentItem.getId(), tag.getId());
    }
}
