package com.ttg.devknowledgeplatform.ecommerce.api;

import com.ttg.devknowledgeplatform.ecommerce.dto.PaymentConfigResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * HTTP contract for the payment-config lookup (Epic 4, Option A: Stripe Elements) — tells the
 * checkout GUI, at runtime, which gateway is active and (when it's {@code stripe}) which
 * publishable key to hand to {@code loadStripe()}. Mapped under {@code /api/v1/public/**}, which
 * this module's own {@code security/SecurityConfig} permits without authentication — same
 * reasoning as {@link PublicProductCategoryApi}: a shopper reaches the payment step before any
 * admin-only concern applies, and a publishable key is, by Stripe's own design, safe to expose.
 *
 * <p>The implementation ({@link com.ttg.devknowledgeplatform.ecommerce.api.impl.PaymentConfigController})
 * carries no HTTP annotations, matching this module's other controllers.
 */
@RequestMapping("/api/v1/public/payment-config")
public interface PaymentConfigApi {

    /**
     * @return {@code 200} with the active gateway and (for {@code stripe}) its publishable key
     */
    @GetMapping
    ResponseEntity<PaymentConfigResponse> get();
}
