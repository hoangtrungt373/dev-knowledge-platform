package com.ttg.devknowledgeplatform.event;

import com.ttg.devknowledgeplatform.infra.event.AsyncEventHandler;
import com.ttg.devknowledgeplatform.service.ContentIndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Indexes a content item into the RAG vector store when it transitions to PUBLISHED status.
 *
 * <p>Async dispatch and exception safety are provided by {@link AsyncEventHandler}. This
 * class only contains the indexing call. No {@code @Async}, {@code @EventListener}, or
 * {@code try/catch} is needed here.
 *
 * <p>No {@code @Transactional} is declared because {@link ContentIndexingService#index}
 * manages its own transaction boundary; this handler is purely a coordinator.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContentPublishedEventListener extends AsyncEventHandler<ContentPublishedEvent> {

    private final ContentIndexingService contentIndexingService;

    /**
     * Triggers RAG indexing for the published content item.
     *
     * @param event carries the content item that was just published
     */
    @Override
    protected void doHandle(ContentPublishedEvent event) throws Exception {
        Integer contentItemId = event.getContentItem().getId();
        log.info("ContentPublishedEvent received for contentItemId={} — starting indexing", contentItemId);
        contentIndexingService.index(contentItemId);
    }
}
