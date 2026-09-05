package com.ttg.devknowledgeplatform.ecommerce.api.impl;

import com.ttg.devknowledgeplatform.ecommerce.api.PaymentConfigApi;
import com.ttg.devknowledgeplatform.ecommerce.config.PaymentProperties;
import com.ttg.devknowledgeplatform.ecommerce.dto.PaymentConfigResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implementation of {@link PaymentConfigApi} — reads the same {@link PaymentProperties}
 * {@code StripePaymentGateway}/{@code MockPaymentGateway}'s own {@code @ConditionalOnProperty}
 * selects between, rather than going through a service layer: there's no business logic here, just
 * exposing two already-external config values.
 */
@RestController
@RequiredArgsConstructor
public class PaymentConfigController implements PaymentConfigApi {

    private final PaymentProperties paymentProperties;

    @Override
    public ResponseEntity<PaymentConfigResponse> get() {
        String gateway = paymentProperties.gateway();
        return ResponseEntity.ok(PaymentConfigResponse.builder()
                .gateway(gateway)
                .publishableKey("stripe".equals(gateway) ? paymentProperties.stripe().publishableKey() : null)
                .build());
    }
}
