package com.ttg.devknowledgeplatform.ecommerce.api.impl;

import com.ttg.devknowledgeplatform.ecommerce.api.PaymentConfigApi;
import com.ttg.devknowledgeplatform.ecommerce.dto.PaymentConfigResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implementation of {@link PaymentConfigApi} — reads the same two properties
 * {@code StripePaymentGateway}/{@code MockPaymentGateway}'s own {@code @ConditionalOnProperty}
 * selects between, rather than going through a service layer: there's no business logic here, just
 * exposing two already-external config values.
 */
@RestController
public class PaymentConfigController implements PaymentConfigApi {

    @Value("${app.ecommerce.payment.gateway:mock}")
    private String gateway;

    @Value("${app.ecommerce.payment.stripe.publishable-key:}")
    private String publishableKey;

    @Override
    public ResponseEntity<PaymentConfigResponse> get() {
        return ResponseEntity.ok(PaymentConfigResponse.builder()
                .gateway(gateway)
                .publishableKey("stripe".equals(gateway) ? publishableKey : null)
                .build());
    }
}
