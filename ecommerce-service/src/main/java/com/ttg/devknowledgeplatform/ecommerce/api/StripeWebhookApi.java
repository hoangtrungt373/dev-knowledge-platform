package com.ttg.devknowledgeplatform.ecommerce.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * HTTP contract for Stripe's own webhook delivery (US-4.5) — a fresh top-level prefix
 * ({@code /webhooks/**}), deliberately <b>not</b> under {@code /api/v1/**}: this is Stripe's
 * server calling us, not an end-user/admin API client, and it carries no JWT at all. Per the
 * confirmed shape for this epic, this endpoint is exposed directly on this service's own origin
 * and is never proxied by {@code gateway} — the same way {@code content-service}'s
 * {@code /internal/**} bypasses {@code gateway}, just with Stripe's own HMAC signature (see
 * {@code webhook.StripeWebhookService}) standing in for that path's shared-secret header, since
 * neither a JWT nor a static shared secret is how Stripe authenticates itself.
 * {@code security.SecurityConfig} marks {@code /webhooks/**} {@code permitAll()} for the same
 * reason it does {@code /internal/**} — Spring Security's own filter chain has nothing to verify
 * here; the signature check happens inside the handler itself, since it needs the raw request body
 * to compute against, not just a header.
 *
 * <p>The implementation ({@link com.ttg.devknowledgeplatform.ecommerce.api.impl.StripeWebhookController})
 * carries no HTTP annotations, matching this module's usual {@code api}/{@code api.impl} split.
 */
@RequestMapping("/webhooks/stripe")
public interface StripeWebhookApi {

    /**
     * @param payload   the exact, unmodified request body bytes — bound as {@code byte[]} (not
     *                  {@code String}) specifically so no {@code HttpMessageConverter} re-encodes
     *                  or reformats them before Stripe's own HMAC signature is checked against them
     * @param signature the {@code Stripe-Signature} header Stripe attaches to every webhook request
     * @return {@code 200} once the event is durably recorded (dedup row + any resulting
     *         {@code Payment}/{@code Order} update, all in one transaction); {@code 400} if the
     *         signature doesn't verify. Any other failure propagates as a {@code 5xx} — the
     *         correct outcome for a genuine unexpected error, since Stripe interprets a non-2xx
     *         response as "retry this delivery later"
     */
    @PostMapping
    ResponseEntity<Void> receive(@RequestBody byte[] payload, @RequestHeader("Stripe-Signature") String signature);
}
