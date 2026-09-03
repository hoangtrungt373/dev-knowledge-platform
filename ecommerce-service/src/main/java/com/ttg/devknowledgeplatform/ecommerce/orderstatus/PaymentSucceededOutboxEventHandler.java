package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.ecommerce.entity.OutboxEvent;
import com.ttg.devknowledgeplatform.ecommerce.outbox.OutboxEventHandler;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code PAYMENT_SUCCEEDED} handler (US-4.4) — {@link PaymentHandoffService#resolvePayment}
 * publishes this event in the same transaction as the {@code Payment} row's {@code SUCCEEDED}
 * update, so the event can't be lost even if the app crashes right after commit.
 *
 * <p><b>A deliberate, documented placeholder</b> — same spirit as Epic 3's own (now-deleted)
 * {@code payment.NoOpPaymentGatewayPort}: this reactor has no real downstream consumer for a
 * payment-succeeded notification yet (no email/Slack/analytics integration exists), so this
 * handler's only job today is to prove the outbox mechanism actually carries the event reliably,
 * via a structured audit log line, rather than the event sitting unconsumed and eventually
 * {@code FAILED} the way an {@code ORDER_CREATED} event would have (see this module's own
 * `CLAUDE.md` for that earlier, opposite decision, and why payment outcomes chose to publish
 * anyway). Replace the body with a real integration when one is actually needed.
 *
 * <p>{@link #EVENT_TYPE}/{@link Payload} follow this module's usual per-handler convention — see
 * {@code service.impl.ProductChangedOutboxEventHandler}'s own Javadoc for why neither is shared
 * across handlers.
 */
@Component
@Slf4j
public class PaymentSucceededOutboxEventHandler implements OutboxEventHandler {

    public static final String EVENT_TYPE = "PAYMENT_SUCCEEDED";

    /** Typed payload for {@code PAYMENT_SUCCEEDED} events. */
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
        log.info("Payment succeeded for order id={} amount={} gatewayReference={}",
                payload.orderId(), payload.amount(), payload.gatewayReference());
    }
}
