package com.ttg.devknowledgeplatform.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContentItemTagId implements Serializable {

    @Column(name = "CONTENT_ITEM_ID", nullable = false)
    private Integer contentItemId;

    @Column(name = "TAG_ID", nullable = false)
    private Integer tagId;
}
