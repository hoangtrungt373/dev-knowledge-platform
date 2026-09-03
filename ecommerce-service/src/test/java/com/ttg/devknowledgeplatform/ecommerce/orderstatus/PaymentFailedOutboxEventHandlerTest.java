package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.ecommerce.entity.OutboxEvent;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link PaymentFailedOutboxEventHandler} — same shape as
 * {@link PaymentSucceededOutboxEventHandlerTest}.
 */
class PaymentFailedOutboxEventHandlerTest {

    private final PaymentFailedOutboxEventHandler handler = new PaymentFailedOutboxEventHandler();

    @Test
    void eventTypeIsThePublishedConstant() {
        assertThat(handler.eventType()).isEqualTo(PaymentFailedOutboxEventHandler.EVENT_TYPE);
    }

    @Test
    void payloadRoundTripsThroughAMap() {
        var payload = new PaymentFailedOutboxEventHandler.Payload(
                42, new BigDecimal("25.00"), "INSUFFICIENT_FUNDS", "raw gateway message");

        OutboxEvent event = new OutboxEvent();
        event.setPayload(payload.toMap());
        var roundTripped = PaymentFailedOutboxEventHandler.Payload.from(event);

        assertThat(roundTripped).isEqualTo(payload);
    }

    @Test
    void handleDoesNotThrowForAWellFormedEvent() {
        var payload = new PaymentFailedOutboxEventHandler.Payload(
                42, new BigDecimal("25.00"), "INSUFFICIENT_FUNDS", "raw gateway message");
        OutboxEvent event = new OutboxEvent();
        event.setPayload(payload.toMap());

        assertThatCode(() -> handler.handle(event)).doesNotThrowAnyException();
    }
}
