package com.ttg.devknowledgeplatform.event;

import com.ttg.devknowledgeplatform.infra.event.AsyncEventHandler;
import com.ttg.devknowledgeplatform.infra.event.EventHandler;
import com.ttg.devknowledgeplatform.service.ContentIndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Indexes a content item into the RAG vector store when it transitions to PUBLISHED status.
 *
 * <p>Async dispatch and exception safety are provided by {@link AsyncEventHandler}. This
 * class only contains the one-line {@code @EventHandler} listener shim ({@link #onEvent}) it
 * requires plus the indexing call. No {@code try/catch} is needed here.
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
     * Spring listener entry point — see {@link AsyncEventHandler} class Javadoc for why this
     * concretely-typed method must live here rather than on the generic base class. Does nothing
     * but delegate; all actual logic is in {@link #doHandle}.
     */
    @EventHandler
    public void onEvent(ContentPublishedEvent event) {
        handle(event);
    }

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
