package com.ttg.devknowledgeplatform.ecommerce.outbox;

import com.ttg.devknowledgeplatform.ecommerce.entity.OutboxEvent;

/**
 * Strategy interface for handling one {@code OutboxEvent}'s {@code eventType}. Implementations
 * are ordinary Spring beans, discovered automatically by {@link OutboxEventDispatcher} — adding a
 * new event type (a later epic's own) is just adding a new implementation, never editing the
 * relay or dispatcher.
 */
public interface OutboxEventHandler {

    /** The {@code eventType} this handler processes, e.g. {@code "PRODUCT_CHANGED"}. */
    String eventType();

    /**
     * Processes the event. Runs inside {@link OutboxEventProcessor}'s transaction — throwing
     * leaves the event's DB changes rolled back and lets the relay record the failure.
     *
     * @param event the claimed event (already transitioned to {@code PROCESSING})
     */
    void handle(OutboxEvent event);
}
