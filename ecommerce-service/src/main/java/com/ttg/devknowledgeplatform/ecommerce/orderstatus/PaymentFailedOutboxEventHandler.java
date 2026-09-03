package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.ecommerce.entity.OutboxEvent;
import com.ttg.devknowledgeplatform.ecommerce.outbox.OutboxEventHandler;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code PAYMENT_FAILED} handler (US-4.4) — {@link PaymentHandoffService#resolvePayment}
 * publishes this event in the same transaction as the {@code Payment} row's {@code DECLINED}
 * update. See {@link PaymentSucceededOutboxEventHandler}'s own Javadoc for why this is a
 * deliberate, documented placeholder (a structured audit log line, no real downstream consumer
 * yet) rather than dead weight — the same reasoning applies here.
 *
 * <p>{@link #gatewayFailureMessage} in the payload is the raw gateway string — fine to log here
 * (server-side, internal-only), but never the value {@code Payment#getFailureCategory()}'s own
 * shopper-facing surface (US-4.7, not yet built) would expose to a client.
 */
@Component
@Slf4j
public class PaymentFailedOutboxEventHandler implements OutboxEventHandler {

    public static final String EVENT_TYPE = "PAYMENT_FAILED";

    /** Typed payload for {@code PAYMENT_FAILED} events. */
    public record Payload(Integer orderId, BigDecimal amount, String failureCategory, String gatewayFailureMessage) {

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("orderId", orderId);
            map.put("amount", amount.toPlainString());
            map.put("failureCategory", failureCategory);
            map.put("gatewayFailureMessage", gatewayFailureMessage);
            return map;
        }

        public static Payload from(OutboxEvent event) {
            Map<String, Object> payload = event.getPayload();
            Number orderId = (Number) payload.get("orderId");
            return new Payload(orderId.intValue(),
                    new BigDecimal((String) payload.get("amount")),
                    (String) payload.get("failureCategory"),
                    (String) payload.get("gatewayFailureMessage"));
        }
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public void handle(OutboxEvent event) {
        Payload payload = Payload.from(event);
        log.info("Payment failed for order id={} amount={} failureCategory={} gatewayFailureMessage={}",
                payload.orderId(), payload.amount(), payload.failureCategory(), payload.gatewayFailureMessage());
    }
}
