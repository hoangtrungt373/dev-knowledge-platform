package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.ecommerce.config.OrderJobProperties;
import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.enums.PaymentFailureCategory;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentCancellationResult;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentGatewayPort;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentOutcome;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentResult;
import com.ttg.devknowledgeplatform.ecommerce.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * The "ask the gateway for the ground truth on one order and act on it" logic — extracted out of
 * {@link OrderReconciliationJob} (which now just supplies the poll-batch loop) once a second
 * caller needed the identical logic: {@code service.impl.OrderServiceImpl#reconcilePayment}, an
 * on-demand endpoint the GUI calls the instant its own live countdown (driven by
 * {@code dto.OrderResponse#getPaymentExpiresAt()}, resolved by {@code mapper.OrderMapper}) reaches
 * zero, instead of waiting for this job's own next poll tick (up to a full
 * {@code reconciliation.poll-interval} away). Both callers go through this exact same method, so
 * the "fast path" (shopper's tab is open, watching the countdown) and the "safety net path"
 * (shopper closed the tab, the scheduled job eventually gets to it) can never behave differently —
 * one code path, two triggers.
 *
 * <p><b>Deliberately does not catch {@link com.ttg.devknowledgeplatform.ecommerce.payment.PaymentGatewayException}
 * itself</b> — {@link OrderReconciliationJob}'s own {@code AbstractReconciliationJob#reconcileBatch}
 * already tolerates it for the poll path (log and retry next tick), and
 * {@code OrderServiceImpl#reconcilePayment} needs it to propagate so its own
 * {@code callGatewayOrFail} can translate it into a friendly, shopper-facing
 * {@code PAYMENT_GATEWAY_UNAVAILABLE} instead of an unmapped 500. A genuine gateway/network failure
 * here must never be treated as "expired"/"cancelled" — this reactor never fabricates an outcome
 * it didn't actually get from the gateway (see {@code payment.PaymentGatewayException}'s own
 * Javadoc); the order/payment rows are simply left exactly as they were, for a retry (manual or the
 * next scheduled poll) to resolve once the gateway is reachable again.
 *
 * <p><b>No separate "is this order actually past the abandonment window yet" gate is needed at
 * either call site</b> — {@link #isAbandoned} is checked inside this method regardless of who
 * called it. If the on-demand endpoint is ever invoked early (clock skew, a stray double-click), it
 * just performs a harmless live {@code checkStatus} refresh and returns the order unchanged; the
 * cancel-and-expire branch only ever fires once the order genuinely is past the deadline, exactly
 * as if the scheduled job's own next tick had landed at that instant instead.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentReconciliationService {

    private final OrderRepository orderRepository;
    private final PaymentGatewayPort paymentGatewayPort;
    private final PaymentHandoffService paymentHandoffService;
    private final PaymentCancellationService paymentCancellationService;
    private final OrderJobProperties orderJobProperties;

    /**
     * Re-checks {@code orderId}'s real gateway status right now and applies whatever it says.
     *
     * @return the order after whatever this call did to it — unchanged if not found, not currently
     *         {@code PAYMENT_PROCESSING}, or the PaymentIntent is still genuinely open and not yet
     *         abandoned; the finalized order (via {@link PaymentHandoffService#resolvePayment} or
     *         {@link PaymentCancellationService#applyAbandonmentExpiry}) otherwise. {@code null}
     *         only if the order itself no longer exists at all.
     */
    public Order reconcileNow(Integer orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        // A defensive guard against a stale/early caller (e.g. it already resolved via the
        // synchronous initiatePayment flow moments ago) — same reasoning as
        // OrderReservationExpiryProcessor's own re-check, not a distributed-concurrency mechanism
        // (this reactor runs one instance per service today).
        if (order == null || order.getStatus() != OrderStatus.PAYMENT_PROCESSING) {
            return order;
        }
        PaymentResult result = paymentGatewayPort.checkStatus(order.getIdempotencyKey());
        if (result.outcome() == PaymentOutcome.PENDING && result.gatewayReference() == null) {
            // The charge attempt never reached the gateway at all — nothing left to ever retrieve,
            // so leaving this PENDING would poll forever with no terminal exit. Past the grace
            // period already (the poll path only runs for orders stuck that long; the on-demand
            // path only fires once the countdown itself has run out, always well past it too).
            log.warn("Order id={} idempotencyKey={} stuck PAYMENT_PROCESSING with no gatewayReference ever "
                    + "recorded — finalizing as a synthetic decline", orderId, order.getIdempotencyKey());
            result = PaymentResult.declined(null, PaymentFailureCategory.GATEWAY_ERROR,
                    "Payment attempt never reached the payment gateway");
        } else if (result.outcome() == PaymentOutcome.PENDING && isAbandoned(order)) {
            // A real, still-open PaymentIntent, but the order has now sat PAYMENT_PROCESSING past
            // the abandonment window with no resolution and no explicit cancel — the shopper
            // appears to have simply walked away from the payment form. Nothing else will ever
            // pick this up on its own (no webhook fires for an intent nobody confirmed), so
            // actively void it at the gateway instead of polling forever.
            log.warn("Order id={} idempotencyKey={} stuck PAYMENT_PROCESSING past the abandonment window ({}) "
                    + "with a still-open PaymentIntent gatewayReference={} — actively cancelling it as abandoned",
                    orderId, order.getIdempotencyKey(), orderJobProperties.reconciliation().abandonmentTimeout(),
                    result.gatewayReference());
            PaymentCancellationResult cancellation = paymentGatewayPort.cancelUnconfirmed(result.gatewayReference());
            Order resolved = paymentCancellationService.applyAbandonmentExpiry(orderId, cancellation);
            log.info("Reconciled (abandoned) order id={} idempotencyKey={} status={}",
                    orderId, order.getIdempotencyKey(), resolved.getStatus());
            return resolved;
        }
        Order resolved = paymentHandoffService.resolvePayment(orderId, result);
        log.info("Reconciled order id={} idempotencyKey={} outcome={}",
                orderId, order.getIdempotencyKey(), result.outcome());
        return resolved;
    }

    /**
     * Whether {@code order} has sat {@code PAYMENT_PROCESSING} past
     * {@code orderJobProperties.reconciliation().abandonmentTimeout()} — {@code false} whenever an
     * order somehow reaches this method without ever having gone through
     * {@code PendingOrderStatusHandler#startPaymentProcessing} (which always stamps this field),
     * treated as "not abandoned" rather than a null-pointer/false-positive risk.
     */
    private boolean isAbandoned(Order order) {
        if (order.getPaymentProcessingStartedAt() == null) {
            return false;
        }
        Instant abandonmentCutoff = Instant.now().minus(orderJobProperties.reconciliation().abandonmentTimeout());
        return order.getPaymentProcessingStartedAt().isBefore(abandonmentCutoff);
    }
}
