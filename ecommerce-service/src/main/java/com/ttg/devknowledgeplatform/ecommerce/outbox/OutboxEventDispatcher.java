package com.ttg.devknowledgeplatform.ecommerce.outbox;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry of every {@link OutboxEventHandler} in the context, keyed by {@link OutboxEventHandler#eventType()}.
 *
 * <p>Built once from whatever handler beans Spring finds — a new handler for a new event type
 * (a later epic's own) just needs to exist as a {@code @Component}; nothing here changes.
 */
@Slf4j
@Component
public class OutboxEventDispatcher {

    private final Map<String, OutboxEventHandler> handlersByEventType;

    public OutboxEventDispatcher(List<OutboxEventHandler> handlers) {
        this.handlersByEventType = handlers.stream()
                .collect(Collectors.toMap(OutboxEventHandler::eventType, Function.identity()));
        log.info("Registered outbox event handlers for: {}", handlersByEventType.keySet());
    }

    /**
     * Resolves the handler for {@code eventType}.
     *
     * @param eventType the event's {@code eventType} column value
     * @return the matching handler, or empty if none is registered
     */
    public Optional<OutboxEventHandler> resolve(String eventType) {
        return Optional.ofNullable(handlersByEventType.get(eventType));
    }
}
