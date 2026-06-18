package com.ttg.devknowledgeplatform.event;

import com.ttg.devknowledgeplatform.service.ContentIndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContentPublishedEventListener {

    private final ContentIndexingService contentIndexingService;

    /**
     * Runs asynchronously so publishing an article does not block the HTTP response
     * while waiting for the OpenAI embedding API call.
     */
    @Async
    @EventListener
    public void onContentPublished(ContentPublishedEvent event) {
        Integer contentItemId = event.getContentItem().getId();
        log.info("ContentPublishedEvent received for content item id={} — starting indexing", contentItemId);
        try {
            contentIndexingService.index(contentItemId);
        } catch (Exception e) {
            log.error("Failed to index content item id={}: {}", contentItemId, e.getMessage(), e);
        }
    }
}
