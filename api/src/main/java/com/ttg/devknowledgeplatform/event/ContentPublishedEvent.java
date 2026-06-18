package com.ttg.devknowledgeplatform.event;

import com.ttg.devknowledgeplatform.common.entity.ContentItem;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Fired when a content item transitions to PUBLISHED status.
 * Triggers automatic ingestion into the RAG vector store.
 */
@Getter
public class ContentPublishedEvent extends ApplicationEvent {

    private final ContentItem contentItem;

    public ContentPublishedEvent(Object source, ContentItem contentItem) {
        super(source);
        this.contentItem = contentItem;
    }
}
