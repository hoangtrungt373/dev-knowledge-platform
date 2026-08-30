package com.ttg.devknowledgeplatform.ecommerce.payment;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * <b>Placeholder implementation — replace with a real gateway adapter when Epic 4 (Payments) is
 * actually built.</b> Always approves instantly: {@link #charge} and {@link #checkStatus} both
 * return {@link PaymentOutcome#SUCCEEDED} unconditionally, with no real money movement, network
 * call, or persisted state of its own. This exists purely so Epic 3 Phase 4's payment-handoff
 * ({@code orderstatus.PaymentHandoffService}) and reconciliation
 * ({@code orderstatus.OrderReconciliationJob}) mechanisms have something concrete to call end to
 * end — proving the {@code PENDING} -> {@code PAYMENT_PROCESSING} -> {@code CONFIRMED}/
 * {@code FAILED} wiring actually works — without Epic 4's real gateway integration existing yet.
 *
 * <p>Since this is the only {@link PaymentGatewayPort} bean in the context today, Spring wires it
 * in automatically wherever the interface is injected. When Epic 4 adds a real adapter, this class
 * should be deleted outright (or both gated behind Spring profiles, if a fake gateway stays useful
 * for local/test environments) — don't leave both wired in as ambiguous candidates for the same
 * interface.
 */
@Component
@Slf4j
public class NoOpPaymentGatewayPort implements PaymentGatewayPort {

    @Override
    public PaymentOutcome charge(String idempotencyKey, BigDecimal amount) {
        log.warn("NoOpPaymentGatewayPort.charge called for idempotencyKey={} amount={} — no real "
                + "payment gateway exists yet (Epic 4); auto-approving", idempotencyKey, amount);
        return PaymentOutcome.SUCCEEDED;
    }

    @Override
    public PaymentOutcome checkStatus(String idempotencyKey) {
        log.warn("NoOpPaymentGatewayPort.checkStatus called for idempotencyKey={} — no real "
                + "payment gateway exists yet (Epic 4); auto-approving", idempotencyKey);
        return PaymentOutcome.SUCCEEDED;
    }
}
