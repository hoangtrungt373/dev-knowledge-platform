package com.ttg.devknowledgeplatform.ecommerce.api.impl;

import com.stripe.exception.SignatureVerificationException;

import com.ttg.devknowledgeplatform.ecommerce.api.StripeWebhookApi;
import com.ttg.devknowledgeplatform.ecommerce.webhook.StripeWebhookService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implementation of {@link StripeWebhookApi} — a thin pass-through, same convention as this
 * module's other controllers: signature verification and every real decision live in
 * {@link StripeWebhookService}, not here.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController implements StripeWebhookApi {

    private final StripeWebhookService stripeWebhookService;

    @Override
    public ResponseEntity<Void> receive(byte[] payload, String signature) {
        try {
            stripeWebhookService.handleWebhook(payload, signature);
        } catch (SignatureVerificationException e) {
            log.warn("Rejected Stripe webhook — signature verification failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok().build();
    }
}
