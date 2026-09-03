package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.ecommerce.entity.OutboxEvent;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link PaymentRefundedOutboxEventHandler} — same shape as
 * {@link PaymentSucceededOutboxEventHandlerTest}. Nothing publishes this event yet (Epic 4 Phase 6
 * is what will) — this test only pins down the handler/payload mechanics built now.
 */
class PaymentRefundedOutboxEventHandlerTest {

    private final PaymentRefundedOutboxEventHandler handler = new PaymentRefundedOutboxEventHandler();

    @Test
    void eventTypeIsThePublishedConstant() {
        assertThat(handler.eventType()).isEqualTo(PaymentRefundedOutboxEventHandler.EVENT_TYPE);
    }

    @Test
    void payloadRoundTripsThroughAMap() {
        var payload = new PaymentRefundedOutboxEventHandler.Payload(42, new BigDecimal("25.00"), "gw-ref-1");

        OutboxEvent event = new OutboxEvent();
        event.setPayload(payload.toMap());
        var roundTripped = PaymentRefundedOutboxEventHandler.Payload.from(event);

        assertThat(roundTripped).isEqualTo(payload);
    }

    @Test
    void handleDoesNotThrowForAWellFormedEvent() {
        var payload = new PaymentRefundedOutboxEventHandler.Payload(42, new BigDecimal("25.00"), "gw-ref-1");
        OutboxEvent event = new OutboxEvent();
        event.setPayload(payload.toMap());

        assertThatCode(() -> handler.handle(event)).doesNotThrowAnyException();
    }
}
