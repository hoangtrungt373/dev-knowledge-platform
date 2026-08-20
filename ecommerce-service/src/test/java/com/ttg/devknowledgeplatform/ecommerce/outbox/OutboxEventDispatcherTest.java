package com.ttg.devknowledgeplatform.ecommerce.outbox;

import com.ttg.devknowledgeplatform.ecommerce.entity.OutboxEvent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OutboxEventDispatcher} — the registry backing US-1.5's async
 * read-model sync (any future epic's handler just needs to exist as a bean; this class verifies
 * the lookup-by-{@code eventType} mechanism itself, independent of any one handler's logic).
 */
class OutboxEventDispatcherTest {

    private static OutboxEventHandler handlerFor(String eventType) {
        return new OutboxEventHandler() {
            @Override
            public String eventType() {
                return eventType;
            }

            @Override
            public void handle(OutboxEvent event) {
                // no-op — dispatch resolution is what's under test, not handler behavior
            }
        };
    }

    @Test
    void resolvesTheHandlerRegisteredForAnEventType() {
        OutboxEventHandler productChangedHandler = handlerFor("PRODUCT_CHANGED");
        OutboxEventDispatcher dispatcher = new OutboxEventDispatcher(List.of(productChangedHandler));

        Optional<OutboxEventHandler> resolved = dispatcher.resolve("PRODUCT_CHANGED");

        assertThat(resolved).contains(productChangedHandler);
    }

    @Test
    void returnsEmptyForAnUnregisteredEventType() {
        OutboxEventDispatcher dispatcher = new OutboxEventDispatcher(List.of(handlerFor("PRODUCT_CHANGED")));

        assertThat(dispatcher.resolve("ORDER_CREATED")).isEmpty();
    }

    @Test
    void distinguishesBetweenMultipleRegisteredHandlers() {
        OutboxEventHandler productChanged = handlerFor("PRODUCT_CHANGED");
        OutboxEventHandler orderCreated = handlerFor("ORDER_CREATED");
        OutboxEventDispatcher dispatcher = new OutboxEventDispatcher(List.of(productChanged, orderCreated));

        assertThat(dispatcher.resolve("PRODUCT_CHANGED")).contains(productChanged);
        assertThat(dispatcher.resolve("ORDER_CREATED")).contains(orderCreated);
    }
}
