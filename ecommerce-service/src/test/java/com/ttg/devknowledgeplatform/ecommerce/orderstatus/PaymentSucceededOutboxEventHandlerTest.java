package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.ecommerce.entity.OutboxEvent;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link PaymentSucceededOutboxEventHandler} — the {@code Payload}'s own
 * map round-trip (what a producer/consumer must agree on) and that {@code handle} doesn't throw
 * for a well-formed event (its only real job today is a log line — see the class's own Javadoc).
 */
class PaymentSucceededOutboxEventHandlerTest {

    private final PaymentSucceededOutboxEventHandler handler = new PaymentSucceededOutboxEventHandler();

    @Test
    void eventTypeIsThePublishedConstant() {
        assertThat(handler.eventType()).isEqualTo(PaymentSucceededOutboxEventHandler.EVENT_TYPE);
    }

    @Test
    void payloadRoundTripsThroughAMap() {
        var payload = new PaymentSucceededOutboxEventHandler.Payload(42, new BigDecimal("25.00"), "gw-ref-1");

        OutboxEvent event = new OutboxEvent();
        event.setPayload(payload.toMap());
        var roundTripped = PaymentSucceededOutboxEventHandler.Payload.from(event);

        assertThat(roundTripped).isEqualTo(payload);
    }

    @Test
    void handleDoesNotThrowForAWellFormedEvent() {
        var payload = new PaymentSucceededOutboxEventHandler.Payload(42, new BigDecimal("25.00"), "gw-ref-1");
        OutboxEvent event = new OutboxEvent();
        event.setPayload(payload.toMap());

        assertThatCode(() -> handler.handle(event)).doesNotThrowAnyException();
    }
}
