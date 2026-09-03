package com.ttg.devknowledgeplatform.ecommerce.payment;

import com.stripe.model.StripeError;

import com.ttg.devknowledgeplatform.ecommerce.enums.PaymentFailureCategory;

/**
 * Maps a Stripe decline's own vocabulary ({@code decline_code}/{@code code}) to this codebase's
 * small, shopper-facing {@link PaymentFailureCategory} set (US-4.7) — extracted out of
 * {@link StripePaymentGateway} (Phase 2's original home for this logic) once
 * {@code webhook.StripeWebhookService} (Phase 5) needed the exact same translation for a decline
 * that arrives asynchronously via webhook rather than synchronously via {@link
 * StripePaymentGateway#charge}; both call sites must agree on one mapping, so this is now the
 * single source of truth for it rather than two independent copies.
 */
public final class StripeFailureCategoryMapper {

    private StripeFailureCategoryMapper() {
    }

    public static PaymentFailureCategory categorize(StripeError error) {
        if (error == null) {
            return PaymentFailureCategory.GATEWAY_ERROR;
        }
        String signal = error.getDeclineCode() != null ? error.getDeclineCode() : error.getCode();
        if (signal == null) {
            return PaymentFailureCategory.GATEWAY_ERROR;
        }
        return switch (signal) {
            case "insufficient_funds" -> PaymentFailureCategory.INSUFFICIENT_FUNDS;
            case "card_declined", "generic_decline", "expired_card", "incorrect_cvc", "processing_error",
                    "stolen_card", "lost_card", "fraudulent", "do_not_honor", "pickup_card" ->
                    PaymentFailureCategory.CARD_DECLINED;
            default -> PaymentFailureCategory.GATEWAY_ERROR;
        };
    }
}
