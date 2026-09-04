package com.ttg.devknowledgeplatform.ecommerce.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Tells the checkout GUI at runtime how to render its payment step (Option A, Stripe Elements).
 *
 * <p>{@link #publishableKey} is Stripe's own <b>publishable</b> key (safe to expose to the
 * browser, unlike {@code app.ecommerce.payment.stripe.secret-key}, which this response never
 * carries) — {@code null}/blank whenever {@link #gateway} is {@code "mock"}, since there's nothing
 * for {@code loadStripe()} to initialize against. {@link #gateway} lets the GUI decide whether to
 * mount a real {@code PaymentElement} at all: against {@code MockPaymentGateway} (the default,
 * used whenever no real Stripe credentials are configured), {@code OrderApi#pay}'s own response
 * already carries a definitive verdict with no {@code paymentClientSecret}, so the GUI can skip
 * Elements entirely and show that verdict directly — see {@code gui}'s own
 * {@code OrderDetailPage.tsx} note for how the two client-side paths this endpoint enables meet
 * back up.
 */
@Data
@Builder
public class PaymentConfigResponse {

    private String gateway;
    private String publishableKey;
}
