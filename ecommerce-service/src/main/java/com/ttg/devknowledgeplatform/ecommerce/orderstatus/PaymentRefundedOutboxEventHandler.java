package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.ecommerce.entity.OutboxEvent;
import com.ttg.devknowledgeplatform.ecommerce.outbox.OutboxEventHandler;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code PAYMENT_REFUNDED} handler (US-4.4/US-4.6) — see
 * {@link PaymentSucceededOutboxEventHandler}'s own Javadoc for why this is a deliberate, documented
 * placeholder (a structured audit log line, no real downstream consumer yet) rather than dead
 * weight.
 *
 * <p><b>Nothing publishes this event yet</b> — Epic 4 Phase 6 (US-4.6, refund on cancellation)
 * is what will call {@code payment.PaymentGatewayPort#refund} and publish this event alongside
 * turning the {@code Payment} row's status to {@code REFUNDED}; this handler (and the event type
 * constant/payload shape it defines) is built now, in the same pass as its two siblings, purely so
 * all three of US-4.4's named events exist together rather than being added piecemeal.
 */
@Component
@Slf4j
public class PaymentRefundedOutboxEventHandler implements OutboxEventHandler {

    public static final String EVENT_TYPE = "PAYMENT_REFUNDED";

    /** Typed payload for {@code PAYMENT_REFUNDED} events. */
    public record Payload(Integer orderId, BigDecimal amount, String gatewayReference) {

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("orderId", orderId);
            map.put("amount", amount.toPlainString());
            map.put("gatewayReference", gatewayReference);
            return map;
        }

        public static Payload from(OutboxEvent event) {
            Map<String, Object> payload = event.getPayload();
            Number orderId = (Number) payload.get("orderId");
            return new Payload(orderId.intValue(),
                    new BigDecimal((String) payload.get("amount")),
                    (String) payload.get("gatewayReference"));
        }
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public void handle(OutboxEvent event) {
        Payload payload = Payload.from(event);
        log.info("Payment refunded for order id={} amount={} gatewayReference={}",
                payload.orderId(), payload.amount(), payload.gatewayReference());
    }
}
