package com.ttg.devknowledgeplatform.ecommerce.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.ecommerce.payment.*} — a code-quality-audit finding: {@code gateway}/
 * {@code stripe.secret-key}/{@code stripe.currency}/{@code stripe.publishable-key}/
 * {@code stripe.webhook-secret} used to be five separate {@code @Value}-injected fields scattered
 * across {@code StripeWebhookService}, {@code StripePaymentGateway}, and
 * {@code api.impl.PaymentConfigController} — one small config surface with no single place that
 * showed its whole shape. Constructor-bound (a Java 21 record — see this reactor's own "modern
 * Java" convention); the compact constructors below null-coalesce every field to the same default
 * each {@code @Value(...:default)} placeholder used to carry, so a profile that sets none of these
 * (the default {@code mock} gateway) still binds a fully-populated, never-{@code null} instance
 * rather than one whose nested {@link Stripe} is itself {@code null} — Spring Boot's relaxed
 * binding otherwise leaves a nested constructor-bound record {@code null} entirely when none of its
 * own sub-properties are set.
 */
@ConfigurationProperties(prefix = "app.ecommerce.payment")
public record PaymentProperties(String gateway, Stripe stripe) {

    public PaymentProperties {
        if (gateway == null) {
            gateway = "mock";
        }
        if (stripe == null) {
            stripe = new Stripe(null, null, null, null);
        }
    }

    /**
     * {@code app.ecommerce.payment.stripe.*} — only meaningful when {@link #gateway()} is
     * {@code stripe} (see {@code StripePaymentGateway}'s own
     * {@code @ConditionalOnProperty}), but always non-{@code null} here regardless, for the same
     * reason the enclosing record's compact constructor exists.
     */
    public record Stripe(String secretKey, String publishableKey, String currency, String webhookSecret) {

        public Stripe {
            secretKey = secretKey == null ? "" : secretKey;
            publishableKey = publishableKey == null ? "" : publishableKey;
            currency = currency == null ? "usd" : currency;
            webhookSecret = webhookSecret == null ? "" : webhookSecret;
        }
    }
}
