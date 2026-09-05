package com.ttg.devknowledgeplatform.ecommerce.payment;

import com.stripe.model.StripeError;

import com.ttg.devknowledgeplatform.ecommerce.enums.PaymentFailureCategory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link StripeFailureCategoryMapper} — the single source of truth both
 * {@link StripePaymentGateway#charge}'s synchronous decline path and
 * {@code webhook.StripeWebhookService}'s asynchronous one agree on, previously with zero test
 * coverage of its own despite that "single source of truth" role.
 */
class StripeFailureCategoryMapperTest {

    @Test
    void nullErrorCategorizesAsGatewayError() {
        assertThat(StripeFailureCategoryMapper.categorize(null)).isEqualTo(PaymentFailureCategory.GATEWAY_ERROR);
    }

    @Test
    void errorWithNoDeclineCodeOrCodeCategorizesAsGatewayError() {
        StripeError error = new StripeError();

        assertThat(StripeFailureCategoryMapper.categorize(error)).isEqualTo(PaymentFailureCategory.GATEWAY_ERROR);
    }

    @Test
    void insufficientFundsDeclineCodeCategorizesAsInsufficientFunds() {
        StripeError error = new StripeError();
        error.setDeclineCode("insufficient_funds");

        assertThat(StripeFailureCategoryMapper.categorize(error)).isEqualTo(PaymentFailureCategory.INSUFFICIENT_FUNDS);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "card_declined", "generic_decline", "expired_card", "incorrect_cvc", "processing_error",
            "stolen_card", "lost_card", "fraudulent", "do_not_honor", "pickup_card"
    })
    void everyKnownDeclineCodeCategorizesAsCardDeclined(String declineCode) {
        StripeError error = new StripeError();
        error.setDeclineCode(declineCode);

        assertThat(StripeFailureCategoryMapper.categorize(error)).isEqualTo(PaymentFailureCategory.CARD_DECLINED);
    }

    @Test
    void unrecognizedDeclineCodeCategorizesAsGatewayError() {
        StripeError error = new StripeError();
        error.setDeclineCode("some_future_stripe_decline_code_this_mapper_has_never_seen");

        assertThat(StripeFailureCategoryMapper.categorize(error)).isEqualTo(PaymentFailureCategory.GATEWAY_ERROR);
    }

    @Test
    void fallsBackToTheTopLevelCodeWhenNoDeclineCodeIsPresent() {
        // Not every Stripe decline carries a decline_code (e.g. some card errors only set code) —
        // this must still classify rather than silently falling through to GATEWAY_ERROR.
        StripeError error = new StripeError();
        error.setCode("card_declined");

        assertThat(StripeFailureCategoryMapper.categorize(error)).isEqualTo(PaymentFailureCategory.CARD_DECLINED);
    }

    @Test
    void preferstDeclineCodeOverCodeWhenBothArePresent() {
        StripeError error = new StripeError();
        error.setDeclineCode("insufficient_funds");
        error.setCode("card_declined");

        assertThat(StripeFailureCategoryMapper.categorize(error)).isEqualTo(PaymentFailureCategory.INSUFFICIENT_FUNDS);
    }
}
